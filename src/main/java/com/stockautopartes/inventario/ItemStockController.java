package com.stockautopartes.inventario;

import com.stockautopartes.marca.Marca;
import com.stockautopartes.matching.MatchingService;
import com.stockautopartes.usuario.Usuario;
import com.stockautopartes.usuario.UsuarioActual;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ABM manual de "Mi stock" (HTMX): agregar un repuesto, editar una fila en el lugar y borrarla.
 * Todo por POST con formularios, así viaja el token CSRF.
 */
@Controller
@RequestMapping("/mi-stock/items")
public class ItemStockController {

    /** Evento que escucha la grilla de "Publicado" para recargarse (ver mi-stock.html). */
    private static final String RECARGAR = "stockActualizado";

    private final ItemStockService servicio;
    private final UsuarioActual usuarioActual;
    private final MatchingService matching;

    public ItemStockController(ItemStockService servicio, UsuarioActual usuarioActual, MatchingService matching) {
        this.servicio = servicio;
        this.usuarioActual = usuarioActual;
        this.matching = matching;
    }

    // ---------- Alta ----------

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        return formNuevo(new ItemStockForm(), List.of(), null, model);
    }

    @GetMapping("/cerrar")
    public String cerrar() {
        return "fragmentos/item-stock :: cerrado";
    }

    @PostMapping
    public String crear(@ModelAttribute ItemStockForm form, Model model, HttpServletResponse response) {
        Usuario yo = usuarioActual.obtener();
        ItemStockService.Resultado r = servicio.crear(yo, form);
        if (!r.ok()) return formNuevo(form, r.errores(), null, model);
        // Si alguien estaba esperando este código, se le avisa (igual que al subir un Excel)
        matching.buscarDemandasPara(yo.getId(), Set.of(r.item().getCodigoNormalizado()));
        response.setHeader("HX-Trigger", RECARGAR);
        return formNuevo(new ItemStockForm(), List.of(), "Listo: " + r.item().getCodigoOriginal() + " quedó publicado.", model);
    }

    // ---------- Modificación (la fila se vuelve formulario) ----------

    @GetMapping("/{id}")
    public String fila(@PathVariable long id, Model model) {
        model.addAttribute("i", servicio.delVendedor(usuarioActual.obtener(), id));
        return "fragmentos/item-stock :: fila";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable long id, Model model) {
        Usuario yo = usuarioActual.obtener();
        ItemStock i = servicio.delVendedor(yo, id);
        return formEditar(i, ItemStockForm.de(i), List.of(), yo, model);
    }

    @PostMapping("/{id}")
    public String guardar(@PathVariable long id, @ModelAttribute ItemStockForm form, Model model) {
        Usuario yo = usuarioActual.obtener();
        ItemStockService.Resultado r = servicio.modificar(yo, id, form);
        if (!r.ok()) return formEditar(r.item(), form, r.errores(), yo, model);
        model.addAttribute("i", r.item());
        return "fragmentos/item-stock :: fila";
    }

    /**
     * El vehículo cambió: la marca se vuelve a buscar (con las reglas de DetectorMarca) y se devuelve la lista
     * de marcas con la detectada elegida. Si después eligen otra a mano, queda esa.
     */
    @GetMapping("/marca-detectada")
    public String marcaDetectada(@RequestParam(defaultValue = "") String vehiculo,
                                 @RequestParam String formId, Model model) {
        if (!formId.matches("[a-z0-9-]{1,40}")) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST);
        Usuario yo = usuarioActual.obtener();
        Marca detectada = servicio.detectarMarca(yo, vehiculo);
        ItemStockForm f = new ItemStockForm();
        f.setMarca(detectada == null ? "" : detectada.getId().toString());
        model.addAttribute("f", f);
        model.addAttribute("formId", formId);
        model.addAttribute("marcasVendedor", opcionesDeMarca(yo, detectada));
        return "fragmentos/item-stock :: marcaSelect";
    }

    // ---------- Baja ----------

    @PostMapping("/{id}/borrar")
    @ResponseBody
    public String borrar(@PathVariable long id, HttpServletResponse response) {
        servicio.borrar(usuarioActual.obtener(), id);
        response.setHeader("HX-Trigger", RECARGAR); // recarga la grilla y el total
        return "";
    }

    // ---------- Apoyo ----------

    private String formNuevo(ItemStockForm form, List<String> errores, String ok, Model model) {
        Usuario yo = usuarioActual.obtener();
        model.addAttribute("f", form);
        model.addAttribute("errores", errores);
        model.addAttribute("ok", ok);
        model.addAttribute("marcasVendedor", opcionesDeMarca(yo, null));
        return "fragmentos/item-stock :: nuevo";
    }

    private String formEditar(ItemStock i, ItemStockForm form, List<String> errores, Usuario yo, Model model) {
        model.addAttribute("i", i);
        model.addAttribute("f", form);
        model.addAttribute("errores", errores);
        model.addAttribute("marcasVendedor", opcionesDeMarca(yo, i.getMarca()));
        return "fragmentos/item-stock :: editar";
    }

    /** Las marcas que trabaja el vendedor, más la que ya tiene el ítem si no es una de ellas. */
    private static List<Marca> opcionesDeMarca(Usuario yo, Marca actual) {
        List<Marca> lista = new ArrayList<>(yo.getMarcasOrdenadas());
        if (actual != null && lista.stream().noneMatch(m -> m.getId().equals(actual.getId()))) lista.add(actual);
        return lista;
    }
}
