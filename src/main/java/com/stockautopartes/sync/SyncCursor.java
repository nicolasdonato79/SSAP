package com.stockautopartes.sync;

import java.util.UUID;

/**
 * Hasta dónde bajó un equipo. Para el equipo es un texto opaco que devuelve tal cual en el próximo pull.
 * <ul>
 *   <li>"" o null: nunca bajó nada (baja todo)</li>
 *   <li>"1234": bajó todo lo de las transacciones anteriores a la 1234</li>
 *   <li>"1234.a_item.&lt;uuid&gt;": está a mitad de una bajada en páginas; sigue después de esa fila</li>
 * </ul>
 */
record SyncCursor(long xid, String entidad, UUID uuid) {

    static final SyncCursor INICIO = new SyncCursor(0, null, null);

    static SyncCursor leer(String texto) {
        if (texto == null || texto.isBlank()) return INICIO;
        String[] partes = texto.strip().split("\\.", 3);
        try {
            long xid = Long.parseLong(partes[0]);
            if (xid < 0) throw new IllegalArgumentException("Cursor inválido");
            if (partes.length == 1) return new SyncCursor(xid, null, null);
            if (partes.length != 3) throw new IllegalArgumentException("Cursor inválido");
            return new SyncCursor(xid, partes[1], UUID.fromString(partes[2]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cursor inválido");
        }
    }

    boolean aMitad() {
        return entidad != null;
    }

    @Override
    public String toString() {
        return aMitad() ? xid + "." + entidad + "." + uuid : Long.toString(xid);
    }
}
