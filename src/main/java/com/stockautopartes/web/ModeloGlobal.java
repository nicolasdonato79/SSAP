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

    private final boolean registroAbierto;
    private final String version;

    public ModeloGlobal(UsuarioActual usuarioActual, SolicitudRepository solicitudes,
                        @org.springframework.beans.factory.annotation.Value("${app.registro.habilitado}") boolean registroAbierto,
                        @org.springframework.beans.factory.annotation.Value("${app.version:}") String version) {
        this.usuarioActual = usuarioActual;
        this.solicitudes = solicitudes;
        this.registroAbierto = registroAbierto;
        // Si se corre sin pasar por Maven queda "@project.version@" sin reemplazar: en ese caso no se muestra.
        this.version = version.isBlank() || version.startsWith("@") ? null : "V." + version;
    }

    @ModelAttribute
    public void comunes(Model model) {
        Optional<Usuario> yo = usuarioActual.buscar();
        model.addAttribute("logueado", usuarioActual.username().isPresent());
        model.addAttribute("esAdmin", yo.map(Usuario::isAdmin).orElse(false));
        model.addAttribute("usuarioId", yo.map(Usuario::getId).orElse(null));
        // Para el label del encabezado (issue #2)
        model.addAttribute("yoUsername", usuarioActual.username().orElse(null));
        model.addAttribute("yoNombre", yo.map(Usuario::getNombre).orElse(null));
        model.addAttribute("pedidosPendientes", yo
                .map(u -> solicitudes.countParaResponder(u.getId()))
                .orElse(0L));
        model.addAttribute("estados", Estados.TODOS);
        model.addAttribute("registroAbierto", registroAbierto);
        model.addAttribute("versionApp", version);
    }

    @ExceptionHandler(UsuarioNoRegistradoException.class)
    public String faltaRegistro() {
        return "redirect:/configuracion";
    }
}
