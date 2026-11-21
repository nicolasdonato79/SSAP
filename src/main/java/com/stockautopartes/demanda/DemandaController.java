package com.stockautopartes.demanda;

import com.stockautopartes.usuario.UsuarioActual;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DemandaController {

    private final DemandaRepository demandas;
    private final UsuarioActual usuarioActual;

    public DemandaController(DemandaRepository demandas, UsuarioActual usuarioActual) {
        this.demandas = demandas;
        this.usuarioActual = usuarioActual;
    }

    @PostMapping("/demandas")
    public String crear(@RequestParam String texto, Model model) {
        String limpio = texto.trim();
        if (limpio.length() >= 3) {
            demandas.save(new Demanda(usuarioActual.obtener(), limpio.length() > 300 ? limpio.substring(0, 300) : limpio));
        }
        model.addAttribute("texto", limpio);
        return "fragmentos/resultados :: demandaCreada";
    }
}
