package com.stockautopartes.inventario;

import com.stockautopartes.importacion.ImportacionRepository;
import com.stockautopartes.usuario.Usuario;
import com.stockautopartes.usuario.UsuarioActual;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Pantalla del vendedor: subir el Excel, ver la última carga y lo que tiene publicado. */
@Controller
public class MiStockController {

    private final UsuarioActual usuarioActual;
    private final ItemStockRepository items;
    private final ImportacionRepository importaciones;
    private final int diasNoConfirmado;

    public MiStockController(UsuarioActual usuarioActual,
                             ItemStockRepository items,
                             ImportacionRepository importaciones,
                             @Value("${app.stock.dias-para-no-confirmado}") int diasNoConfirmado) {
        this.usuarioActual = usuarioActual;
        this.items = items;
        this.importaciones = importaciones;
        this.diasNoConfirmado = diasNoConfirmado;
    }

    @GetMapping("/mi-stock")
    public String miStock(Model model) {
        Usuario yo = usuarioActual.obtener();
        model.addAttribute("usuario", yo);
        model.addAttribute("total", items.countByVendedor_Id(yo.getId()));
        model.addAttribute("items", items.findTop300ByVendedor_IdOrderByCodigoOriginalAsc(yo.getId()));
        model.addAttribute("ultima", importaciones.findFirstByUsuarioIdOrderByCreadoEnDesc(yo.getId()).orElse(null));
        model.addAttribute("diasNoConfirmado", diasNoConfirmado);
        return "mi-stock";
    }
}
