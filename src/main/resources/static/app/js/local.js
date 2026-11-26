// Cambios hechos en el equipo: se guardan en la base local y se anotan en la cola (outbox) para subirlos.
// Cada cambio y su anotación van en la misma transacción: si se corta la luz, o quedan los dos o ninguno.
import { db } from './db.js';
import { normalizarCodigo, parsearNumero, parsearCantidad, uuid7 } from './texto.js';

let detectar = () => null;
let marcasDelVendedor = [];
let alCambiar = () => {};

/** detector: de marca.js. perfil: el de la última sincronización. cambio: avisa al motor de sync. */
export function configurarLocal({ detector, perfil, cambio }) {
  if (detector) detectar = detector;
  if (perfil) marcasDelVendedor = perfil.marcas ?? [];
  if (cambio) alCambiar = cambio;
}

const PRECIO_MAXIMO = 9999999999.99;
const ahora = () => new Date().toISOString();

// ---------- Validaciones (las mismas que ItemStockService.validar) ----------

export function validarItem(f) {
  const errores = [];
  const codigo = (f.codigo ?? '').trim();
  const descripcion = (f.descripcion ?? '').trim();
  const vehiculo = (f.vehiculo ?? '').trim();
  if (!codigo || !normalizarCodigo(codigo)) errores.push('Poné el código del repuesto.');
  else if (codigo.length > 80) errores.push('El código puede tener hasta 80 caracteres.');
  if (descripcion.length > 300) errores.push('La descripción puede tener hasta 300 caracteres.');
  if (vehiculo.length > 150) errores.push('El vehículo puede tener hasta 150 caracteres.');

  let cantidad = null;
  const c = (f.cantidad ?? '').trim().toLowerCase();
  if (c && c !== 'consultar') {
    cantidad = parsearCantidad(c);
    if (cantidad == null) errores.push('La cantidad tiene que ser un número entero o "consultar".');
  }
  let precio = null;
  const p = (f.precio ?? '').trim().toLowerCase();
  if (p && p !== 'consultar') {
    precio = parsearNumero(p);
    if (precio == null || precio < 0 || precio > PRECIO_MAXIMO) {
      errores.push('El precio no parece válido. Ej: 35,50 (o vacío = a consultar).');
      precio = null;
    }
  }
  return {
    errores,
    datos: { codigo, descripcion: descripcion || null, vehiculo: vehiculo || null, cantidad, precio },
  };
}

// ---------- Cola ----------

/** La última operación todavía no enviada sobre ese uuid (para juntar varios cambios en uno). */
async function sinEnviar(uuid) {
  return db.filas('SELECT * FROM outbox WHERE uuid = ? AND enviada = 0 ORDER BY seq', [uuid]);
}

async function enviadas(uuid) {
  return db.valor('SELECT count(*) FROM outbox WHERE uuid = ? AND enviada = 1', [uuid]);
}

/** La carga de Excel todavía no enviada que trae este ítem (si la hay): {seq, datos, fila}. */
async function cargaSinEnviarCon(uuid) {
  const o = await db.fila("SELECT * FROM outbox WHERE tipo = 'STOCK_REEMPLAZAR' AND enviada = 0 ORDER BY seq DESC LIMIT 1");
  if (!o) return null;
  const datos = JSON.parse(o.datos);
  const fila = datos.filas.find(f => f.uuid === uuid);
  return fila ? { seq: o.seq, datos, fila } : null;
}

function insertarOp(tipo, uuid, baseVersion, datos) {
  return {
    sql: 'INSERT INTO outbox (op_id, tipo, uuid, base_version, hecha_en, datos) VALUES (?,?,?,?,?,?)',
    bind: [crypto.randomUUID(), tipo, uuid, baseVersion ?? null, ahora(), datos ? JSON.stringify(datos) : null],
  };
}

// ---------- Mi stock ----------

/** Alta (existente = null) o cambio de un repuesto. Devuelve los errores para mostrar, o []. */
export async function guardarItem(form, existente) {
  const { errores, datos } = validarItem(form);
  if (errores.length) return errores;
  const marcaId = form.marca ? Number(form.marca) : detectar(datos.vehiculo, null, datos.descripcion, marcasDelVendedor);
  const uuid = existente?.uuid ?? uuid7();
  const datosOp = {
    codigo: form.codigo ?? '', descripcion: form.descripcion ?? '', vehiculo: form.vehiculo ?? '',
    cantidad: form.cantidad ?? '', precio: form.precio ?? '', marcaId: form.marca ?? '',
  };
  const carga = existente?.version == null ? await cargaSinEnviarCon(uuid) : null;
  const sentencias = [{
    sql: `INSERT OR REPLACE INTO item (uuid, version, codigo, codigo_norm, descripcion, marca_repuesto, vehiculo, marca_id,
          cantidad, precio, cargado_en, actualizado_en, pendiente, gen, orden)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,?,1,?,?)`,
    bind: [uuid, existente?.version ?? null, datos.codigo, normalizarCodigo(datos.codigo), datos.descripcion,
      existente?.marca_repuesto ?? null, datos.vehiculo, marcaId ?? null, datos.cantidad, datos.precio, ahora(), ahora(),
      existente?.gen ?? 0, existente?.orden ?? Date.now()],
  }];
  // Si ya había un cambio de este ítem sin enviar, se reemplaza (viaja uno solo)
  const previa = (await sinEnviar(uuid)).filter(o => o.tipo === 'ITEM_GUARDAR').pop();
  if (carga) {
    // Es una fila de un Excel que todavía no se subió: se corrige la fila (el ítem todavía no existe en el servidor)
    Object.assign(carga.fila, {
      codigo: datos.codigo, descripcion: datos.descripcion, vehiculo: datos.vehiculo,
      cantidad: datosOp.cantidad, precio: datosOp.precio,
    });
    sentencias.push({ sql: 'UPDATE outbox SET datos = ? WHERE seq = ?', bind: [JSON.stringify(carga.datos), carga.seq] });
  } else if (previa) {
    sentencias.push({ sql: 'UPDATE outbox SET datos = ?, hecha_en = ? WHERE seq = ?', bind: [JSON.stringify(datosOp), ahora(), previa.seq] });
  } else {
    sentencias.push(insertarOp('ITEM_GUARDAR', uuid, existente?.version, datosOp));
  }
  await db.lote(sentencias);
  alCambiar();
  return [];
}

export async function borrarItem(item) {
  const pendientes = await sinEnviar(item.uuid);
  const sentencias = [{ sql: 'DELETE FROM item WHERE uuid = ?', bind: [item.uuid] }];
  const carga = item.version == null ? await cargaSinEnviarCon(item.uuid) : null;
  if (carga) {
    // Fila de un Excel que todavía no se subió: se saca de la carga y listo
    carga.datos.filas = carga.datos.filas.filter(f => f.uuid !== item.uuid);
    sentencias.push({ sql: 'UPDATE outbox SET datos = ? WHERE seq = ?', bind: [JSON.stringify(carga.datos), carga.seq] });
    await db.lote(sentencias);
    alCambiar();
    return;
  }
  const nuncaSubido = item.version == null && !(await enviadas(item.uuid));
  // Lo que no se envió de este ítem ya no hace falta
  for (const o of pendientes) sentencias.push({ sql: 'DELETE FROM outbox WHERE seq = ?', bind: [o.seq] });
  // Si nunca llegó al servidor, alcanza con borrarlo acá
  if (!nuncaSubido) sentencias.push(insertarOp('ITEM_BORRAR', item.uuid, item.version, null));
  await db.lote(sentencias);
  alCambiar();
}

/**
 * Carga de Excel: reemplaza todo el stock. Compara por código + marca del repuesto igual que StockWriter, así
 * cada repuesto conserva su uuid acá y en el servidor. filas: las válidas de excel.js.
 */
export async function reemplazarStock(nombreArchivo, filas) {
  const actuales = await db.filas('SELECT * FROM item ORDER BY orden, rowid');
  const clave = (norm, marca) => norm + '\u0000' + (marca ?? '').trim().toLowerCase();
  const porClave = new Map();
  for (const i of actuales) {
    const k = clave(i.codigo_norm, i.marca_repuesto);
    porClave.set(k, [...(porClave.get(k) ?? []), i]);
  }
  const t = ahora();
  const sentencias = [{ sql: 'DELETE FROM item' }];
  const filasOp = [];
  let orden = Date.now();
  for (const f of filas) {
    const norm = normalizarCodigo(f.codigo);
    const previo = porClave.get(clave(norm, f.marca))?.shift();
    const uuid = previo?.uuid ?? uuid7();
    const marcaId = detectar(f.vehiculo, f.marca, f.descripcion, marcasDelVendedor);
    sentencias.push({
      sql: `INSERT INTO item (uuid, version, codigo, codigo_norm, descripcion, marca_repuesto, vehiculo, marca_id,
            cantidad, precio, cargado_en, actualizado_en, pendiente, gen, orden) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,1,?,?)`,
      bind: [uuid, previo?.version ?? null, f.codigo, norm, f.descripcion, f.marca, f.vehiculo, marcaId ?? null,
        f.cantidad, f.precio, t, t, previo?.gen ?? 0, orden++],
    });
    filasOp.push({
      uuid, codigo: f.codigo, descripcion: f.descripcion, marca: f.marca, vehiculo: f.vehiculo,
      cantidad: f.cantidadTexto, precio: f.precioTexto, reemplaza: f.reemplaza, equivalentes: f.equivalentes,
    });
  }
  // Los cambios sueltos sin enviar quedan reemplazados por la carga
  sentencias.push({ sql: "DELETE FROM outbox WHERE enviada = 0 AND tipo IN ('ITEM_GUARDAR', 'ITEM_BORRAR', 'STOCK_REEMPLAZAR')" });
  sentencias.push(insertarOp('STOCK_REEMPLAZAR', null, null, { nombreArchivo, filas: filasOp }));
  await db.lote(sentencias);
  alCambiar();
}

// ---------- Pedidos ----------

/** Qué puede hacer cada uno en cada estado (como Solicitud.java). */
export function accionesPosibles(p) {
  if (p.rol === 'VENDEDOR') {
    if (p.estado === 'PENDIENTE') return ['PEDIDO_OFERTAR', 'PEDIDO_SIN_STOCK'];
    if (p.estado === 'CONTRAOFERTA') return ['PEDIDO_ACEPTAR_CONTRAOFERTA', 'PEDIDO_RECHAZAR_CONTRAOFERTA'];
    return [];
  }
  if (p.estado === 'OFERTADA') return ['PEDIDO_ACEPTAR_OFERTA', 'PEDIDO_CONTRAOFERTAR', 'PEDIDO_CANCELAR'];
  if (p.estado === 'PENDIENTE' || p.estado === 'CONTRAOFERTA') return ['PEDIDO_CANCELAR'];
  return [];
}

const DESTINO = {
  PEDIDO_OFERTAR: 'OFERTADA', PEDIDO_SIN_STOCK: 'RECHAZADA', PEDIDO_ACEPTAR_CONTRAOFERTA: 'ACEPTADA',
  PEDIDO_RECHAZAR_CONTRAOFERTA: 'RECHAZADA', PEDIDO_ACEPTAR_OFERTA: 'ACEPTADA', PEDIDO_CONTRAOFERTAR: 'CONTRAOFERTA',
  PEDIDO_CANCELAR: 'CANCELADA',
};

/** SolicitudService.leerPrecio. */
function leerPrecio(texto) {
  const p = parsearNumero(texto);
  if (p == null) throw new Error('Poné el precio en dólares, por ejemplo 35,50.');
  if (p <= 0 || p > 1000000) throw new Error('Poné un precio mayor a 0.');
  return Math.round(p * 100) / 100;
}

/** Responder un pedido (también sin conexión). Devuelve un mensaje de error o null. */
export async function accionPedido(p, tipo, precioTexto) {
  if (!accionesPosibles(p).includes(tipo)) return 'El pedido cambió mientras tanto.';
  let precio = null;
  try {
    if (tipo === 'PEDIDO_OFERTAR' || tipo === 'PEDIDO_CONTRAOFERTAR') precio = leerPrecio(precioTexto);
  } catch (e) {
    return e.message;
  }
  if (tipo === 'PEDIDO_CONTRAOFERTAR' && p.precio_oferta != null && precio === Number(p.precio_oferta)) {
    return 'Es el mismo precio que te ofrecieron: aceptalo directamente.';
  }
  const set = ['estado = ?', 'pendiente = 1', 'actualizada_en = ?'];
  const bind = [DESTINO[tipo], ahora()];
  if (tipo === 'PEDIDO_OFERTAR') { set.push('precio_oferta = ?'); bind.push(precio); }
  if (tipo === 'PEDIDO_CONTRAOFERTAR') { set.push('precio_contraoferta = ?'); bind.push(precio); }
  await db.lote([
    { sql: `UPDATE pedido SET ${set.join(', ')} WHERE uuid = ?`, bind: [...bind, p.uuid] },
    insertarOp(tipo, p.uuid, p.version, precio != null ? { precio: String(precioTexto) } : null),
  ]);
  alCambiar();
  return null;
}

// ---------- Avisos ----------

export const avisos = {
  listar: () => db.filas('SELECT * FROM aviso ORDER BY id DESC LIMIT 50'),
  noLeidos: () => db.valor('SELECT count(*) FROM aviso WHERE leido = 0'),
  marcarLeidos: () => db.ejecutar('UPDATE aviso SET leido = 1 WHERE leido = 0'),
};
