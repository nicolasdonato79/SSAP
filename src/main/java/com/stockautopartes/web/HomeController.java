package com.stockautopartes.web;

import com.stockautopartes.config.LoginExitosoHandler;
import com.stockautopartes.marca.MarcaRepository;
import com.stockautopartes.usuario.Usuario;
import com.stockautopartes.usuario.UsuarioActual;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;

@Controller
public class HomeController {

    private final MarcaRepository marcas;
    private final UsuarioActual usuarioActual;

    public HomeController(MarcaRepository marcas, UsuarioActual usuarioActual) {
        this.marcas = marcas;
        this.usuarioActual = usuarioActual;
    }

    @GetMapping("/")
    public String inicio(Model model, HttpSession session) {
        model.addAttribute("gruposMarcas", marcas.agrupadas());

        // Aviso de datos faltantes: una sola vez, justo después del login (lo marca LoginExitosoHandler)
        if (Boolean.TRUE.equals(session.getAttribute(LoginExitosoHandler.AVISO_PERFIL))) {
            session.removeAttribute(LoginExitosoHandler.AVISO_PERFIL);
            usuarioActual.buscar().filter(u -> !u.isPerfilCompleto()).ifPresent(u -> {
                model.addAttribute("avisoPerfil", true);
                model.addAttribute("datosFaltantes", faltantes(u));
            });
        }
        return "index";
    }

    private static List<String> faltantes(Usuario u) {
        List<String> lista = new ArrayList<>();
        if (u.getTelefono() == null) lista.add("tu teléfono con WhatsApp");
        if (u.getEstado() == null) lista.add("el estado");
        if (u.getCiudad() == null) lista.add("la ciudad o pueblo");
        return lista;
    }
}
