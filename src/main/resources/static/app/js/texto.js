// Mismas reglas que el servidor, para que lo que se ve sin conexión sea igual a lo que va a quedar.
// Si se cambia algo acá, cambiarlo también en Java (indicado en cada función).

/** CodigoNormalizer.normalizar: "04465-02220" = "0446502220". */
export function normalizarCodigo(codigo) {
  if (codigo == null) return '';
  return String(codigo).normalize('NFD').replace(/\p{M}/gu, '').toUpperCase().replace(/[^A-Z0-9]/g, '');
}

/** texto_para_buscar (V13): minúsculas y sin acentos. */
export function paraBuscar(t) {
  return String(t ?? '').toLowerCase().normalize('NFD').replace(/\p{M}/gu, '');
}

/** Campo.normalizar: encabezado sin acentos, mayúsculas, espacios ni símbolos ("Precio $" → "precio$"). */
export function normalizarEncabezado(s) {
  if (s == null) return '';
  return String(s).normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/[^a-z0-9$]/g, '');
}

/**
 * NumeroParser.parsear: "35,50", "1.234,56", "1,234.56", "$ 40". Un único separador seguido de exactamente
 * 3 dígitos ("1.500") es de miles, como se escribe en Venezuela. Devuelve un número o null.
 */
export function parsearNumero(texto) {
  if (texto == null) return null;
  const s = String(texto).replace(/[^0-9,.\-]/g, '');
  if (s === '' || s === '-') return null;
  const ultimaComa = s.lastIndexOf(',');
  const ultimoPunto = s.lastIndexOf('.');
  let normal;
  if (ultimaComa >= 0 && ultimoPunto >= 0) {
    const decimal = ultimaComa > ultimoPunto ? ',' : '.';
    const miles = decimal === ',' ? '.' : ',';
    normal = s.split(miles).join('').replace(decimal, '.');
  } else if (ultimaComa >= 0 || ultimoPunto >= 0) {
    const sep = ultimaComa >= 0 ? ',' : '.';
    const cantidad = s.split(sep).length - 1;
    const digitosDespues = s.length - s.lastIndexOf(sep) - 1;
    normal = (cantidad > 1 || digitosDespues === 3) ? s.split(sep).join('') : s.replace(sep, '.');
  } else {
    normal = s;
  }
  if (!/^-?\d+(\.\d+)?$/.test(normal)) return null;
  return Number(normal);
}

/** NumeroParser.parsearCantidad: entero no negativo o null. */
export function parsearCantidad(texto) {
  const n = parsearNumero(texto);
  return n != null && Number.isInteger(n) && n >= 0 ? n : null;
}

/** ProcesadorFilas.esConsultar: "consultar", "a consultar", "consulte"... */
export function esConsultar(texto) {
  if (texto == null) return false;
  const n = normalizarEncabezado(texto);
  return n.startsWith('consult') || n.startsWith('aconsult');
}

/** Un número de una celda de Excel como texto que el servidor lea igual (sin ambigüedad de miles). */
export function numeroComoTexto(n) {
  if (Number.isInteger(n)) return String(n);
  return n.toFixed(2).replace('.', ',');
}

export function precioEnTexto(p) {
  if (p == null) return '';
  return Number(p).toFixed(2).replace('.', ',');
}

/** "$ 1.250,50" para mostrar. */
export function mostrarPrecio(p) {
  if (p == null) return 'Precio a consultar';
  return '$ ' + Number(p).toLocaleString('es-VE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function mostrarCantidad(c) {
  if (c == null) return 'Disponibilidad a consultar';
  return c + (c === 1 ? ' disponible' : ' disponibles');
}

/** "hace 2 min", "hace 3 h", "hace 2 días". */
export function hace(fecha) {
  if (!fecha) return 'nunca';
  const seg = Math.max(0, (Date.now() - new Date(fecha).getTime()) / 1000);
  if (seg < 60) return 'recién';
  if (seg < 3600) return `hace ${Math.round(seg / 60)} min`;
  if (seg < 86400) return `hace ${Math.round(seg / 3600)} h`;
  const d = Math.round(seg / 86400);
  return `hace ${d} ${d === 1 ? 'día' : 'días'}`;
}

/** UUID v7 (empieza con la fecha: los índices andan mejor que con v4). */
export function uuid7() {
  const b = crypto.getRandomValues(new Uint8Array(16));
  const ms = BigInt(Date.now());
  for (let i = 0; i < 6; i++) b[i] = Number((ms >> BigInt(8 * (5 - i))) & 0xffn);
  b[6] = (b[6] & 0x0f) | 0x70;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = [...b].map(x => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

/** Usuario.getLinkWhatsapp: el teléfono ya está guardado con código de país. */
export function linkWhatsapp(tel) {
  return tel ? 'https://wa.me/' + String(tel).replace(/\D/g, '') : null;
}
