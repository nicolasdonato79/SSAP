// Búsqueda sin conexión en el stock bajado de los otros vendedores, con las reglas de ItemStockRepository.buscar:
// por código (más sus reemplazos y equivalentes) o porque todas las palabras aparecen en código, descripción,
// marca o vehículo. Orden: misma ciudad, mismo estado, distancia entre estados; después código exacto.
import { db } from './db.js';
import { normalizarCodigo, paraBuscar } from './texto.js';

const MAX_RESULTADOS = 500;

/** Códigos que le sirven a quien busca este (EquivalenciaService.SIRVEN_PARA). */
async function sirvenPara(codigo) {
  if (!codigo) return [];
  const r = await db.filas(`
    WITH RECURSIVE aristas(desde, hacia) AS (
        SELECT codigo_a, codigo_b FROM relacion WHERE tipo = 'REEMPLAZO'
        UNION ALL SELECT codigo_a, codigo_b FROM relacion WHERE tipo = 'EQUIVALENTE'
        UNION ALL SELECT codigo_b, codigo_a FROM relacion WHERE tipo = 'EQUIVALENTE'),
      sirve(codigo, nivel) AS (
        SELECT ?, 0
        UNION SELECT a.hacia, s.nivel + 1 FROM sirve s JOIN aristas a ON a.desde = s.codigo WHERE s.nivel < 20)
    SELECT DISTINCT codigo FROM sirve`, [codigo]);
  return r.map(x => x.codigo);
}

function distanciaKm(a, b) {
  if (!a || !b) return null;
  const rad = (g) => (g * Math.PI) / 180;
  const h = Math.sin(rad(b.lat - a.lat) / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(rad(b.lon - a.lon) / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.sqrt(h));
}

/**
 * @param texto lo que escribió
 * @param filtros {estado, marcaId}
 * @param yo {estado, ciudad} del que busca (para ordenar por cercanía)
 * @param estados catalogos.estados (ubicación de cada estado)
 */
export async function buscar(texto, filtros, yo, estados) {
  const codigo = normalizarCodigo(texto);
  const codigos = await sirvenPara(codigo);
  const palabras = paraBuscar(texto).split(/\s+/).filter(Boolean).slice(0, 8);

  const condiciones = [];
  const bind = [];
  if (palabras.length) {
    const todas = palabras.map(w => {
      if (w.length >= 3) {
        bind.push('"' + w.replaceAll('"', '""') + '"');
        return 'rid IN (SELECT rowid FROM otro_fts WHERE otro_fts MATCH ?)';
      }
      bind.push('%' + w + '%');
      return 'texto LIKE ?';
    }).join(' AND ');
    if (codigos.length) {
      condiciones.push(`(codigo_norm IN (${codigos.map(() => '?').join(',')}) OR (${todas}))`);
      bind.unshift(...codigos);
    } else {
      condiciones.push(`(${todas})`);
    }
  }
  if (filtros.estado) { condiciones.push('estado = ?'); bind.push(filtros.estado); }
  if (filtros.marcaId) { condiciones.push('marca_id = ?'); bind.push(Number(filtros.marcaId)); }
  condiciones.push('(cantidad IS NULL OR cantidad > 0)');

  const filas = await db.filas(
    `SELECT * FROM otro WHERE ${condiciones.join(' AND ')} LIMIT ${MAX_RESULTADOS + 1}`, bind);
  const masDe = filas.length > MAX_RESULTADOS;
  if (masDe) filas.pop();

  const ubic = new Map((estados ?? []).map(e => [e.estado, e]));
  const mia = ubic.get(yo?.estado);
  const ciudad = (yo?.ciudad ?? '').toLowerCase();
  const set = new Set(codigos);
  for (const f of filas) {
    f._mismaCiudad = !!yo?.estado && f.estado === yo.estado && (f.ciudad ?? '').toLowerCase() === ciudad;
    f._km = distanciaKm(mia, ubic.get(f.estado));
    f._exacto = f.codigo_norm === codigo;
    f._porCodigo = set.has(f.codigo_norm);
  }
  filas.sort((a, b) =>
    (b._mismaCiudad - a._mismaCiudad)
    || ((a._km ?? 1e9) - (b._km ?? 1e9))
    || (b._exacto - a._exacto)
    || (b._porCodigo - a._porCodigo)
    || a.rid - b.rid);
  return { filas, masDe };
}
