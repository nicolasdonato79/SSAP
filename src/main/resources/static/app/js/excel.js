// Lee el Excel de stock en el equipo (también sin conexión), con las mismas reglas que ProcesadorFilas.java.
// Los sinónimos de los encabezados vienen del servidor (catálogos), así no hay dos listas para mantener.
import { readSheet } from '../vendor/read-excel-file.js';
import { normalizarCodigo, normalizarEncabezado, parsearNumero, parsearCantidad, esConsultar, numeroComoTexto } from './texto.js';

const FILAS_PARA_BUSCAR_ENCABEZADO = 10;
const ETIQUETA = {
  CODIGO: 'código', DESCRIPCION: 'descripción', MARCA: 'marca', VEHICULO: 'vehículo', CANTIDAD: 'cantidad',
  PRECIO: 'precio', REEMPLAZA: 'reemplaza a', EQUIVALENTES: 'equivalentes',
};

function reconocer(encabezados, texto) {
  const n = normalizarEncabezado(texto);
  if (!n) return null;
  for (const [campo, e] of Object.entries(encabezados)) if (e.nombres.includes(n)) return campo;
  for (const [campo, e] of Object.entries(encabezados)) if (e.prefijos.some(p => n.startsWith(p))) return campo;
  return null;
}

function comoTexto(v) {
  if (v == null) return null;
  if (typeof v === 'number') return numeroComoTexto(v);
  if (v instanceof Date) return v.toISOString().slice(0, 10);
  const s = String(v).trim();
  return s === '' ? null : s;
}

const recortar = (s, max) => (s == null ? null : s.length <= max ? s : s.slice(0, max));

/** ProcesadorFilas.lista: cuántos códigos válidos trae "A / B, C". */
function lista(texto, propio) {
  if (texto == null) return [];
  const propioNorm = normalizarCodigo(propio);
  const r = [];
  for (const parte of texto.split(/[\/,;|\n\r]+/)) {
    const c = parte.trim();
    const n = normalizarCodigo(c);
    if (n && n !== propioNorm && c.length <= 80 && r.length < 20) r.push(c);
  }
  return r;
}

/**
 * @param archivo File del input
 * @param encabezados catalogos.encabezados
 * @return {filas, errores} o {errorFatal}
 */
export async function leerExcel(archivo, encabezados) {
  const nombre = archivo.name.toLowerCase();
  if (nombre.endsWith('.xls')) {
    return { errorFatal: 'Los archivos .xls viejos no se pueden leer acá. Abrilo en Excel y guardalo como .xlsx (o cargalo desde la web).' };
  }
  if (!nombre.endsWith('.xlsx')) return { errorFatal: 'Elegí un archivo de Excel (.xlsx).' };
  let datos;
  try {
    datos = await readSheet(archivo);
  } catch (e) {
    return { errorFatal: 'No se pudo leer el archivo. ¿Es un Excel (.xlsx) sin contraseña?' };
  }

  let columnas = null;
  let vistas = 0;
  const filas = [];
  const errores = [];
  for (let i = 0; i < datos.length; i++) {
    const celdas = (datos[i] ?? []).map(comoTexto);
    if (celdas.every(v => v == null)) continue;
    if (!columnas) {
      if (++vistas > FILAS_PARA_BUSCAR_ENCABEZADO) break;
      const encontradas = {};
      celdas.forEach((t, col) => {
        const campo = reconocer(encabezados, t);
        if (campo && !(campo in encontradas)) encontradas[campo] = col;
      });
      if (Object.keys(encontradas).length < 2) continue;
      const faltan = Object.entries(encabezados).filter(([c, e]) => e.obligatorio && !(c in encontradas)).map(([c]) => ETIQUETA[c]);
      if (faltan.length) {
        return { errorFatal: `Falta la columna de ${faltan.join(', ')}. Columnas reconocidas: ${Object.keys(encontradas).map(c => ETIQUETA[c]).join(', ')}.` };
      }
      columnas = encontradas;
      continue;
    }
    const valor = (campo) => (campo in columnas ? celdas[columnas[campo]] ?? null : null);
    const filaExcel = i + 1;
    const codigo = valor('CODIGO');
    if (codigo == null) { errores.push(`Fila ${filaExcel}: falta el código`); continue; }
    const mostrar = (t) => (t == null ? ' (vacía)' : ` (${t})`);

    const cantTexto = valor('CANTIDAD');
    let cantidad = null;
    if (!esConsultar(cantTexto)) {
      cantidad = parsearCantidad(cantTexto);
      if (cantidad == null) {
        errores.push(`Fila ${filaExcel} (${codigo}): cantidad inválida${mostrar(cantTexto)}. Poné un número o "consultar".`);
        continue;
      }
    }
    const precioTexto = valor('PRECIO');
    let precio = null;
    if (precioTexto != null && !esConsultar(precioTexto)) {
      const p = parsearNumero(precioTexto);
      if (p == null || p < 0) {
        errores.push(`Fila ${filaExcel} (${codigo}): precio inválido${mostrar(precioTexto)}. Poné un número, o dejalo vacío si es a consultar.`);
        continue;
      }
      if (p > 0) precio = Math.round(p * 100) / 100;
    }
    const reemplaza = valor('REEMPLAZA');
    const equivalentes = valor('EQUIVALENTES');
    filas.push({
      codigo: recortar(codigo, 80),
      descripcion: recortar(valor('DESCRIPCION'), 300),
      marca: recortar(valor('MARCA'), 80),
      vehiculo: recortar(valor('VEHICULO'), 150),
      cantidad, precio, cantidadTexto: cantTexto, precioTexto,
      reemplaza, equivalentes,
      relaciones: lista(reemplaza, codigo).length + lista(equivalentes, codigo).length,
    });
  }
  if (!columnas) {
    return { errorFatal: 'No encontré la fila de títulos. Tiene que tener al menos las columnas de código y cantidad.' };
  }
  return { filas, errores };
}
