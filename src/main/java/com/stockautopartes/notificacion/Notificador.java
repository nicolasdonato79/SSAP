package com.stockautopartes.notificacion;

import com.stockautopartes.demanda.Demanda;
import com.stockautopartes.solicitud.Solicitud;
import com.stockautopartes.usuario.Usuario;

public interface Notificador {

    /** Un vendedor cargó algo que un comprador había pedido. */
    void avisarCoincidencia(Demanda demanda, Usuario vendedor);

    /** Al vendedor: le hicieron un pedido. */
    void nuevaSolicitud(Solicitud solicitud);

    /** A quien le toca responder o enterarse de un cambio en un pedido (oferta, contraoferta, cierre). */
    void cambioEnPedido(Solicitud solicitud, Usuario destinatario, String mensaje);
}
