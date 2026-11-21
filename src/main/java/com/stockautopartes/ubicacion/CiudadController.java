package com.stockautopartes.ubicacion;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Opciones del desplegable de ciudades cuando cambia el estado (lo pide HTMX).
 * modo=form   → para la configuración: "Elegí la ciudad", las ciudades y "Otra (escribirla)".
 * modo=filtro → para la búsqueda: "Todas las ciudades" y las ciudades.
 */
@Controller
public class CiudadController {

    public static final String OTRA = "__otra__";

    private final CiudadRepository ciudades;

    public CiudadController(CiudadRepository ciudades) {
        this.ciudades = ciudades;
    }

    @GetMapping("/ciudades")
    public String opciones(@RequestParam(defaultValue = "") String estado,
                           @RequestParam(defaultValue = "form") String modo,
                           Model model) {
        List<String> lista = estado.isBlank() ? List.of() : ciudades.nombresDe(estado);
        model.addAttribute("ciudades", lista);
        model.addAttribute("estadoElegido", !estado.isBlank());
        model.addAttribute("seleccionada", "");
        return "filtro".equals(modo) ? "fragmentos/ciudades :: filtro" : "fragmentos/ciudades :: form";
    }
}
