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
        publicado(yo, 1, com.stockautopartes.web.Paginas.TAMANIO_POR_DEFECTO, model);
        model.addAttribute("ultima", importaciones.findFirstByUsuarioIdOrderByCreadoEnDesc(yo.getId()).orElse(null));
        model.addAttribute("diasNoConfirmado", diasNoConfirmado);
        return "mi-stock";
    }

    /** Solo la grilla de lo publicado (y el total de arriba): se pide al terminar una carga. */
    @GetMapping("/mi-stock/listado")
    public String listado(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int pagina,
                          @org.springframework.web.bind.annotation.RequestParam(defaultValue = "10") int tamanio,
                          Model model) {
        publicado(usuarioActual.obtener(), pagina, tamanio, model);
        model.addAttribute("oob", true);
        return "mi-stock :: publicado";
    }

    private void publicado(Usuario yo, int pagina, int tamanio, Model model) {
        org.springframework.data.domain.Page<ItemStock> pag = items.findByVendedor_IdOrderByCodigoOriginalAsc(
                yo.getId(), com.stockautopartes.web.Paginas.pedirElegido(pagina, tamanio));
        if (pag.isEmpty() && pag.getTotalPages() > 0) { // pidió una página que ya no existe (ej. después de una carga más chica)
            pag = items.findByVendedor_IdOrderByCodigoOriginalAsc(yo.getId(),
                    com.stockautopartes.web.Paginas.pedir(pag.getTotalPages(), pag.getSize()));
        }
        model.addAttribute("total", pag.getTotalElements());
        model.addAttribute("items", pag.getContent());
        model.addAttribute("pag", pag);
    }
}
