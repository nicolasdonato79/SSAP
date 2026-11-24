package com.stockautopartes.solicitud;

import com.stockautopartes.usuario.Usuario;
import com.stockautopartes.usuario.UsuarioActual;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.function.Supplier;

/**
 * Pantalla de pedidos y acciones de la negociación (todas por HTMX: cada acción
 * devuelve la tarjeta del pedido actualizada, o la misma tarjeta con el error).
 */
@Controller
public class SolicitudController {

    private final SolicitudService servicio;
    private final SolicitudRepository solicitudes;
    private final UsuarioActual usuarioActual;

    public SolicitudController(SolicitudService servicio, SolicitudRepository solicitudes, UsuarioActual usuarioActual) {
        this.servicio = servicio;
        this.solicitudes = solicitudes;
        this.usuarioActual = usuarioActual;
    }

    /** Pedidos que me hicieron (como vendedor) y que hice (como comprador). */
    @GetMapping("/pedidos")
    public String pedidos(@RequestParam(defaultValue = "1") int recibidos,
                          @RequestParam(defaultValue = "1") int enviados,
                          @RequestParam(defaultValue = "10") int tRecibidos,
                          @RequestParam(defaultValue = "10") int tEnviados, Model model) {
        Usuario yo = usuarioActual.obtener();
        var pagRecibidos = solicitudes.findByVendedor_IdOrderByCreadaEnDesc(yo.getId(),
                com.stockautopartes.web.Paginas.pedirElegido(recibidos, tRecibidos));
        var pagEnviados = solicitudes.findByComprador_IdOrderByCreadaEnDesc(yo.getId(),
                com.stockautopartes.web.Paginas.pedirElegido(enviados, tEnviados));
        model.addAttribute("recibidos", pagRecibidos.getContent());
        model.addAttribute("enviados", pagEnviados.getContent());
        model.addAttribute("pagRecibidos", pagRecibidos);
        model.addAttribute("pagEnviados", pagEnviados);
        return "pedidos";
    }


    /** Botón "Pedir" de los resultados de búsqueda. */
    @PostMapping("/solicitudes")
    public String crear(@RequestParam long itemId, @RequestParam(defaultValue = "1") int cantidad, Model model) {
        try {
            servicio.crear(usuarioActual.obtener(), itemId, cantidad);
            model.addAttribute("mensaje", "Pedido enviado. El vendedor te va a confirmar stock y precio.");
            model.addAttribute("ok", true);
        } catch (SolicitudException e) {
            model.addAttribute("mensaje", e.getMessage());
        }
        return "fragmentos/solicitud :: mensaje";
    }

    // ---------- Vendedor ----------

    @PostMapping("/solicitudes/{id}/ofertar")
    public String ofertar(@PathVariable long id, @RequestParam String precio, Model model) {
        return accion(id, "vendedor", model, () -> servicio.ofertar(usuarioActual.obtener(), id, precio));
    }

    @PostMapping("/solicitudes/{id}/sin-stock")
    public String sinStock(@PathVariable long id, Model model) {
        return accion(id, "vendedor", model, () -> servicio.sinStock(usuarioActual.obtener(), id));
    }

    @PostMapping("/solicitudes/{id}/aceptar-contraoferta")
    public String aceptarContraoferta(@PathVariable long id, Model model) {
        return accion(id, "vendedor", model, () -> servicio.aceptarContraoferta(usuarioActual.obtener(), id));
    }

    @PostMapping("/solicitudes/{id}/rechazar-contraoferta")
    public String rechazarContraoferta(@PathVariable long id, Model model) {
        return accion(id, "vendedor", model, () -> servicio.rechazarContraoferta(usuarioActual.obtener(), id));
    }

    // ---------- Comprador ----------

    @PostMapping("/solicitudes/{id}/aceptar-oferta")
    public String aceptarOferta(@PathVariable long id, Model model) {
        return accion(id, "comprador", model, () -> servicio.aceptarOferta(usuarioActual.obtener(), id));
    }

    @PostMapping("/solicitudes/{id}/contraofertar")
    public String contraofertar(@PathVariable long id, @RequestParam String precio, Model model) {
        return accion(id, "comprador", model, () -> servicio.contraofertar(usuarioActual.obtener(), id, precio));
    }

    @PostMapping("/solicitudes/{id}/cancelar")
    public String cancelar(@PathVariable long id, Model model) {
        return accion(id, "comprador", model, () -> servicio.cancelar(usuarioActual.obtener(), id));
    }

    /** Ejecuta la acción y devuelve la tarjeta; si falla, la misma tarjeta con el mensaje de error. */
    private String accion(long id, String rol, Model model, Supplier<Solicitud> paso) {
        Solicitud s;
        try {
            s = paso.get();
        } catch (SolicitudException e) {
            model.addAttribute("error", e.getMessage());
            s = servicio.buscar(id);
        }
        model.addAttribute("s", s);
        model.addAttribute("rol", rol);
        return "fragmentos/solicitud :: fila";
    }
}
