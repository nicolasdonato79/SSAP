// Igual que DetectorMarca.java: a qué marca de vehículo pertenece un repuesto.
// 1) marca o modelo en "vehículo"  2) la columna marca  3) si el vendedor trabaja una sola, esa
// 4) marca o modelo en la descripción, si es una de las del vendedor.

const ALIAS = {
  chevy: ['Chevrolet'], vw: ['Volkswagen'], mercedes: ['Mercedes-Benz'], benz: ['Mercedes-Benz'],
  motorcraft: ['Ford'], acdelco: ['Chevrolet'], mopar: ['Chrysler', 'Jeep', 'Dodge', 'RAM'],
  toyotagenuine: ['Toyota'],
};
const MAX_PALABRAS = 3;

function palabras(texto) {
  const s = String(texto).normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
  return s ? s.split(' ') : [];
}
const compactar = (n) => palabras(n).join('');

function agregar(mapa, clave, id) {
  if (!clave) return;
  const ids = mapa.get(clave) ?? [];
  if (!ids.includes(id)) ids.push(id);
  mapa.set(clave, ids);
}

/** catalogos: lo que devuelve /api/sync/catalogos. */
export function crearDetector(catalogos) {
  const marcas = new Map();
  const modelos = new Map();
  const porNombre = new Map();
  // autos primero, como en el servidor (ORDER BY tipo, id: AUTO < CAMION < MOTO)
  const activas = catalogos.marcas.filter(m => m.activa)
    .sort((a, b) => a.tipo.localeCompare(b.tipo) || a.id - b.id);
  for (const m of activas) {
    porNombre.set(m.nombre, [...(porNombre.get(m.nombre) ?? []), m.id]);
    for (const parte of m.nombre.split('/')) agregar(marcas, compactar(parte), m.id);
  }
  for (const [alias, nombres] of Object.entries(ALIAS)) {
    for (const n of nombres) for (const id of porNombre.get(n) ?? []) agregar(marcas, alias, id);
  }
  for (const m of catalogos.modelos) {
    const clave = compactar(m.nombre);
    if (clave.length >= 3) agregar(modelos, clave, m.marcaId);
  }

  const elegir = (ids, delVendedor) => ids.find(id => delVendedor.has(id)) ?? ids[0];

  function buscar(pals, mapa, delVendedor, ambiguoEsNulo) {
    const encontrados = new Set();
    for (let i = 0; i < pals.length; i++) {
      for (let n = Math.min(MAX_PALABRAS, pals.length - i); n >= 1; n--) {
        const ids = mapa.get(pals.slice(i, i + n).join(''));
        if (ids) {
          if (!ambiguoEsNulo) return elegir(ids, delVendedor);
          ids.forEach(id => encontrados.add(id));
          i += n - 1;
          break;
        }
      }
    }
    if (!encontrados.size) return null;
    for (const id of encontrados) if (delVendedor.has(id)) return id;
    return encontrados.size === 1 ? [...encontrados][0] : null;
  }

  function enTexto(texto, delVendedor, conModelos) {
    const p = palabras(texto);
    let r = buscar(p, marcas, delVendedor, false);
    if (r == null && conModelos) r = buscar(p, modelos, delVendedor, true);
    return r;
  }

  const hay = (s) => s != null && String(s).trim() !== '';

  return function detectar(vehiculo, marcaRepuesto, descripcion, marcasVendedor) {
    const delVendedor = new Set(marcasVendedor ?? []);
    let r;
    if (hay(vehiculo) && (r = enTexto(vehiculo, delVendedor, true)) != null) return r;
    if (hay(marcaRepuesto) && (r = enTexto(marcaRepuesto, delVendedor, false)) != null) return r;
    if (delVendedor.size === 1) return [...delVendedor][0];
    if (hay(descripcion) && (r = enTexto(descripcion, delVendedor, true)) != null) {
      return !delVendedor.size || delVendedor.has(r) ? r : null;
    }
    return null;
  };
}
