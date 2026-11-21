package com.stockautopartes.web;

import com.stockautopartes.solicitud.SolicitudRepository;
import com.stockautopartes.usuario.Usuario;
import com.stockautopartes.usuario.UsuarioActual;
import com.stockautopartes.usuario.UsuarioNoRegistradoException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Optional;

/** Datos disponibles en todas las vistas, y redirección al registro cuando falta. */
@ControllerAdvice
public class ModeloGlobal {

    private final UsuarioActual usuarioActual;
    private final SolicitudRepository solicitudes;

    public ModeloGlobal(UsuarioActual usuarioActual, SolicitudRepository solicitudes) {
        this.usuarioActual = usuarioActual;
        this.solicitudes = solicitudes;
    }

    @ModelAttribute
    public void comunes(Model model) {
        Optional<Usuario> yo = usuarioActual.buscar();
        model.addAttribute("logueado", usuarioActual.username().isPresent());
        model.addAttribute("esAdmin", yo.map(Usuario::isAdmin).orElse(false));
        model.addAttribute("usuarioId", yo.map(Usuario::getId).orElse(null));
        model.addAttribute("pedidosPendientes", yo
                .map(u -> solicitudes.countParaResponder(u.getId()))
                .orElse(0L));
        model.addAttribute("estados", Estados.TODOS);
    }

    @ExceptionHandler(UsuarioNoRegistradoException.class)
    public String faltaRegistro() {
        return "redirect:/configuracion";
    }
}
