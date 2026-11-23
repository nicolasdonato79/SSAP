package com.stockautopartes.admin;

import com.stockautopartes.marca.Marca;
import com.stockautopartes.marca.MarcaRepository;
import com.stockautopartes.usuario.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Administración (solo ADMIN, ver SecurityConfig):
 * alta de usuarios con sus marcas, editar la configuración de cualquiera, contraseña,
 * activar/desactivar, y agregar marcas al catálogo.
 */
@Controller
@RequestMapping("/admin")
public class AdminUsuariosController {

    private static final String PATRON_USUARIO = "[a-zA-Z0-9._-]{3,50}";

    private final UsuarioRepository usuarios;
    private final MarcaRepository marcas;
    private final PasswordEncoder encoder;
    private final UsuarioActual usuarioActual;
    private final PerfilService perfil;

    public AdminUsuariosController(UsuarioRepository usuarios, MarcaRepository marcas, PasswordEncoder encoder,
                                   UsuarioActual usuarioActual, PerfilService perfil) {
        this.usuarios = usuarios;
        this.marcas = marcas;
        this.encoder = encoder;
        this.usuarioActual = usuarioActual;
        this.perfil = perfil;
    }

    @GetMapping("/usuarios")
    public String lista(Model model) {
        model.addAttribute("usuarios", usuarios.findAllByOrderByUsernameAsc());
        model.addAttribute("gruposMarcas", marcas.agrupadas());
        model.addAttribute("tiposMarca", Marca.Tipo.values());
        return "admin-usuarios";
    }

    @PostMapping("/usuarios")
    @Transactional
    public String crear(@RequestParam String username,
                        @RequestParam String nombre,
                        @RequestParam String password,
                        @RequestParam(defaultValue = "false") boolean admin,
                        @RequestParam(required = false) List<Integer> marcasElegidas,
                        RedirectAttributes flash) {
        String u = username.trim();
        String n = nombre.trim();
        if (!u.matches(PATRON_USUARIO)) {
            flash.addFlashAttribute("error", "El usuario tiene que tener entre 3 y 50 letras, números, punto, guion o guion bajo.");
        } else if (usuarios.existsByUsernameIgnoreCase(u)) {
            flash.addFlashAttribute("error", "Ya existe el usuario " + u + ".");
        } else if (n.length() < 2 || n.length() > 150) {
            flash.addFlashAttribute("error", "Poné un nombre.");
        } else if (password.length() < PerfilService.MIN_PASSWORD) {
            flash.addFlashAttribute("error", "La contraseña tiene que tener al menos " + PerfilService.MIN_PASSWORD + " caracteres.");
        } else {
            Usuario nuevo = new Usuario(u.toLowerCase(), encoder.encode(password), n,
                    admin ? Usuario.Rol.ADMIN : Usuario.Rol.USUARIO);
            if (marcasElegidas != null && !marcasElegidas.isEmpty()) {
                nuevo.reemplazarMarcas(marcas.findByIdInAndActivaTrue(marcasElegidas));
            }
            usuarios.save(nuevo);
            flash.addFlashAttribute("ok", "Usuario " + nuevo.getUsername() + " creado"
                    + (nuevo.getMarcas().isEmpty() ? "." : " con " + nuevo.getMarcas().size() + " marca(s)."));
        }
        return "redirect:/admin/usuarios";
    }

    /** El admin edita la configuración de otro usuario (misma pantalla que ⚙ Configuración). */
    @GetMapping("/usuarios/{id}/configuracion")
    public String configuracion(@PathVariable long id, Model model) {
        Usuario u = usuarios.findById(id).orElseThrow();
        return vistaConfiguracion(model, u, perfil.paraVista(PerfilForm.desde(u)), List.of());
    }

    @PostMapping("/usuarios/{id}/configuracion")
    public String guardarConfiguracion(@PathVariable long id, @ModelAttribute PerfilForm form,
                                       Model model, RedirectAttributes flash) {
        Usuario u = usuarios.findById(id).orElseThrow();
        List<String> errores = perfil.guardar(id, form, false);
        if (!errores.isEmpty()) {
            return vistaConfiguracion(model, u, form, errores);
        }
        flash.addFlashAttribute("ok", "Configuración de " + u.getUsername() + " guardada.");
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/usuarios/{id}/password")
    @Transactional
    public String cambiarPassword(@PathVariable long id, @RequestParam String password, RedirectAttributes flash) {
        Usuario u = usuarios.findById(id).orElseThrow();
        if (password.length() < PerfilService.MIN_PASSWORD) {
            flash.addFlashAttribute("error", "La contraseña tiene que tener al menos " + PerfilService.MIN_PASSWORD + " caracteres.");
        } else {
            u.cambiarPassword(encoder.encode(password));
            flash.addFlashAttribute("ok", "Contraseña de " + u.getUsername() + " cambiada.");
        }
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/usuarios/{id}/activo")
    @Transactional
    public String activar(@PathVariable long id, RedirectAttributes flash) {
        Usuario u = usuarios.findById(id).orElseThrow();
        boolean soyYo = usuarioActual.buscar().map(yo -> yo.getId().equals(u.getId())).orElse(false);
        if (soyYo) {
            flash.addFlashAttribute("error", "No podés desactivarte a vos mismo.");
        } else {
            u.setActivo(!u.isActivo());
            flash.addFlashAttribute("ok", u.getUsername() + (u.isActivo() ? " activado." : " desactivado."));
        }
        return "redirect:/admin/usuarios";
    }

    /** Agregar una marca que falte en el catálogo. */
    @PostMapping("/marcas")
    public String agregarMarca(@RequestParam String nombre, @RequestParam Marca.Tipo tipo, RedirectAttributes flash) {
        String n = nombre.trim();
        if (n.length() < 2 || n.length() > 60) {
            flash.addFlashAttribute("error", "Poné el nombre de la marca.");
        } else if (marcas.existsByNombreIgnoreCaseAndTipo(n, tipo)) {
            flash.addFlashAttribute("error", "La marca " + n + " ya existe.");
        } else {
            marcas.save(new Marca(n, tipo));
            flash.addFlashAttribute("ok", "Marca " + n + " agregada.");
        }
        return "redirect:/admin/usuarios#marcas";
    }

    private String vistaConfiguracion(Model model, Usuario u, PerfilForm form, List<String> errores) {
        model.addAttribute("form", form);
        model.addAttribute("ciudades", perfil.ciudadesDe(form.getEstado()));
        model.addAttribute("seleccionada", form.getCiudad());
        model.addAttribute("estadoElegido", !form.getEstado().isBlank());
        model.addAttribute("telefonoVerificado", u.isTelefonoVerificado());
        model.addAttribute("emailVerificado", u.isEmailVerificado());
        model.addAttribute("errores", errores);
        model.addAttribute("gruposMarcas", marcas.agrupadas());
        model.addAttribute("accion", "/admin/usuarios/" + u.getId() + "/configuracion");
        model.addAttribute("titulo", "Configuración de " + u.getUsername());
        model.addAttribute("username", u.getUsername());
        model.addAttribute("primeraVez", false);
        model.addAttribute("esPropio", false);
        return "registro";
    }
}
