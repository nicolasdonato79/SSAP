package com.stockautopartes.usuario;

import com.stockautopartes.marca.MarcaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

/**
 * ⚙ Configuración del propio usuario: datos de contacto, ubicación, marcas que trabaja,
 * horarios de atención y contraseña. Vista: templates/registro.html (compartida con el admin).
 * Teléfono, estado y ciudad son obligatorios antes de cargar stock o hacer pedidos.
 */
@Controller
public class RegistroController {

    private final UsuarioActual usuarioActual;
    private final PerfilService perfil;
    private final MarcaRepository marcas;

    public RegistroController(UsuarioActual usuarioActual, PerfilService perfil, MarcaRepository marcas) {
        this.usuarioActual = usuarioActual;
        this.perfil = perfil;
        this.marcas = marcas;
    }

    @GetMapping("/perfil")
    public String rutaVieja() {
        return "redirect:/configuracion";
    }

    @GetMapping("/configuracion")
    public String formulario(Model model) {
        Usuario yo = usuarioActual.buscar().orElseThrow();
        return vista(model, yo, perfil.paraVista(PerfilForm.desde(yo)), List.of());
    }

    @PostMapping("/configuracion")
    public String guardar(@ModelAttribute PerfilForm form, Model model, RedirectAttributes flash) {
        Usuario yo = usuarioActual.buscar().orElseThrow();
        boolean eraPrimeraVez = !yo.isPerfilCompleto();
        List<String> errores = perfil.guardar(yo.getId(), form, true);
        if (!errores.isEmpty()) {
            return vista(model, yo, form, errores);
        }
        if (eraPrimeraVez) {
            return "redirect:/";
        }
        flash.addFlashAttribute("ok", "Cambios guardados.");
        return "redirect:/configuracion";
    }

    @PostMapping("/configuracion/password")
    public String cambiarPassword(@RequestParam String actual,
                                  @RequestParam String nueva,
                                  @RequestParam String repetida,
                                  RedirectAttributes flash) {
        Usuario yo = usuarioActual.buscar().orElseThrow();
        Optional<String> error = perfil.cambiarPassword(yo.getId(), actual, nueva, repetida);
        flash.addFlashAttribute(error.isPresent() ? "errorPassword" : "ok", error.orElse("Contraseña cambiada."));
        return "redirect:/configuracion";
    }

    private String vista(Model model, Usuario yo, PerfilForm form, List<String> errores) {
        model.addAttribute("form", form);
        model.addAttribute("ciudades", perfil.ciudadesDe(form.getEstado()));
        model.addAttribute("seleccionada", form.getCiudad());
        model.addAttribute("estadoElegido", !form.getEstado().isBlank());
        model.addAttribute("telefonoVerificado", yo.isTelefonoVerificado());
        model.addAttribute("emailVerificado", yo.isEmailVerificado());
        model.addAttribute("errores", errores);
        model.addAttribute("gruposMarcas", marcas.agrupadas());
        model.addAttribute("accion", "/configuracion");
        model.addAttribute("titulo", "Configuración");
        model.addAttribute("username", yo.getUsername());
        model.addAttribute("primeraVez", !yo.isPerfilCompleto());
        model.addAttribute("esPropio", true);
        return "registro";
    }
}
