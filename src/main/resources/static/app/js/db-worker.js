// Base local: SQLite (WASM) guardado en el almacenamiento privado del navegador (OPFS).
// Corre en un Worker porque OPFS solo se puede usar así. Si se corta la luz, lo que ya se confirmó queda.
import sqlite3InitModule from '../vendor/sqlite/sqlite3.js';

const ESQUEMA = 3;

let db = null;
let persistente = false;

const TABLAS = `
CREATE TABLE IF NOT EXISTS meta (clave TEXT PRIMARY KEY, valor TEXT);

-- Mi stock
CREATE TABLE IF NOT EXISTS item (
  uuid TEXT PRIMARY KEY, version INTEGER, codigo TEXT NOT NULL, codigo_norm TEXT NOT NULL, descripcion TEXT,
  marca_repuesto TEXT, vehiculo TEXT, marca_id INTEGER, cantidad INTEGER, precio REAL, cargado_en TEXT,
  actualizado_en TEXT, pendiente INTEGER NOT NULL DEFAULT 0, gen INTEGER NOT NULL DEFAULT 0, orden INTEGER);
CREATE INDEX IF NOT EXISTS ix_item_codigo ON item (codigo);

-- Pedidos (como vendedor y como comprador)
CREATE TABLE IF NOT EXISTS pedido (
  uuid TEXT PRIMARY KEY, version INTEGER, rol TEXT, codigo TEXT, descripcion TEXT, cantidad INTEGER,
  precio_usd REAL, precio_oferta REAL, precio_contraoferta REAL, estado TEXT, creada_en TEXT, actualizada_en TEXT,
  contraparte TEXT, pendiente INTEGER NOT NULL DEFAULT 0, gen INTEGER NOT NULL DEFAULT 0);

CREATE TABLE IF NOT EXISTS demanda (uuid TEXT PRIMARY KEY, version INTEGER, texto TEXT, estado TEXT, creada_en TEXT,
  gen INTEGER NOT NULL DEFAULT 0);

-- Stock de los otros vendedores (para buscar sin conexión)
CREATE TABLE IF NOT EXISTS otro (
  rid INTEGER PRIMARY KEY, uuid TEXT NOT NULL UNIQUE, codigo TEXT, codigo_norm TEXT, descripcion TEXT,
  marca_repuesto TEXT, vehiculo TEXT, marca_id INTEGER, cantidad INTEGER, precio REAL, cargado_en TEXT,
  vendedor TEXT, vendedor_nombre TEXT, estado TEXT, ciudad TEXT, texto TEXT);
CREATE INDEX IF NOT EXISTS ix_otro_codigo ON otro (codigo_norm);
CREATE VIRTUAL TABLE IF NOT EXISTS otro_fts USING fts5(texto, content='', tokenize='trigram');

CREATE TABLE IF NOT EXISTS relacion (codigo_a TEXT, codigo_b TEXT, tipo TEXT, PRIMARY KEY (codigo_a, codigo_b, tipo));
CREATE INDEX IF NOT EXISTS ix_relacion_b ON relacion (codigo_b);

-- Cambios hechos acá que todavía no llegaron al servidor, en orden
CREATE TABLE IF NOT EXISTS outbox (
  seq INTEGER PRIMARY KEY AUTOINCREMENT, op_id TEXT NOT NULL UNIQUE, tipo TEXT NOT NULL, uuid TEXT,
  base_version INTEGER, hecha_en TEXT NOT NULL, datos TEXT, enviada INTEGER NOT NULL DEFAULT 0);

-- Avisos para el usuario (conflictos, rechazos)
CREATE TABLE IF NOT EXISTS aviso (
  id INTEGER PRIMARY KEY AUTOINCREMENT, tipo TEXT, resultado TEXT, mensaje TEXT, creado_en TEXT,
  leido INTEGER NOT NULL DEFAULT 0);
`;

async function abrir() {
  if (db) return { persistente };
  const sqlite3 = await sqlite3InitModule();
  try {
    // Al recargar, la página anterior puede tardar un momento en soltar la base: se reintenta unos segundos
    let pool = null;
    for (let intento = 0; !pool; intento++) {
      try {
        pool = await sqlite3.installOpfsSAHPoolVfs({ name: 'ssap' });
      } catch (e) {
        if (intento >= 8) throw e;
        await new Promise(r => setTimeout(r, 500));
      }
    }
    db = new pool.OpfsSAHPoolDb('/ssap.db');
    persistente = true;
  } catch (e) {
    // Con OPFS disponible, el error es casi siempre que la app ya está abierta en otra pestaña
    // (la base se abre en exclusiva): no se sigue, para no trabajar sobre una copia en memoria.
    if (self.navigator?.storage?.getDirectory && self.FileSystemSyncAccessHandle) {
      throw new Error('OTRA_PESTANA');
    }
    // Navegador sin OPFS (muy viejo o en modo privado): funciona, pero se pierde al cerrar
    console.warn('Sin almacenamiento persistente:', e);
    db = new sqlite3.oo1.DB(':memory:');
    persistente = false;
  }
  db.exec('PRAGMA journal_mode=DELETE; PRAGMA synchronous=FULL;');
  const v = db.selectValue('PRAGMA user_version');
  if (v !== ESQUEMA) {
    // Versión vieja del esquema local: se borra todo y se baja de nuevo (menos lo pendiente de subir)
    const pendientes = v ? tryRows('SELECT * FROM outbox ORDER BY seq') : [];
    const metas = v ? tryRows("SELECT * FROM meta WHERE clave IN ('sesion','dispositivo','usuario')") : [];
    for (const t of ['meta', 'item', 'pedido', 'demanda', 'otro', 'otro_fts', 'relacion', 'outbox', 'aviso']) {
      db.exec(`DROP TABLE IF EXISTS ${t}`);
    }
    db.exec(TABLAS);
    for (const m of metas) db.exec({ sql: 'INSERT INTO meta VALUES (?, ?)', bind: [m.clave, m.valor] });
    for (const o of pendientes) {
      db.exec({ sql: 'INSERT INTO outbox (op_id, tipo, uuid, base_version, hecha_en, datos) VALUES (?,?,?,?,?,?)',
        bind: [o.op_id, o.tipo, o.uuid, o.base_version, o.hecha_en, o.datos] });
    }
    db.exec(`PRAGMA user_version = ${ESQUEMA}`);
  }
  return { persistente };
}

function tryRows(sql) {
  try { return db.selectObjects(sql); } catch { return []; }
}

function filas(sql, bind) {
  return db.selectObjects(sql, bind ?? []);
}

function ejecutar(sql, bind) {
  db.exec({ sql, bind: bind ?? [] });
  return db.changes();
}

/** Varias sentencias en una transacción: o se guardan todas o ninguna. */
function lote(sentencias) {
  const resultados = [];
  db.transaction(() => {
    for (const s of sentencias) {
      if (s.filas) resultados.push(db.selectObjects(s.sql, s.bind ?? []));
      else { db.exec({ sql: s.sql, bind: s.bind ?? [] }); resultados.push(db.changes()); }
    }
  });
  return resultados;
}

/** Guarda una página del stock de los otros (con su índice de búsqueda). */
function guardarOtros({ items, borrados, relaciones }) {
  db.transaction(() => {
    const rid = (uuid) => db.selectValue('SELECT rid FROM otro WHERE uuid = ?', [uuid]);
    const sacar = (uuid) => {
      const r = rid(uuid);
      if (r != null) {
        const viejo = db.selectValue('SELECT texto FROM otro WHERE rid = ?', [r]);
        db.exec({ sql: "INSERT INTO otro_fts (otro_fts, rowid, texto) VALUES ('delete', ?, ?)", bind: [r, viejo] });
        db.exec({ sql: 'DELETE FROM otro WHERE rid = ?', bind: [r] });
      }
    };
    for (const u of borrados) sacar(u);
    for (const i of items) {
      sacar(i.uuid);
      db.exec({
        sql: `INSERT INTO otro (uuid, codigo, codigo_norm, descripcion, marca_repuesto, vehiculo, marca_id, cantidad,
              precio, cargado_en, vendedor, vendedor_nombre, estado, ciudad, texto)
              VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`,
        bind: [i.uuid, i.codigo, i.codigoNorm, i.descripcion, i.marcaRepuesto, i.vehiculo, i.marcaId, i.cantidad,
          i.precioUsd, i.cargadoEn, i.vendedor, i.vendedorNombre, i.estado, i.ciudad, i.texto],
      });
      const r = db.selectValue('SELECT last_insert_rowid()');
      db.exec({ sql: 'INSERT INTO otro_fts (rowid, texto) VALUES (?, ?)', bind: [r, i.texto] });
    }
    for (const r of relaciones) {
      db.exec({ sql: 'INSERT OR IGNORE INTO relacion VALUES (?,?,?)', bind: [r.codigoA, r.codigoB, r.tipo] });
    }
  });
  return true;
}

function borrarOtros() {
  db.transaction(() => {
    db.exec('DELETE FROM otro');
    db.exec("INSERT INTO otro_fts (otro_fts) VALUES ('delete-all')");
  });
  return true;
}

/** Cerrar sesión de otro usuario: se borra todo lo de este equipo. */
function borrarTodo() {
  db.transaction(() => {
    for (const t of ['meta', 'item', 'pedido', 'demanda', 'otro', 'relacion', 'outbox', 'aviso']) db.exec(`DELETE FROM ${t}`);
    db.exec("INSERT INTO otro_fts (otro_fts) VALUES ('delete-all')");
  });
  return true;
}

const ACCIONES = { abrir, filas, ejecutar, lote, guardarOtros, borrarOtros, borrarTodo };

onmessage = async (e) => {
  const { id, accion, args } = e.data;
  try {
    const r = await ACCIONES[accion](...(args ?? []));
    postMessage({ id, ok: true, r });
  } catch (err) {
    postMessage({ id, ok: false, error: String(err?.message ?? err) });
  }
};
