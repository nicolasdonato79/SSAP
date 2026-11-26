// Acceso a la base local (corre en db-worker.js). Todas las funciones devuelven promesas.

const worker = new Worker(new URL('./db-worker.js', import.meta.url), { type: 'module' });
let siguiente = 1;
const esperando = new Map();

worker.onmessage = (e) => {
  const { id, ok, r, error } = e.data;
  const p = esperando.get(id);
  if (!p) return;
  esperando.delete(id);
  ok ? p.resolve(r) : p.reject(new Error(error));
};

function llamar(accion, ...args) {
  const id = siguiente++;
  return new Promise((resolve, reject) => {
    esperando.set(id, { resolve, reject });
    worker.postMessage({ id, accion, args });
  });
}

export const db = {
  abrir: () => llamar('abrir'),
  filas: (sql, bind) => llamar('filas', sql, bind),
  async fila(sql, bind) {
    return (await llamar('filas', sql, bind))[0] ?? null;
  },
  async valor(sql, bind) {
    const f = (await llamar('filas', sql, bind))[0];
    return f ? Object.values(f)[0] : null;
  },
  ejecutar: (sql, bind) => llamar('ejecutar', sql, bind),
  /** [{sql, bind, filas?}] en una sola transacción. */
  lote: (sentencias) => llamar('lote', sentencias),
  guardarOtros: (pagina) => llamar('guardarOtros', pagina),
  borrarOtros: () => llamar('borrarOtros'),
  borrarTodo: () => llamar('borrarTodo'),

  async meta(clave) {
    const v = await this.valor('SELECT valor FROM meta WHERE clave = ?', [clave]);
    return v == null ? null : JSON.parse(v);
  },
  setMeta(clave, valor) {
    return llamar('ejecutar', 'INSERT OR REPLACE INTO meta VALUES (?, ?)', [clave, JSON.stringify(valor)]);
  },
  metaSql(clave, valor) {
    return { sql: 'INSERT OR REPLACE INTO meta VALUES (?, ?)', bind: [clave, JSON.stringify(valor)] };
  },
};
