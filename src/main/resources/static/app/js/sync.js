// Motor de sincronización: sube la cola de cambios, baja lo que cambió y el stock de los otros vendedores.
// Corre solo: al abrir la app, al volver la conexión, al volver a la pestaña, después de cada cambio y cada
// 30 segundos si hay algo pendiente. Si falla, reintenta cada vez más espaciado (hasta 5 minutos).
import { db } from './db.js';
import { api, usarToken, SinSesion, SinConexion, ErrorApi } from './api.js';
import { normalizarCodigo, paraBuscar, uuid7 } from './texto.js';

const estado = {
  listo: false, online: navigator.onLine, sincronizando: false, pendientes: 0, avisos: 0, ultimaSync: null,
  error: null, sinSesion: false, bloqueado: false, sesion: null, perfil: null,
  config: { diasSinSincronizar: 3 },
  catalogo: { cantidad: 0, ultima: null, bajando: false, bajados: 0 },
  datosMoviles: false,
};
const oyentes = new Set();

export function suscribir(fn) {
  oyentes.add(fn);
  fn({ ...estado });
  return () => oyentes.delete(fn);
}

function emitir() {
  for (const fn of oyentes) fn({ ...estado, catalogo: { ...estado.catalogo } });
}

export function leerEstado() {
  return { ...estado };
}

export async function refrescar() {
  estado.pendientes = await db.valor('SELECT count(*) FROM outbox');
  estado.avisos = await db.valor('SELECT count(*) FROM aviso WHERE leido = 0');
  estado.catalogo.cantidad = await db.valor('SELECT count(*) FROM otro');
  estado.catalogo.ultima = await db.meta('ultima_catalogo');
  estado.ultimaSync = await db.meta('ultima_sync');
  estado.perfil = await db.meta('perfil');
  estado.datosMoviles = (await db.meta('datos_moviles')) ?? false;
  const dias = estado.config.diasSinSincronizar ?? 3;
  const desde = estado.ultimaSync ?? estado.sesion?.desde;
  estado.bloqueado = !!desde && Date.now() - new Date(desde).getTime() > dias * 86400000;
  emitir();
}

// ---------- Sesión ----------

export async function iniciar() {
  const r = await db.abrir();
  await db.ejecutar('UPDATE outbox SET enviada = 0'); // lo que quedó a mitad de envío se reenvía (no duplica)
  estado.sesion = await db.meta('sesion');
  if (estado.sesion) {
    usarToken(estado.sesion.token);
    estado.config = estado.sesion.config ?? estado.config;
  }
  estado.listo = true;
  await refrescar();
  if (navigator.storage?.persist) navigator.storage.persist().catch(() => {});
  return { ...r, sesion: estado.sesion };
}

export async function entrar(usuario, password) {
  let dispositivo = await db.meta('dispositivo');
  if (!dispositivo) {
    dispositivo = uuid7();
    await db.setMeta('dispositivo', dispositivo);
  }
  const nombre = (navigator.userAgentData?.platform || navigator.platform || 'Equipo') + ' · ' + new Date().toLocaleDateString('es-VE');
  const r = await api.login(usuario, password, dispositivo, nombre);
  const anterior = await db.meta('sesion');
  if (anterior && anterior.usuario.username.toLowerCase() !== r.usuario.username.toLowerCase()) {
    // Otro usuario en este equipo: no se mezcla nada con lo del anterior
    await db.borrarTodo();
    await db.setMeta('dispositivo', dispositivo);
  }
  estado.sesion = { token: r.token, usuario: r.usuario, config: r.config, desde: new Date().toISOString() };
  estado.config = r.config;
  await db.setMeta('sesion', estado.sesion);
  usarToken(r.token);
  estado.sinSesion = false;
  await refrescar();
  sincronizar();
  return r.usuario;
}

/** Cerrar sesión en este equipo: se borra todo lo guardado acá. */
export async function salir() {
  try { await api.salir(); } catch { /* sin conexión: el token vence solo */ }
  await db.borrarTodo();
  usarToken(null);
  estado.sesion = null;
  await refrescar();
}

export async function setDatosMoviles(v) {
  await db.setMeta('datos_moviles', v);
  estado.datosMoviles = v;
  emitir();
  if (v) sincronizar({ catalogo: true });
}

// ---------- Cuándo sincronizar ----------

let demora = null;
let reintento = null;
let fallos = 0;
let enCurso = null;

/** Pedir una sincronización pronto (después de un cambio local). */
export function pedirSync(ms = 1500) {
  clearTimeout(demora);
  demora = setTimeout(() => sincronizar(), ms);
}

function programarReintento() {
  clearTimeout(reintento);
  const ms = Math.min(300000, 15000 * 2 ** Math.min(fallos, 5));
  fallos++;
  reintento = setTimeout(() => sincronizar(), ms);
}

window.addEventListener('online', () => { estado.online = true; emitir(); sincronizar(); });
window.addEventListener('offline', () => { estado.online = false; emitir(); });
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') pedirSync(300); });
setInterval(() => {
  if (!estado.sesion || enCurso) return;
  const viejo = !estado.ultimaSync || Date.now() - new Date(estado.ultimaSync).getTime() > 300000;
  if (estado.pendientes > 0 || viejo) sincronizar();
}, 30000);

// ---------- Sincronizar ----------

export function sincronizar(opciones = {}) {
  if (!estado.sesion) return Promise.resolve();
  if (enCurso) return enCurso;
  enCurso = (async () => {
    estado.sincronizando = true;
    estado.error = null;
    emitir();
    try {
      const ping = await api.ping();
      if (!ping) {
        estado.online = false;
        programarReintento();
        return;
      }
      estado.online = true;
      if (ping.config) estado.config = ping.config;
      await bajarCatalogos();
      const conflictos = await subir();
      await bajarLoMio(conflictos);
      await db.setMeta('ultima_sync', new Date().toISOString());
      fallos = 0;
      clearTimeout(reintento);
      await refrescar();
      if (await debeBajarCatalogo(opciones.catalogo)) await bajarCatalogo();
    } catch (e) {
      if (e instanceof SinSesion) {
        estado.sinSesion = true;
      } else if (e instanceof SinConexion) {
        estado.online = false;
        programarReintento();
      } else {
        console.error(e);
        estado.error = e.message;
        programarReintento();
      }
    } finally {
      estado.sincronizando = false;
      await refrescar().catch(() => {});
      // Si mientras tanto se hicieron cambios, otra vuelta
      if (estado.online && estado.pendientes > 0 && fallos === 0) pedirSync(1000);
    }
  })().finally(() => { enCurso = null; });
  return enCurso;
}

async function bajarCatalogos() {
  const anterior = await db.meta('catalogos');
  const cuando = await db.meta('catalogos_cuando');
  if (anterior && cuando && Date.now() - new Date(cuando).getTime() < 6 * 3600000) return;
  const r = await api.catalogos(anterior?.version ? `"${anterior.version}"` : null);
  if (r.status === 200) await db.setMeta('catalogos', r.cuerpo);
  await db.setMeta('catalogos_cuando', new Date().toISOString());
  if (r.status === 200) document.dispatchEvent(new CustomEvent('catalogos'));
}

const NOMBRE_OPERACION = {
  ITEM_GUARDAR: 'Cambio de un repuesto', ITEM_BORRAR: 'Borrado de un repuesto', STOCK_REEMPLAZAR: 'Carga de Excel',
  PEDIDO_OFERTAR: 'Oferta', PEDIDO_SIN_STOCK: 'Respuesta "no lo tengo"', PEDIDO_ACEPTAR_CONTRAOFERTA: 'Aceptar contraoferta',
  PEDIDO_RECHAZAR_CONTRAOFERTA: 'Rechazar contraoferta', PEDIDO_ACEPTAR_OFERTA: 'Aceptar oferta',
  PEDIDO_CONTRAOFERTAR: 'Contraoferta', PEDIDO_CANCELAR: 'Cancelar pedido',
};

/** Sube la cola en orden. Devuelve true si algo no se aplicó tal cual (hay que bajar todo lo mío de nuevo). */
async function subir() {
  let rehacer = false;
  for (let vuelta = 0; vuelta < 100; vuelta++) {
    const ops = await db.filas('SELECT * FROM outbox ORDER BY seq LIMIT 50');
    if (!ops.length) break;
    await db.ejecutar(`UPDATE outbox SET enviada = 1 WHERE seq <= ?`, [ops[ops.length - 1].seq]);
    let r;
    try {
      r = await api.push(ops.map(o => ({
        opId: o.op_id, tipo: o.tipo, uuid: o.uuid, baseVersion: o.base_version, hechaEn: o.hecha_en,
        datos: o.datos ? JSON.parse(o.datos) : {},
      })));
    } catch (e) {
      await db.ejecutar('UPDATE outbox SET enviada = 0');
      throw e;
    }
    const porOp = new Map(r.resultados.map(x => [x.opId, x]));
    let falloServidor = false;
    for (const o of ops) {
      const res = porOp.get(o.op_id);
      if (!res || res.resultado === 'ERROR' || falloServidor) {
        // Falla del servidor: esta y las siguientes quedan para el próximo intento, en el mismo orden
        falloServidor = true;
        continue;
      }
      const sentencias = [{ sql: 'DELETE FROM outbox WHERE seq = ?', bind: [o.seq] }];
      if (res.version != null && o.uuid) {
        // Los cambios siguientes de lo mismo se apoyan en la versión que quedó en el servidor
        sentencias.push({ sql: 'UPDATE outbox SET base_version = ? WHERE uuid = ? AND base_version IS ?', bind: [res.version, o.uuid, o.base_version] });
        for (const t of ['item', 'pedido']) {
          sentencias.push({ sql: `UPDATE ${t} SET version = ? WHERE uuid = ?`, bind: [res.version, o.uuid] });
        }
      }
      if (res.resultado !== 'APLICADA') {
        rehacer = true;
        sentencias.push({
          sql: 'INSERT INTO aviso (tipo, resultado, mensaje, creado_en) VALUES (?,?,?,?)',
          bind: [NOMBRE_OPERACION[o.tipo] ?? o.tipo, res.resultado, res.mensaje ?? '', new Date().toISOString()],
        });
      }
      if (o.tipo === 'STOCK_REEMPLAZAR') rehacer = true;
      await db.lote(sentencias);
    }
    await db.ejecutar(`UPDATE item SET pendiente = 0 WHERE pendiente = 1
        AND NOT EXISTS (SELECT 1 FROM outbox o WHERE o.uuid = item.uuid OR o.tipo = 'STOCK_REEMPLAZAR')`);
    await db.ejecutar(`UPDATE pedido SET pendiente = 0 WHERE pendiente = 1
        AND NOT EXISTS (SELECT 1 FROM outbox o WHERE o.uuid = pedido.uuid)`);
    if (falloServidor) {
      await db.ejecutar('UPDATE outbox SET enviada = 0');
      throw new Error('El servidor no pudo aplicar algunos cambios; se reintenta solo.');
    }
  }
  return rehacer;
}

/** Baja lo mío (stock, pedidos, avisos). Con todo=true baja todo y borra lo que el servidor ya no tiene. */
async function bajarLoMio(todo) {
  let cursor = todo ? '' : ((await db.meta('cursor_mio')) ?? '');
  let completo = !cursor;
  let gen = ((await db.meta('gen')) ?? 0) + (completo ? 1 : 0);
  for (let pagina = 0; pagina < 1000; pagina++) {
    const b = await api.pull(cursor, 500);
    if (b.resyncCompleto) {
      cursor = '';
      completo = true;
      gen++;
      continue;
    }
    const s = [];
    for (const i of b.items) {
      s.push({
        sql: `INSERT INTO item (uuid, version, codigo, codigo_norm, descripcion, marca_repuesto, vehiculo, marca_id, cantidad,
              precio, cargado_en, actualizado_en, pendiente, gen, orden) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,0,?,?)
              ON CONFLICT (uuid) DO UPDATE SET version = excluded.version, codigo = excluded.codigo,
                codigo_norm = excluded.codigo_norm, descripcion = excluded.descripcion, marca_repuesto = excluded.marca_repuesto,
                vehiculo = excluded.vehiculo, marca_id = excluded.marca_id, cantidad = excluded.cantidad,
                precio = excluded.precio, cargado_en = excluded.cargado_en, actualizado_en = excluded.actualizado_en,
                gen = excluded.gen
              WHERE item.pendiente = 0`,
        bind: [i.uuid, i.version, i.codigo, normalizarCodigo(i.codigo), i.descripcion, i.marcaRepuesto, i.vehiculo,
          i.marcaId, i.cantidad, i.precioUsd, i.cargadoEn, i.actualizadoEn, gen, Date.parse(i.actualizadoEn) || 0],
      });
    }
    for (const p of b.pedidos) {
      s.push({
        sql: `INSERT INTO pedido (uuid, version, rol, codigo, descripcion, cantidad, precio_usd, precio_oferta,
              precio_contraoferta, estado, creada_en, actualizada_en, contraparte, pendiente, gen)
              VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,0,?)
              ON CONFLICT (uuid) DO UPDATE SET version = excluded.version, rol = excluded.rol, estado = excluded.estado,
                precio_oferta = excluded.precio_oferta, precio_contraoferta = excluded.precio_contraoferta,
                actualizada_en = excluded.actualizada_en, contraparte = excluded.contraparte, gen = excluded.gen
              WHERE pedido.pendiente = 0`,
        bind: [p.uuid, p.version, p.rol, p.codigo, p.descripcion, p.cantidad, p.precioUsd, p.precioOferta,
          p.precioContraoferta, p.estado, p.creadaEn, p.actualizadaEn, JSON.stringify(p.contraparte), gen],
      });
    }
    for (const d of b.demandas) {
      s.push({ sql: 'INSERT OR REPLACE INTO demanda VALUES (?,?,?,?,?,?)', bind: [d.uuid, d.version, d.texto, d.estado, d.creadaEn, gen] });
    }
    for (const x of b.borrados) {
      if (x.entidad === 'item') s.push({ sql: 'DELETE FROM item WHERE uuid = ? AND pendiente = 0', bind: [x.uuid] });
      else if (x.entidad === 'demanda') s.push({ sql: 'DELETE FROM demanda WHERE uuid = ?', bind: [x.uuid] });
    }
    s.push(db.metaSql('perfil', b.perfil));
    cursor = b.cursor;
    // Si es una bajada completa, el cursor se guarda recién al final (si se corta, se empieza de nuevo)
    if (!completo) s.push(db.metaSql('cursor_mio', cursor));
    await db.lote(s);
    if (!b.masDatos) break;
  }
  if (completo) {
    await db.lote([
      { sql: 'DELETE FROM item WHERE gen < ? AND pendiente = 0', bind: [gen] },
      { sql: 'DELETE FROM pedido WHERE gen < ? AND pendiente = 0', bind: [gen] },
      { sql: 'DELETE FROM demanda WHERE gen < ?', bind: [gen] },
      db.metaSql('gen', gen),
      db.metaSql('cursor_mio', cursor),
    ]);
  }
  const perfil = await db.meta('perfil');
  const anterior = estado.perfil;
  estado.perfil = perfil;
  if (JSON.stringify(anterior?.marcas) !== JSON.stringify(perfil?.marcas)) document.dispatchEvent(new CustomEvent('perfil'));
}

// ---------- Stock de los otros vendedores ----------

/** Con datos móviles solo si el usuario lo permitió. En PC (sin dato del tipo de red) se baja siempre. */
function redPermite() {
  const c = navigator.connection;
  if (estado.datosMoviles) return true;
  if (!c) return true;
  if (c.saveData) return false;
  if (c.type) return c.type === 'wifi' || c.type === 'ethernet';
  return true;
}

async function debeBajarCatalogo(forzar) {
  if (!redPermite()) return false;
  if (forzar) return true;
  const ultima = await db.meta('ultima_catalogo');
  const aMitad = String((await db.meta('cursor_catalogo')) ?? '').startsWith('v.');
  return aMitad || !ultima || Date.now() - new Date(ultima).getTime() > 15 * 60000;
}

async function bajarCatalogo() {
  const perfil = await db.meta('perfil');
  const catalogos = await db.meta('catalogos');
  const marcas = [...(perfil?.marcas ?? [])].sort((a, b) => a - b);
  const alcance = marcas.join(',') || 'todas';
  const nombreMarca = new Map((catalogos?.marcas ?? []).map(m => [m.id, m.nombre]));
  if ((await db.meta('alcance_catalogo')) !== alcance) {
    // Cambiaron las marcas que trabaja: se baja de nuevo
    await db.borrarOtros();
    await db.lote([db.metaSql('alcance_catalogo', alcance), db.metaSql('cursor_catalogo', '')]);
  }
  let cursor = (await db.meta('cursor_catalogo')) ?? '';
  estado.catalogo.bajando = true;
  estado.catalogo.bajados = 0;
  emitir();
  try {
    for (let pagina = 0; pagina < 5000; pagina++) {
      const b = await api.catalogo(cursor, marcas, 2000);
      if (b.resyncCompleto) {
        await db.borrarOtros();
        cursor = '';
        continue;
      }
      const items = b.items.map(i => ({
        ...i,
        codigoNorm: normalizarCodigo(i.codigo),
        texto: paraBuscar([i.codigo, i.descripcion, i.marcaRepuesto, i.vehiculo, nombreMarca.get(i.marcaId)].filter(Boolean).join(' ')),
      }));
      await db.guardarOtros({ items, borrados: b.borrados, relaciones: b.relaciones });
      cursor = b.cursor;
      await db.setMeta('cursor_catalogo', cursor);
      estado.catalogo.bajados += items.length;
      estado.catalogo.cantidad = await db.valor('SELECT count(*) FROM otro');
      emitir();
      if (!b.masDatos) break;
    }
    await db.setMeta('ultima_catalogo', new Date().toISOString());
  } finally {
    estado.catalogo.bajando = false;
    emitir();
  }
}

export { ErrorApi };
