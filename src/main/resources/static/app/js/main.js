// Stock Multimarca sin conexión: pantallas (Preact + htm, sin compilar).
import { html, render, useState, useEffect, useMemo, useRef } from '../vendor/preact-htm.js';
import { db } from './db.js';
import * as sync from './sync.js';
import { configurarLocal, guardarItem, borrarItem, reemplazarStock, accionesPosibles, accionPedido, avisos } from './local.js';
import { leerExcel } from './excel.js';
import { crearDetector } from './marca.js';
import { buscar } from './busqueda.js';
import { api, ErrorApi, SinConexion } from './api.js';
import { hace, mostrarPrecio, mostrarCantidad, precioEnTexto, paraBuscar, linkWhatsapp } from './texto.js';

// ---------- Catálogos y detector de marca ----------

let catalogos = null;
async function cargarCatalogos() {
  catalogos = await db.meta('catalogos');
  const perfil = await db.meta('perfil');
  configurarLocal({
    detector: catalogos ? crearDetector(catalogos) : undefined,
    perfil: perfil ?? undefined,
    cambio: async () => { await sync.refrescar(); sync.pedirSync(); },
  });
}
document.addEventListener('catalogos', cargarCatalogos);
document.addEventListener('perfil', cargarCatalogos);
const nombreMarca = (id) => catalogos?.marcas.find(m => m.id === id)?.nombre ?? '';

// ---------- Utilidades de interfaz ----------

function useEstado() {
  const [e, setE] = useState(sync.leerEstado());
  useEffect(() => sync.suscribir(setE), []);
  return e;
}

function useHash() {
  const [h, setH] = useState(location.hash || '#/stock');
  useEffect(() => {
    const f = () => setH(location.hash || '#/stock');
    addEventListener('hashchange', f);
    return () => removeEventListener('hashchange', f);
  }, []);
  return h;
}

/** Se vuelve a dibujar cada 30 s (para los "hace 2 min"). */
function useTic() {
  const [, set] = useState(0);
  useEffect(() => { const t = setInterval(() => set(x => x + 1), 30000); return () => clearInterval(t); }, []);
}

function Paginas({ total, pagina, porPagina, setPagina, setPorPagina }) {
  const paginas = Math.max(1, Math.ceil(total / porPagina));
  if (total <= 10) return null;
  return html`<div class="paginas">
    <button class="btn btn-chico" disabled=${pagina <= 0} onClick=${() => setPagina(pagina - 1)}>‹</button>
    <span>${pagina + 1} de ${paginas}</span>
    <button class="btn btn-chico" disabled=${pagina >= paginas - 1} onClick=${() => setPagina(pagina + 1)}>›</button>
    <select value=${porPagina} onChange=${e => { setPorPagina(Number(e.target.value)); setPagina(0); }}>
      ${[10, 20, 30].map(n => html`<option value=${n}>${n} por página</option>`)}
    </select>
  </div>`;
}

function Modal({ titulo, cerrar, children }) {
  return html`<div class="fondo" onClick=${e => e.target === e.currentTarget && cerrar()}>
    <div class="modal" role="dialog" aria-label=${titulo}>
      <div class="modal-titulo"><h3>${titulo}</h3><button class="x" onClick=${cerrar} aria-label="Cerrar">×</button></div>
      ${children}
    </div>
  </div>`;
}

// ---------- Indicador de sincronización ----------

function textoIndicador(e) {
  const p = e.pendientes;
  const cambios = `${p} ${p === 1 ? 'cambio pendiente' : 'cambios pendientes'}`;
  if (!e.online) return { clase: 'off', texto: p ? `Sin conexión · ${cambios}` : 'Sin conexión' };
  if (e.sincronizando) return { clase: 'sync', texto: 'Sincronizando…' };
  if (e.error) return { clase: 'off', texto: p ? `No se pudo sincronizar · ${cambios}` : 'No se pudo sincronizar' };
  if (p) return { clase: 'sync', texto: cambios };
  return { clase: 'ok', texto: `Sincronizado ${hace(e.ultimaSync)}` };
}

function Indicador({ e }) {
  useTic();
  const t = textoIndicador(e);
  return html`<a class=${'indicador ' + t.clase} href="#/estado" title="Ver estado de la sincronización">
    <span class="punto"></span>${t.texto}${e.avisos ? html`<span class="badge">${e.avisos}</span>` : null}
  </a>`;
}

// ---------- Login ----------

function Login({ e }) {
  const [usuario, setUsuario] = useState(e.sesion?.usuario?.username ?? '');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [enviando, setEnviando] = useState(false);
  async function entrar(ev) {
    ev.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      await sync.entrar(usuario, password);
      await cargarCatalogos();
    } catch (err) {
      setError(err instanceof SinConexion ? 'Para entrar la primera vez necesitás conexión a internet.'
        : err instanceof ErrorApi ? err.message : 'No se pudo entrar. Probá de nuevo.');
    } finally {
      setEnviando(false);
    }
  }
  return html`<main class="login">
    <div class="tarjeta">
      <div class="logo-grande"><img src="img/icono.svg" alt="" /> <span>Stock <b>Multimarca</b></span></div>
      ${e.sinSesion ? html`<p class="msg msg-aviso">Tu sesión en este equipo venció. Entrá de nuevo: ${e.pendientes
        ? `los ${e.pendientes} cambios pendientes se suben cuando entres.` : 'no se pierde nada.'}</p>` : null}
      <form onSubmit=${entrar}>
        <label>Usuario o email<input autocomplete="username" value=${usuario} onInput=${x => setUsuario(x.target.value)} required /></label>
        <label>Contraseña<input type="password" autocomplete="current-password" value=${password} onInput=${x => setPassword(x.target.value)} required /></label>
        ${error ? html`<p class="msg msg-error">${error}</p>` : null}
        <button class="btn btn-primario" disabled=${enviando}>${enviando ? 'Entrando…' : 'Entrar'}</button>
      </form>
      <p class="chico">Después de entrar, la app funciona aunque se corte internet.
        ¿No tenés cuenta? <a href="/registro">Registrate en la web</a>.</p>
    </div>
  </main>`;
}

// ---------- Mi stock ----------

const VACIO = { codigo: '', descripcion: '', vehiculo: '', cantidad: '', precio: '', marca: '' };

function FormItem({ item, cerrar, bloqueado }) {
  const [f, setF] = useState(item ? {
    codigo: item.codigo, descripcion: item.descripcion ?? '', vehiculo: item.vehiculo ?? '',
    cantidad: item.cantidad == null ? 'consultar' : String(item.cantidad), precio: precioEnTexto(item.precio),
    marca: item.marca_id ? String(item.marca_id) : '',
  } : VACIO);
  const [errores, setErrores] = useState([]);
  const campo = (k) => ({ value: f[k], onInput: (x) => setF({ ...f, [k]: x.target.value }) });
  const perfil = sync.leerEstado().perfil;
  const marcas = useMemo(() => {
    const todas = (catalogos?.marcas ?? []).filter(m => m.activa);
    const mias = new Set(perfil?.marcas ?? []);
    return [...todas.filter(m => mias.has(m.id)), ...todas.filter(m => !mias.has(m.id))];
  }, [perfil]);
  async function guardar(ev) {
    ev.preventDefault();
    const e = await guardarItem(f, item);
    if (e.length) setErrores(e); else cerrar(true);
  }
  async function borrar() {
    if (!confirm(`¿Borrar ${item.codigo} de tu stock?`)) return;
    await borrarItem(item);
    cerrar(true);
  }
  return html`<${Modal} titulo=${item ? 'Cambiar repuesto' : 'Agregar repuesto'} cerrar=${() => cerrar(false)}>
    <form class="form" onSubmit=${guardar}>
      <label>Código *<input ...${campo('codigo')} maxlength="80" required /></label>
      <label>Descripción<input ...${campo('descripcion')} maxlength="300" /></label>
      <label>Vehículo<input ...${campo('vehiculo')} maxlength="150" placeholder="Ej: Ranger 2015" /></label>
      <div class="fila2">
        <label>Cantidad<input ...${campo('cantidad')} inputmode="numeric" placeholder="vacío = consultar" /></label>
        <label>Precio (USD)<input ...${campo('precio')} inputmode="decimal" placeholder="Ej: 35,50" /></label>
      </div>
      <label>Marca del vehículo
        <select ...${campo('marca')} onChange=${x => setF({ ...f, marca: x.target.value })}>
          <option value="">Automática (según el vehículo)</option>
          ${marcas.map(m => html`<option value=${m.id}>${m.nombre}${m.tipo !== 'AUTO' ? ` (${m.tipo.toLowerCase()})` : ''}</option>`)}
        </select>
      </label>
      ${errores.map(x => html`<p class="msg msg-error">${x}</p>`)}
      <div class="acciones">
        <button class="btn btn-primario" disabled=${bloqueado}>Guardar</button>
        ${item ? html`<button type="button" class="btn btn-peligro" disabled=${bloqueado} onClick=${borrar}>Borrar</button>` : null}
      </div>
    </form>
  <//>`;
}

function CargaExcel({ cerrar, cantidadActual }) {
  const [lectura, setLectura] = useState(null);
  const [archivo, setArchivo] = useState(null);
  const [leyendo, setLeyendo] = useState(false);
  async function elegir(ev) {
    const a = ev.target.files[0];
    if (!a) return;
    setArchivo(a);
    setLeyendo(true);
    if (!catalogos) await cargarCatalogos();
    setLectura(catalogos ? await leerExcel(a, catalogos.encabezados)
      : { errorFatal: 'Falta sincronizar una vez con conexión antes de cargar un Excel.' });
    setLeyendo(false);
  }
  async function confirmar() {
    await reemplazarStock(archivo.name, lectura.filas);
    cerrar(true);
  }
  return html`<${Modal} titulo="Cargar stock desde Excel" cerrar=${() => cerrar(false)}>
    <p class="chico">Columnas: código y cantidad (obligatorias), descripción, marca, vehículo, precio, reemplaza a,
      equivalentes. <a href="/plantilla.xlsx">Bajar la plantilla</a>.</p>
    <input type="file" accept=".xlsx,.xls" onChange=${elegir} />
    ${leyendo ? html`<p>Leyendo…</p>` : null}
    ${lectura?.errorFatal ? html`<p class="msg msg-error">${lectura.errorFatal}</p>` : null}
    ${lectura?.filas ? html`
      <p class="msg ${lectura.filas.length ? 'msg-ok' : 'msg-error'}">Se leyeron <b>${lectura.filas.length}</b> repuestos.
        ${lectura.errores.length ? html` <b>${lectura.errores.length}</b> filas con errores (no se cargan):` : null}</p>
      ${lectura.errores.length ? html`<pre class="errores">${lectura.errores.slice(0, 50).join('\n')}</pre>` : null}
      ${lectura.filas.length ? html`
        <p>Esto <b>reemplaza todo tu stock</b> (hoy tenés ${cantidadActual} repuestos). Se guarda en este equipo
          ahora y se sube cuando haya conexión.</p>
        <div class="acciones"><button class="btn btn-primario" onClick=${confirmar}>Reemplazar mi stock</button>
          <button class="btn" onClick=${() => cerrar(false)}>Cancelar</button></div>` : null}` : null}
  <//>`;
}

function MiStock({ e }) {
  const [items, setItems] = useState(null);
  const [filtro, setFiltro] = useState('');
  const [pagina, setPagina] = useState(0);
  const [porPagina, setPorPagina] = useState(10);
  const [editando, setEditando] = useState(null); // null | 'nuevo' | item
  const [excel, setExcel] = useState(false);
  async function cargar() { setItems(await db.filas('SELECT * FROM item ORDER BY codigo COLLATE NOCASE')); }
  useEffect(() => { cargar(); }, [e.ultimaSync, e.pendientes]);
  const filtrados = useMemo(() => {
    const ps = paraBuscar(filtro).split(/\s+/).filter(Boolean);
    if (!items || !ps.length) return items ?? [];
    return items.filter(i => {
      const t = paraBuscar([i.codigo, i.descripcion, i.vehiculo, i.marca_repuesto, nombreMarca(i.marca_id)].join(' '));
      return ps.every(p => t.includes(p));
    });
  }, [items, filtro]);
  const vista = filtrados.slice(pagina * porPagina, (pagina + 1) * porPagina);
  const cerrar = (cambio) => { setEditando(null); setExcel(false); if (cambio) cargar(); };
  return html`<section>
    <div class="titulo-seccion"><h2>Mi stock</h2><span class="chico">${items?.length ?? '…'} repuestos</span></div>
    <div class="herramientas">
      <input type="search" placeholder="Buscar en mi stock" value=${filtro} onInput=${x => { setFiltro(x.target.value); setPagina(0); }} />
      <button class="btn btn-primario" disabled=${e.bloqueado} onClick=${() => setEditando('nuevo')}>+ Agregar</button>
      <button class="btn" disabled=${e.bloqueado} onClick=${() => setExcel(true)}>Cargar Excel</button>
    </div>
    ${items && !items.length ? html`<p class="vacio">Todavía no cargaste repuestos. Subí tu Excel o agregalos uno por uno.</p>` : null}
    <ul class="lista">
      ${vista.map(i => html`<li class="item" onClick=${() => !e.bloqueado && setEditando(i)}>
        <div class="item-cab"><b class="codigo">${i.codigo}</b>
          ${i.pendiente ? html`<span class="etiqueta pendiente" title="Todavía no se subió al servidor">Sin subir</span>` : null}</div>
        <div>${i.descripcion ?? html`<i class="chico">Sin descripción</i>`}</div>
        <div class="chico">${[i.vehiculo, nombreMarca(i.marca_id)].filter(Boolean).join(' · ')}</div>
        <div class="item-pie"><span>${mostrarCantidad(i.cantidad)}</span><b class="precio">${mostrarPrecio(i.precio)}</b></div>
      </li>`)}
    </ul>
    <${Paginas} total=${filtrados.length} pagina=${pagina} porPagina=${porPagina} setPagina=${setPagina} setPorPagina=${setPorPagina} />
    ${editando ? html`<${FormItem} item=${editando === 'nuevo' ? null : editando} cerrar=${cerrar} bloqueado=${e.bloqueado} />` : null}
    ${excel ? html`<${CargaExcel} cerrar=${cerrar} cantidadActual=${items?.length ?? 0} />` : null}
  </section>`;
}

// ---------- Pedidos ----------

const ESTADO_PEDIDO = {
  PENDIENTE: 'Esperando respuesta del vendedor', OFERTADA: 'Con oferta del vendedor', CONTRAOFERTA: 'Con contraoferta del comprador',
  ACEPTADA: 'Aceptado', RECHAZADA: 'Rechazado', CANCELADA: 'Cancelado',
};

function Pedido({ p, bloqueado, alCambiar }) {
  const [precio, setPrecio] = useState('');
  const [error, setError] = useState(null);
  const contra = p.contraparte ? JSON.parse(p.contraparte) : {};
  const acciones = accionesPosibles(p);
  async function hacer(tipo) {
    if (tipo === 'PEDIDO_CANCELAR' && !confirm('¿Cancelar el pedido?')) return;
    const err = await accionPedido(p, tipo, precio);
    setError(err);
    if (!err) alCambiar();
  }
  const total = (u) => (u == null ? '' : ` · total ${mostrarPrecio(u * p.cantidad)}`);
  const boton = (tipo, texto, clase = 'btn') => html`<button class=${clase} disabled=${bloqueado} onClick=${() => hacer(tipo)}>${texto}</button>`;
  return html`<li class="item pedido">
    <div class="item-cab"><b class="codigo">${p.codigo}</b> <span class=${'etiqueta e-' + p.estado.toLowerCase()}>${ESTADO_PEDIDO[p.estado]}</span>
      ${p.pendiente ? html`<span class="etiqueta pendiente">Sin subir</span>` : null}</div>
    ${p.descripcion ? html`<div>${p.descripcion}</div>` : null}
    <div class="chico">${p.rol === 'VENDEDOR' ? 'Lo pide' : 'Se lo pediste a'} <b>${contra.nombre}</b>
      ${contra.ciudad ? ` de ${contra.ciudad}, ${contra.estado}` : ''} · ${p.cantidad} ${p.cantidad === 1 ? 'unidad' : 'unidades'}</div>
    <div class="chico">Publicado: ${mostrarPrecio(p.precio_usd)}
      ${p.precio_oferta != null ? html` · Oferta: <b>${mostrarPrecio(p.precio_oferta)}</b>${total(p.precio_oferta)}` : null}
      ${p.precio_contraoferta != null ? html` · Contraoferta: <b>${mostrarPrecio(p.precio_contraoferta)}</b>${total(p.precio_contraoferta)}` : null}</div>
    ${p.estado === 'ACEPTADA' && contra.telefono ? html`<a class="btn btn-wa" href=${linkWhatsapp(contra.telefono)} target="_blank" rel="noopener">Escribirle por WhatsApp</a>` : null}
    ${acciones.length ? html`<div class="acciones">
      ${acciones.includes('PEDIDO_OFERTAR') || acciones.includes('PEDIDO_CONTRAOFERTAR') ? html`
        <input class="precio-input" inputmode="decimal" placeholder="Precio c/u (USD)" value=${precio} onInput=${x => setPrecio(x.target.value)} />` : null}
      ${acciones.includes('PEDIDO_OFERTAR') ? boton('PEDIDO_OFERTAR', 'Tengo stock: ofertar', 'btn btn-primario') : null}
      ${acciones.includes('PEDIDO_SIN_STOCK') ? boton('PEDIDO_SIN_STOCK', 'No lo tengo') : null}
      ${acciones.includes('PEDIDO_ACEPTAR_CONTRAOFERTA') ? boton('PEDIDO_ACEPTAR_CONTRAOFERTA', 'Aceptar contraoferta', 'btn btn-primario') : null}
      ${acciones.includes('PEDIDO_RECHAZAR_CONTRAOFERTA') ? boton('PEDIDO_RECHAZAR_CONTRAOFERTA', 'Rechazar') : null}
      ${acciones.includes('PEDIDO_ACEPTAR_OFERTA') ? boton('PEDIDO_ACEPTAR_OFERTA', 'Aceptar oferta', 'btn btn-primario') : null}
      ${acciones.includes('PEDIDO_CONTRAOFERTAR') ? boton('PEDIDO_CONTRAOFERTAR', 'Contraofertar') : null}
      ${acciones.includes('PEDIDO_CANCELAR') ? boton('PEDIDO_CANCELAR', 'Cancelar pedido', 'btn btn-peligro') : null}
    </div>` : null}
    ${error ? html`<p class="msg msg-error">${error}</p>` : null}
  </li>`;
}

function Pedidos({ e }) {
  const [rol, setRol] = useState('VENDEDOR');
  const [pedidos, setPedidos] = useState([]);
  const [pagina, setPagina] = useState(0);
  const [porPagina, setPorPagina] = useState(10);
  async function cargar() {
    setPedidos(await db.filas(`SELECT * FROM pedido WHERE rol = ? ORDER BY
      CASE WHEN estado IN ('ACEPTADA','RECHAZADA','CANCELADA') THEN 1 ELSE 0 END, coalesce(actualizada_en, creada_en) DESC`, [rol]));
  }
  useEffect(() => { cargar(); setPagina(0); }, [rol, e.ultimaSync, e.pendientes]);
  const vista = pedidos.slice(pagina * porPagina, (pagina + 1) * porPagina);
  return html`<section>
    <div class="titulo-seccion"><h2>Pedidos</h2></div>
    <div class="pestanas">
      <button class=${rol === 'VENDEDOR' ? 'activa' : ''} onClick=${() => setRol('VENDEDOR')}>Me pidieron</button>
      <button class=${rol === 'COMPRADOR' ? 'activa' : ''} onClick=${() => setRol('COMPRADOR')}>Pedí yo</button>
    </div>
    ${!pedidos.length ? html`<p class="vacio">No hay pedidos.</p>` : null}
    <ul class="lista">${vista.map(p => html`<${Pedido} key=${p.uuid} p=${p} bloqueado=${e.bloqueado} alCambiar=${cargar} />`)}</ul>
    <${Paginas} total=${pedidos.length} pagina=${pagina} porPagina=${porPagina} setPagina=${setPagina} setPorPagina=${setPorPagina} />
  </section>`;
}

// ---------- Buscar ----------

function Pedir({ r, cerrar, online }) {
  const [cantidad, setCantidad] = useState('1');
  const [msg, setMsg] = useState(null);
  const [enviando, setEnviando] = useState(false);
  async function pedir() {
    setEnviando(true);
    try {
      await api.pedidoNuevo(r.uuid, Number(cantidad));
      setMsg({ ok: true, texto: 'Listo: el pedido le llegó al vendedor. Lo seguís en "Pedidos".' });
      sync.sincronizar();
    } catch (err) {
      setMsg({ ok: false, texto: err instanceof SinConexion ? 'Se cortó la conexión. Probá de nuevo.' : err.message });
    } finally {
      setEnviando(false);
    }
  }
  return html`<${Modal} titulo=${'Pedir ' + r.codigo} cerrar=${cerrar}>
    <p>${r.descripcion ?? ''} <br /><span class="chico">${r.vendedor_nombre} · ${r.ciudad}, ${r.estado}</span></p>
    <p><b class="precio">${mostrarPrecio(r.precio)}</b> · ${mostrarCantidad(r.cantidad)}</p>
    ${!online ? html`<p class="msg msg-aviso">Para pedir necesitás conexión (el vendedor tiene que confirmar su stock actual).</p>` : null}
    ${msg ? html`<p class=${'msg ' + (msg.ok ? 'msg-ok' : 'msg-error')}>${msg.texto}</p>` : null}
    ${!msg?.ok ? html`<div class="acciones">
      <label class="en-linea">Cantidad <input class="cantidad-input" inputmode="numeric" value=${cantidad} onInput=${x => setCantidad(x.target.value)} /></label>
      <button class="btn btn-primario" disabled=${!online || enviando} onClick=${pedir}>${enviando ? 'Enviando…' : 'Hacer el pedido'}</button>
    </div>` : null}
  <//>`;
}

function Buscar({ e }) {
  const [texto, setTexto] = useState('');
  const [estadoF, setEstadoF] = useState('');
  const [marcaF, setMarcaF] = useState('');
  const [res, setRes] = useState(null);
  const [pagina, setPagina] = useState(0);
  const [porPagina, setPorPagina] = useState(10);
  const [pidiendo, setPidiendo] = useState(null);
  const ultimo = useRef(0);
  useEffect(() => {
    if (!texto.trim() && !estadoF && !marcaF) { setRes(null); return; }
    const n = ++ultimo.current;
    const t = setTimeout(async () => {
      const r = await buscar(texto, { estado: estadoF, marcaId: marcaF }, e.perfil, catalogos?.estados);
      if (n === ultimo.current) { setRes(r); setPagina(0); }
    }, 250);
    return () => clearTimeout(t);
  }, [texto, estadoF, marcaF, e.catalogo.cantidad]);
  const estados = catalogos?.estados?.map(x => x.estado) ?? [];
  const marcas = (catalogos?.marcas ?? []).filter(m => (e.perfil?.marcas ?? []).includes(m.id));
  const vista = res ? res.filas.slice(pagina * porPagina, (pagina + 1) * porPagina) : [];
  return html`<section>
    <div class="titulo-seccion"><h2>Buscar repuestos</h2></div>
    <p class="chico">${e.catalogo.cantidad
      ? html`Buscás en ${e.catalogo.cantidad.toLocaleString('es-VE')} repuestos de otros vendedores
          (${e.catalogo.bajando ? `bajando… ${e.catalogo.bajados}` : `actualizado ${hace(e.catalogo.ultima)}`}). Funciona sin conexión.`
      : e.catalogo.bajando ? `Bajando el stock de los otros vendedores… ${e.catalogo.bajados}`
      : 'Todavía no se bajó el stock de los otros vendedores: se baja con conexión por Wi-Fi (o activá los datos móviles en "Estado").'}</p>
    <div class="herramientas">
      <input type="search" placeholder="Código o palabras: disco ranger, bujia civic…" value=${texto} onInput=${x => setTexto(x.target.value)} />
      <select value=${estadoF} onChange=${x => setEstadoF(x.target.value)}><option value="">Todo el país</option>
        ${estados.map(s => html`<option value=${s}>${s}</option>`)}</select>
      ${marcas.length > 1 ? html`<select value=${marcaF} onChange=${x => setMarcaF(x.target.value)}><option value="">Todas mis marcas</option>
        ${marcas.map(m => html`<option value=${m.id}>${m.nombre}</option>`)}</select>` : null}
    </div>
    ${res && !res.filas.length ? html`<p class="vacio">No se encontró nada${e.online ? '' : ' en el stock bajado'}.</p>` : null}
    ${res?.masDe ? html`<p class="chico">Hay muchos resultados: se muestran los primeros 500. Escribí algo más preciso.</p>` : null}
    <ul class="lista">${vista.map(r => html`<li class="item" onClick=${() => setPidiendo(r)}>
      <div class="item-cab"><b class="codigo">${r.codigo}</b>${r._mismaCiudad ? html`<span class="etiqueta cerca">En tu ciudad</span>` : null}</div>
      <div>${r.descripcion ?? ''}</div>
      <div class="chico">${[r.vehiculo, nombreMarca(r.marca_id), r.marca_repuesto].filter(Boolean).join(' · ')}</div>
      <div class="chico">${r.vendedor_nombre} · ${r.ciudad}, ${r.estado}</div>
      <div class="item-pie"><span>${mostrarCantidad(r.cantidad)}</span><b class="precio">${mostrarPrecio(r.precio)}</b></div>
    </li>`)}</ul>
    ${res ? html`<${Paginas} total=${res.filas.length} pagina=${pagina} porPagina=${porPagina} setPagina=${setPagina} setPorPagina=${setPorPagina} />` : null}
    ${pidiendo ? html`<${Pedir} r=${pidiendo} online=${e.online} cerrar=${() => setPidiendo(null)} />` : null}
  </section>`;
}

// ---------- Estado ----------

let eventoInstalar = null;
addEventListener('beforeinstallprompt', (ev) => { ev.preventDefault(); eventoInstalar = ev; document.dispatchEvent(new Event('instalable')); });

function Estado({ e, persistente }) {
  useTic();
  const [lista, setLista] = useState([]);
  const [instalable, setInstalable] = useState(!!eventoInstalar);
  useEffect(() => {
    avisos.listar().then(setLista).then(() => avisos.marcarLeidos()).then(() => sync.refrescar());
    const f = () => setInstalable(true);
    document.addEventListener('instalable', f);
    return () => document.removeEventListener('instalable', f);
  }, []);
  async function cerrarSesion() {
    const msg = e.pendientes
      ? `Hay ${e.pendientes} cambios que todavía no se subieron y se van a PERDER. ¿Cerrar sesión igual?`
      : 'Se borra todo lo guardado en este equipo. ¿Cerrar sesión?';
    if (confirm(msg)) await sync.salir();
  }
  const t = textoIndicador(e);
  return html`<section>
    <div class="titulo-seccion"><h2>Estado</h2></div>
    <div class=${'estado-caja ' + t.clase}><span class="punto"></span> ${t.texto}</div>
    <dl class="datos">
      <dt>Usuario</dt><dd>${e.sesion?.usuario?.nombre} (${e.sesion?.usuario?.username})</dd>
      <dt>Última sincronización</dt><dd>${e.ultimaSync ? new Date(e.ultimaSync).toLocaleString('es-VE') : 'nunca'}</dd>
      <dt>Cambios por subir</dt><dd>${e.pendientes}</dd>
      <dt>Stock de otros vendedores</dt><dd>${e.catalogo.cantidad.toLocaleString('es-VE')} repuestos · ${hace(e.catalogo.ultima)}</dd>
      <dt>Guardado en este equipo</dt><dd>${persistente ? 'Sí (queda aunque se apague)' : 'No: este navegador no permite guardar. Usá Chrome actualizado.'}</dd>
    </dl>
    ${e.error ? html`<p class="msg msg-error">${e.error}</p>` : null}
    <div class="acciones">
      <button class="btn btn-primario" disabled=${e.sincronizando} onClick=${() => sync.sincronizar({ catalogo: true })}>Sincronizar ahora</button>
      ${instalable ? html`<button class="btn" onClick=${async () => { eventoInstalar.prompt(); await eventoInstalar.userChoice; eventoInstalar = null; setInstalable(false); }}>Instalar la app</button>` : null}
    </div>
    <label class="interruptor"><input type="checkbox" checked=${e.datosMoviles} onChange=${x => sync.setDatosMoviles(x.target.checked)} />
      Bajar el stock de los otros vendedores también con datos móviles (si no, solo con Wi-Fi)</label>
    <h3>Avisos</h3>
    ${!lista.length ? html`<p class="chico">No hay avisos.</p>` : null}
    <ul class="lista">${lista.map(a => html`<li class=${'item aviso ' + (a.resultado === 'RECHAZADA' || a.resultado === 'CONFLICTO' ? 'aviso-error' : '')}>
      <div class="item-cab"><b>${a.tipo}</b><span class="chico">${hace(a.creado_en)}</span></div>
      <div>${a.resultado === 'CONFLICTO' ? 'No se aplicó: ' : a.resultado === 'RECHAZADA' ? 'Rechazado: ' : ''}${a.mensaje}</div>
    </li>`)}</ul>
    <h3>Otras opciones</h3>
    <p class="chico">Perfil, marcas que trabajás, "avisarme cuando aparezca" y administración: en la <a href="/">versión web</a>.</p>
    <button class="btn btn-peligro" onClick=${cerrarSesion}>Cerrar sesión en este equipo</button>
  </section>`;
}

// ---------- App ----------

function Actualizacion() {
  const [hay, setHay] = useState(false);
  useEffect(() => { const f = () => setHay(true); document.addEventListener('nueva-version', f); return () => document.removeEventListener('nueva-version', f); }, []);
  if (!hay) return null;
  return html`<div class="banner">Hay una versión nueva de la app.
    <button class="btn btn-chico" onClick=${() => { window.__swEsperando?.postMessage('SKIP_WAITING'); }}>Actualizar</button></div>`;
}

function App({ persistente }) {
  const e = useEstado();
  const hash = useHash();
  if (!e.sesion || e.sinSesion) return html`<${Login} e=${e} />`;
  const pantalla = hash.startsWith('#/pedidos') ? 'pedidos' : hash.startsWith('#/buscar') ? 'buscar' : hash.startsWith('#/estado') ? 'estado' : 'stock';
  const nav = (id, texto, icono) => html`<a href=${'#/' + id} class=${pantalla === id ? 'activa' : ''}><span class="ico">${icono}</span>${texto}</a>`;
  return html`
    <header class="top">
      <a class="logo" href="#/stock"><img src="img/icono.svg" alt="" width="28" height="28" /><span>Stock <b>Multimarca</b></span></a>
      <${Indicador} e=${e} />
    </header>
    <${Actualizacion} />
    ${e.bloqueado ? html`<div class="banner banner-error">Hace más de ${e.config.diasSinSincronizar} días que este equipo no
      sincroniza. Conectate a internet para seguir haciendo cambios (podés seguir consultando).</div>` : null}
    ${e.perfil && !e.perfil.perfilCompleto ? html`<div class="banner">Te falta completar tu perfil (teléfono, estado y ciudad)
      para publicar y pedir. <a href="/configuracion">Completalo en la web</a>.</div>` : null}
    <main class="contenido">
      ${pantalla === 'stock' ? html`<${MiStock} e=${e} />` : null}
      ${pantalla === 'pedidos' ? html`<${Pedidos} e=${e} />` : null}
      ${pantalla === 'buscar' ? html`<${Buscar} e=${e} />` : null}
      ${pantalla === 'estado' ? html`<${Estado} e=${e} persistente=${persistente} />` : null}
    </main>
    <nav class="abajo">
      ${nav('stock', 'Mi stock', '▦')}${nav('pedidos', 'Pedidos', '⇄')}${nav('buscar', 'Buscar', '⌕')}${nav('estado', 'Estado', '●')}
    </nav>`;
}

async function arrancar() {
  const raiz = document.getElementById('app');
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js').then((reg) => {
      const avisar = (w) => { window.__swEsperando = w; document.dispatchEvent(new Event('nueva-version')); };
      if (reg.waiting && navigator.serviceWorker.controller) avisar(reg.waiting);
      reg.addEventListener('updatefound', () => {
        const w = reg.installing;
        w?.addEventListener('statechange', () => { if (w.state === 'installed' && navigator.serviceWorker.controller) avisar(w); });
      });
    }).catch(err => console.warn('Sin service worker:', err));
    // Recargar solo cuando se actualiza la app (no la primera vez que se instala el service worker)
    const habiaControlador = !!navigator.serviceWorker.controller;
    let recargando = false;
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      if (habiaControlador && !recargando) { recargando = true; location.reload(); }
    });
  }
  try {
    const { persistente } = await sync.iniciar();
    await cargarCatalogos();
    render(html`<${App} persistente=${persistente} />`, raiz);
    if (sync.leerEstado().sesion) sync.sincronizar();
  } catch (err) {
    const msg = String(err.message).includes('OTRA_PESTANA')
      ? 'La app ya está abierta en otra pestaña o ventana. Cerrá las otras y recargá esta.'
      : 'No se pudo abrir la base de este equipo: ' + err.message;
    render(html`<main class="login"><div class="tarjeta"><p class="msg msg-error">${msg}</p>
      <button class="btn" onClick=${() => location.reload()}>Reintentar</button></div></main>`, raiz);
  }
}

arrancar();
