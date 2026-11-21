package com.stockautopartes.notificacion;

import com.stockautopartes.demanda.Demanda;
import com.stockautopartes.solicitud.Solicitud;
import com.stockautopartes.usuario.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementación de desarrollo: solo escribe en el log.
 * TODO: WhatsAppNotificador con WhatsApp Cloud API (plantillas aprobadas por Meta) y email de respaldo.
 */
@Component
public class LogNotificador implements Notificador {

    private static final Logger log = LoggerFactory.getLogger(LogNotificador.class);

    @Override
    public void avisarCoincidencia(Demanda demanda, Usuario vendedor) {
        log.info("[aviso] '{}' pedido por {} ahora lo tiene {}",
                demanda.getTexto(), demanda.getComprador().getNombre(), vendedor.getNombre());
    }

    @Override
    public void nuevaSolicitud(Solicitud s) {
        log.info("[aviso a {}] {} te pidió {} x {}", s.getVendedor().getUsername(),
                s.getComprador().getNombre(), s.getCantidad(), s.getCodigo());
    }

    @Override
    public void cambioEnPedido(Solicitud s, Usuario destinatario, String mensaje) {
        log.info("[aviso a {}] Pedido {} ({}): {}", destinatario.getUsername(), s.getId(), s.getCodigo(), mensaje);
    }
}
