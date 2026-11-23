package com.stockautopartes.inventario;

import com.stockautopartes.ubicacion.CiudadRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;

@Controller
public class BusquedaController {

    private final ItemStockRepository items;
    private final EquivalenciaService equivalencias;
    private final CiudadRepository ciudades;
    private final int diasNoConfirmado;

    public BusquedaController(ItemStockRepository items,
                              EquivalenciaService equivalencias,
                              CiudadRepository ciudades,
                              @Value("${app.stock.dias-para-no-confirmado}") int diasNoConfirmado) {
        this.items = items;
        this.equivalencias = equivalencias;
        this.ciudades = ciudades;
        this.diasNoConfirmado = diasNoConfirmado;
    }

    /** Devuelve solo el fragmento de resultados (lo pide HTMX mientras se escribe). */
    @GetMapping("/buscar")
    public String buscar(@RequestParam(defaultValue = "") String q,
                         @RequestParam(required = false) String estado,
                         @RequestParam(required = false) String ciudad,
                         @RequestParam(required = false) Integer marca,
                         @RequestParam(defaultValue = "") String modelo,
                         @RequestParam(defaultValue = "1") int pagina,
                         @RequestParam(defaultValue = "10") int tamanio,
                         Model model) {
        String texto = q.trim();
        String modeloFiltro = modelo.trim().length() >= 2 ? modelo.trim() : null;
        String codigo = CodigoNormalizer.normalizar(texto);
        List<ItemStock> resultados = List.of();
        Set<String> codigos = Set.of();
        // Se busca con 3 letras o más, o solo con el modelo (ej. todo lo de "Ranger" en Ford)
        boolean busco = texto.length() >= 3 || modeloFiltro != null;
        if (busco) {
            // El buscado + todo lo que lo reemplaza o es equivalente
            codigos = texto.length() >= 3 ? equivalencias.codigosQueSirvenPara(codigo) : Set.of();
            if (codigos.isEmpty()) {
                codigos = Set.of(codigo);
            }
            String estadoFiltro = (estado == null || estado.isBlank()) ? null : estado;
            // La ciudad solo filtra si es del estado elegido (al cambiar de estado puede llegar la anterior)
            String ciudadFiltro = (estadoFiltro == null || ciudad == null || ciudad.isBlank()) ? null
                    : ciudades.buscar(estadoFiltro, ciudad).orElse(null);
            org.springframework.data.domain.Page<ItemStock> pag = items.buscar(codigo, codigos, texto,
                    estadoFiltro, ciudadFiltro, marca, modeloFiltro, com.stockautopartes.web.Paginas.pedirElegido(pagina, tamanio));
            resultados = pag.getContent();
            model.addAttribute("pag", pag);
        }
        model.addAttribute("q", texto);
        model.addAttribute("busco", busco);
        model.addAttribute("modelo", modeloFiltro);
        model.addAttribute("codigoBuscado", codigo);
        model.addAttribute("equivalentes", codigos);
        model.addAttribute("resultados", resultados);
        model.addAttribute("diasNoConfirmado", diasNoConfirmado);
        return "fragmentos/resultados :: resultados";
    }
}
