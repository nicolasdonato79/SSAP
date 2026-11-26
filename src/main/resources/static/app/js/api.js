// Llamadas a la API del servidor (/api). Sin token o con el token vencido: SinSesion.

let token = null;

export function usarToken(t) {
  token = t;
}

export class SinSesion extends Error {}
export class SinConexion extends Error {}

/** Error del servidor con mensaje para mostrar. */
export class ErrorApi extends Error {
  constructor(status, cuerpo) {
    super(cuerpo?.mensaje ?? `Error ${status}`);
    this.status = status;
    this.cuerpo = cuerpo;
  }
}

async function llamar(metodo, ruta, { cuerpo, timeout = 20000, headers = {} } = {}) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeout);
  let r;
  try {
    r = await fetch('/api' + ruta, {
      method: metodo,
      headers: {
        ...(cuerpo !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
        ...headers,
      },
      body: cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined,
      signal: ctrl.signal,
      cache: 'no-store',
    });
  } catch (e) {
    throw new SinConexion(String(e));
  } finally {
    clearTimeout(t);
  }
  if (r.status === 401) {
    const c = await r.json().catch(() => null);
    if (c?.error === 'LOGIN') throw new ErrorApi(401, c);
    throw new SinSesion();
  }
  if (r.status === 304) return { status: 304, cuerpo: null, headers: r.headers };
  // Cloudflare/servidor caído: es como no tener conexión
  if (r.status >= 502 && r.status <= 504) throw new SinConexion(`HTTP ${r.status}`);
  const txt = await r.text();
  let c = null;
  try { c = txt ? JSON.parse(txt) : null; } catch { c = null; }
  if (!r.ok) throw new ErrorApi(r.status, c);
  return { status: r.status, cuerpo: c, headers: r.headers };
}

export const api = {
  /** ¿Hay conexión de verdad con el servidor? (tener Wi-Fi no alcanza) */
  async ping() {
    try {
      const r = await llamar('GET', '/sync/ping', { timeout: 6000 });
      return r.cuerpo;
    } catch (e) {
      if (e instanceof SinConexion) return null;
      throw e;
    }
  },
  login: (usuario, password, dispositivoId, nombreDispositivo) =>
    llamar('POST', '/auth/dispositivo', { cuerpo: { usuario, password, dispositivoId, nombreDispositivo } }).then(r => r.cuerpo),
  salir: () => llamar('POST', '/auth/salir', { cuerpo: {} }),
  pull: (cursor, limite = 500) =>
    llamar('GET', `/sync/pull?limite=${limite}` + (cursor ? `&cursor=${encodeURIComponent(cursor)}` : '')).then(r => r.cuerpo),
  catalogo: (cursor, marcas, limite = 2000) =>
    llamar('GET', `/sync/catalogo?limite=${limite}` + (cursor ? `&cursor=${encodeURIComponent(cursor)}` : '')
      + (marcas?.length ? `&marcas=${marcas.join(',')}` : ''), { timeout: 60000 }).then(r => r.cuerpo),
  push: (operaciones) =>
    llamar('POST', '/sync/push', { cuerpo: { relojCliente: new Date().toISOString(), operaciones }, timeout: 60000 })
      .then(r => r.cuerpo),
  catalogos: (etag) => llamar('GET', '/sync/catalogos', { headers: etag ? { 'If-None-Match': etag } : {} }),
  pedidoNuevo: (itemUuid, cantidad) =>
    llamar('POST', '/sync/pedido-nuevo', { cuerpo: { itemUuid, cantidad } }).then(r => r.cuerpo),
};
