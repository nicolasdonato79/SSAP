package com.stockautopartes.solicitud;

import com.stockautopartes.importacion.NumeroParser;
import com.stockautopartes.inventario.ItemStock;
import com.stockautopartes.inventario.ItemStockRepository;
import com.stockautopartes.notificacion.Notificador;
import com.stockautopartes.usuario.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;

/** Reglas de los pedidos: quién puede hacer qué en cada estado, y a quién se avisa. */
@Service
public class SolicitudService {

    private static final BigDecimal PRECIO_MAXIMO = new BigDecimal("1000000");

    private final SolicitudRepository solicitudes;
    private final ItemStockRepository items;
    private final Notificador notificador;

    public SolicitudService(SolicitudRepository solicitudes, ItemStockRepository items, Notificador notificador) {
        this.solicitudes = solicitudes;
        this.items = items;
        this.notificador = notificador;
    }

    @Transactional
    public Solicitud crear(Usuario comprador, long itemId, int cantidad) {
        ItemStock item = items.findById(itemId)
                .orElseThrow(() -> new SolicitudException("Ese producto ya no está publicado."));
        if (item.getVendedor().getId().equals(comprador.getId())) {
            throw new SolicitudException("Es tu propio producto.");
        }
        if (cantidad < 1 || cantidad > 9999) {
            throw new SolicitudException("Poné una cantidad válida.");
        }
        if (item.getCantidad() != null && cantidad > item.getCantidad()) {
            throw new SolicitudException("Podés pedir entre 1 y " + item.getCantidad() + ".");
        }
        if (solicitudes.existsByComprador_IdAndVendedor_IdAndCodigoAndEstadoIn(
                comprador.getId(), item.getVendedor().getId(), item.getCodigoOriginal(),
                EnumSet.of(EstadoSolicitud.PENDIENTE, EstadoSolicitud.OFERTADA, EstadoSolicitud.CONTRAOFERTA))) {
            throw new SolicitudException("Ya tenés un pedido abierto de este producto con este vendedor.");
        }
        Solicitud s = solicitudes.save(new Solicitud(comprador, item, cantidad));
        notificador.nuevaSolicitud(s);
        return s;
    }

    // ---------- Vendedor ----------

    @Transactional
    public Solicitud ofertar(Usuario vendedor, long id, String precioTexto) {
        Solicitud s = delVendedor(vendedor, id);
        s.ofertar(leerPrecio(precioTexto));
        notificador.cambioEnPedido(s, s.getComprador(), "el vendedor tiene stock y te pasó el precio");
        return s;
    }

    @Transactional
    public Solicitud sinStock(Usuario vendedor, long id) {
        Solicitud s = delVendedor(vendedor, id);
        s.sinStock();
        notificador.cambioEnPedido(s, s.getComprador(), "el vendedor no lo tiene");
        return s;
    }

    @Transactional
    public Solicitud aceptarContraoferta(Usuario vendedor, long id) {
        Solicitud s = delVendedor(vendedor, id);
        s.aceptarContraoferta();
        notificador.cambioEnPedido(s, s.getComprador(), "el vendedor aceptó tu contraoferta");
        return s;
    }

    @Transactional
    public Solicitud rechazarContraoferta(Usuario vendedor, long id) {
        Solicitud s = delVendedor(vendedor, id);
        s.rechazarContraoferta();
        notificador.cambioEnPedido(s, s.getComprador(), "el vendedor rechazó tu contraoferta");
        return s;
    }

    // ---------- Comprador ----------

    @Transactional
    public Solicitud aceptarOferta(Usuario comprador, long id) {
        Solicitud s = delComprador(comprador, id);
        s.aceptarOferta();
        notificador.cambioEnPedido(s, s.getVendedor(), "el comprador aceptó tu precio");
        return s;
    }

    @Transactional
    public Solicitud contraofertar(Usuario comprador, long id, String precioTexto) {
        Solicitud s = delComprador(comprador, id);
        BigDecimal precio = leerPrecio(precioTexto);
        if (s.getPrecioOferta() != null && precio.compareTo(s.getPrecioOferta()) == 0) {
            throw new SolicitudException("Es el mismo precio que te ofrecieron: aceptalo directamente.");
        }
        s.contraofertar(precio);
        notificador.cambioEnPedido(s, s.getVendedor(), "el comprador te hizo una contraoferta");
        return s;
    }

    @Transactional
    public Solicitud cancelar(Usuario comprador, long id) {
        Solicitud s = delComprador(comprador, id);
        s.cancelar();
        notificador.cambioEnPedido(s, s.getVendedor(), "el comprador canceló el pedido");
        return s;
    }

    // ---------- Apoyo ----------

    public Solicitud buscar(long id) {
        return solicitudes.findById(id).orElseThrow(() -> new SolicitudException("El pedido no existe."));
    }

    private Solicitud delVendedor(Usuario vendedor, long id) {
        Solicitud s = buscar(id);
        if (!s.getVendedor().getId().equals(vendedor.getId())) {
            throw new SolicitudException("Ese pedido no es para vos.");
        }
        return s;
    }

    private Solicitud delComprador(Usuario comprador, long id) {
        Solicitud s = buscar(id);
        if (!s.getComprador().getId().equals(comprador.getId())) {
            throw new SolicitudException("Ese pedido no es tuyo.");
        }
        return s;
    }

    /** Acepta "1.250,50", "35,5", "$ 40". */
    static BigDecimal leerPrecio(String texto) {
        BigDecimal p = NumeroParser.parsear(texto)
                .orElseThrow(() -> new SolicitudException("Poné el precio en dólares, por ejemplo 35,50."));
        if (p.signum() <= 0 || p.compareTo(PRECIO_MAXIMO) > 0) {
            throw new SolicitudException("Poné un precio mayor a 0.");
        }
        return p.setScale(2, RoundingMode.HALF_UP);
    }
}
