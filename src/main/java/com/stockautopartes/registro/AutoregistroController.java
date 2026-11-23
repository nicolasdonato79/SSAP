package com.stockautopartes.registro;

import com.stockautopartes.usuario.Usuario;
import com.stockautopartes.usuario.UsuarioActual;
import com.stockautopartes.usuario.UsuarioRepository;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * Pantallas públicas del alta automática y de "Olvidé mi contraseña".
 * El registro en curso se sigue con el id guardado en la sesión.
 */
@Controller
public class AutoregistroController {

    private static final Logger log = LoggerFactory.getLogger(AutoregistroController.class);
    static final String EN_CURSO = "registro.usuarioId";

    private final AutoregistroService registro;
    private final UsuarioRepository usuarios;
    private final UsuarioActual usuarioActual;
    private final boolean abierto;

    public AutoregistroController(AutoregistroService registro, UsuarioRepository usuarios, UsuarioActual usuarioActual,
                                  @Value("${app.registro.habilitado}") boolean abierto) {
        this.registro = registro;
        this.usuarios = usuarios;
        this.usuarioActual = usuarioActual;
        this.abierto = abierto;
    }

    // ---------- 1. Crear cuenta ----------

    @GetMapping("/registro")
    public String formulario(Model model) {
        if (usuarioActual.username().isPresent()) return "redirect:/";
        if (!abierto) return "alta-cerrada";
        model.addAttribute("form", new RegistroForm());
        model.addAttribute("minPassword", AutoregistroService.MIN_PASSWORD);
        model.addAttribute("conTelefono", registro.exigeTelefono());
        return "alta";
    }

    @PostMapping("/registro")
    public String registrar(@ModelAttribute("form") RegistroForm form, Model model,
                            HttpSession session, RedirectAttributes flash) {
        if (!abierto) return "alta-cerrada";
        try {
            AutoregistroService.Paso paso = registro.registrar(form);
            session.setAttribute(EN_CURSO, paso.usuario().getId());
            if (!registro.exigeTelefono()) {
                flash.addFlashAttribute("linkPrueba", paso.paraMostrar());
                return "redirect:/registro/email";
            }
            flash.addFlashAttribute("codigoPrueba", paso.paraMostrar());
            return "redirect:/registro/telefono";
        } catch (RegistroException e) {
            form.setPassword("");
            form.setRepetida("");
            model.addAttribute("errores", e.getErrores());
            model.addAttribute("minPassword", AutoregistroService.MIN_PASSWORD);
            model.addAttribute("conTelefono", registro.exigeTelefono());
            return "alta";
        }
    }

    // ---------- 2. Código por SMS ----------

    @GetMapping("/registro/telefono")
    public String telefono(HttpSession session, Model model) {
        Optional<Usuario> u = enCurso(session);
        if (u.isEmpty()) return "redirect:/registro";
        if (!registro.exigeTelefono() || u.get().isTelefonoVerificado()) return "redirect:/registro/email";
        model.addAttribute("telefono", u.get().getTelefono());
        model.addAttribute("conTelefono", true);
        return "alta-telefono";
    }

    @PostMapping("/registro/telefono")
    public String verificarTelefono(@RequestParam(defaultValue = "") String codigo, HttpSession session,
                                    Model model, RedirectAttributes flash) {
        Optional<Usuario> u = enCurso(session);
        if (u.isEmpty()) return "redirect:/registro";
        try {
            registro.verificarTelefono(u.get().getId(), codigo);
        } catch (RegistroException e) {
            model.addAttribute("telefono", u.get().getTelefono());
            model.addAttribute("conTelefono", true);
            model.addAttribute("error", e.getMessage());
            return "alta-telefono";
        }
        // Teléfono verificado: ahora el link de activación por email
        try {
            flash.addFlashAttribute("linkPrueba", registro.reenviarLinkActivacion(u.get().getId()).paraMostrar());
        } catch (RegistroException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/registro/email";
    }

    @PostMapping("/registro/telefono/reenviar")
    public String reenviarCodigo(HttpSession session, RedirectAttributes flash) {
        Optional<Usuario> u = enCurso(session);
        if (u.isEmpty()) return "redirect:/registro";
        try {
            flash.addFlashAttribute("codigoPrueba", registro.reenviarCodigo(u.get().getId()).paraMostrar());
            flash.addFlashAttribute("ok", "Te mandamos un código nuevo.");
        } catch (RegistroException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/registro/telefono";
    }

    // ---------- 3. Link por email ----------

    @GetMapping("/registro/email")
    public String email(HttpSession session, Model model) {
        Optional<Usuario> u = enCurso(session);
        if (u.isEmpty()) return "redirect:/registro";
        if (registro.exigeTelefono() && !u.get().isTelefonoVerificado()) return "redirect:/registro/telefono";
        model.addAttribute("email", u.get().getEmail());
        model.addAttribute("conTelefono", registro.exigeTelefono());
        return "alta-email";
    }

    @PostMapping("/registro/email/reenviar")
    public String reenviarEmail(HttpSession session, RedirectAttributes flash) {
        Optional<Usuario> u = enCurso(session);
        if (u.isEmpty()) return "redirect:/registro";
        try {
            flash.addFlashAttribute("linkPrueba", registro.reenviarLinkActivacion(u.get().getId()).paraMostrar());
            flash.addFlashAttribute("ok", "Te mandamos el link de nuevo.");
        } catch (RegistroException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/registro/email";
    }

    /** El link del email. Funciona aunque se abra en otro navegador o en el celular. */
    @GetMapping("/registro/confirmar")
    public String confirmar(@RequestParam(defaultValue = "") String token, HttpSession session, Model model) {
        Optional<Usuario> u = registro.confirmarEmail(token);
        if (u.isEmpty()) {
            return resultado(model, "El link no sirve",
                    "Venció o ya se usó. Si todavía no activaste la cuenta, volvé a registrarte con los mismos datos.",
                    "/registro", "Crear cuenta");
        }
        if (u.get().isPendienteVerificacion()) {
            return resultado(model, "Falta un paso", "Email verificado. Falta verificar el teléfono con el código por SMS.",
                    "/registro/telefono", "Verificar teléfono");
        }
        session.removeAttribute(EN_CURSO);
        return resultado(model, "¡Cuenta activada!",
                "Ya podés entrar con tu usuario " + u.get().getUsername()
                        + ". La primera vez te vamos a pedir tu ubicación y las marcas que trabajás.",
                "/login", "Entrar");
    }

    // ---------- Olvidé mi contraseña ----------

    @GetMapping("/recuperar")
    public String recuperar() {
        return "recuperar";
    }

    @PostMapping("/recuperar")
    public String pedirRecuperacion(@RequestParam(defaultValue = "") String dato, Model model) {
        try {
            registro.pedirRecuperacion(dato).ifPresent(link -> model.addAttribute("linkPrueba", link));
        } catch (RegistroException e) {
            log.warn("No se pudo mandar el email de recuperación: {}", e.getMessage());
        }
        // Siempre el mismo mensaje, exista o no la cuenta
        model.addAttribute("enviado", true);
        return "recuperar";
    }

    @GetMapping("/recuperar/nueva")
    public String nueva(@RequestParam(defaultValue = "") String token, Model model) {
        if (!registro.linkDeRecuperacionValido(token)) {
            return resultado(model, "El link no sirve", "Venció o ya se usó. Pedí uno nuevo.",
                    "/recuperar", "Pedir otro link");
        }
        model.addAttribute("token", token);
        model.addAttribute("minPassword", AutoregistroService.MIN_PASSWORD);
        return "recuperar-nueva";
    }

    @PostMapping("/recuperar/nueva")
    public String guardarNueva(@RequestParam String token, @RequestParam String nueva,
                               @RequestParam String repetida, Model model) {
        Optional<String> error = registro.cambiarPassword(token, nueva, repetida);
        if (error.isPresent()) {
            model.addAttribute("token", token);
            model.addAttribute("error", error.get());
            model.addAttribute("minPassword", AutoregistroService.MIN_PASSWORD);
            return "recuperar-nueva";
        }
        return "redirect:/login?clave";
    }

    // ---------- Apoyo ----------

    private Optional<Usuario> enCurso(HttpSession session) {
        Object id = session.getAttribute(EN_CURSO);
        if (!(id instanceof Long l)) return Optional.empty();
        return usuarios.findById(l).filter(Usuario::isPendienteVerificacion);
    }

    private static String resultado(Model model, String titulo, String texto, String url, String boton) {
        model.addAttribute("titulo", titulo);
        model.addAttribute("texto", texto);
        model.addAttribute("url", url);
        model.addAttribute("boton", boton);
        return "alta-resultado";
    }
}
