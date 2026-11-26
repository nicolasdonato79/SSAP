package com.stockautopartes.sync;

import com.stockautopartes.solicitud.EstadoSolicitud;

import java.util.Optional;

/**
 * Cambios que el equipo puede hacer sin conexión y manda después.
 * <pre>
 * ITEM_GUARDAR       alta o cambio de un repuesto. datos: codigo, descripcion, vehiculo, cantidad, precio, marcaId
 *                    (cantidad/precio como texto: "35,50", "consultar" o vacío; marcaId vacío = automática)
 * ITEM_BORRAR        baja de un repuesto
 * STOCK_REEMPLAZAR   carga de Excel. datos: nombreArchivo, filas [{codigo, descripcion, marca, vehiculo, cantidad,
 *                    precio, reemplaza, equivalentes}] como texto, tal cual se leyeron
 * PEDIDO_*           respuesta a un pedido. OFERTAR y CONTRAOFERTAR llevan datos.precio
 * </pre>
 * Los pedidos nuevos no se pueden hacer sin conexión (hay que ver el stock actual del vendedor).
 */
public enum TipoOperacion {
    ITEM_GUARDAR,
    ITEM_BORRAR,
    STOCK_REEMPLAZAR,
    PEDIDO_OFERTAR(EstadoSolicitud.OFERTADA),
    PEDIDO_SIN_STOCK(EstadoSolicitud.RECHAZADA),
    PEDIDO_ACEPTAR_CONTRAOFERTA(EstadoSolicitud.ACEPTADA),
    PEDIDO_RECHAZAR_CONTRAOFERTA(EstadoSolicitud.RECHAZADA),
    PEDIDO_ACEPTAR_OFERTA(EstadoSolicitud.ACEPTADA),
    PEDIDO_CONTRAOFERTAR(EstadoSolicitud.CONTRAOFERTA),
    PEDIDO_CANCELAR(EstadoSolicitud.CANCELADA);

    /** Estado en que queda el pedido si la operación se aplica (null = no es de pedidos). */
    private final EstadoSolicitud destino;

    TipoOperacion() {
        this(null);
    }

    TipoOperacion(EstadoSolicitud destino) {
        this.destino = destino;
    }

    public boolean esDePedido() {
        return destino != null;
    }

    public EstadoSolicitud destino() {
        return destino;
    }

    public static Optional<TipoOperacion> de(String texto) {
        if (texto == null) return Optional.empty();
        try {
            return Optional.of(valueOf(texto.strip()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
