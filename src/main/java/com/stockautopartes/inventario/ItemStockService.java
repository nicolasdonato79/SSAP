package com.stockautopartes.inventario;

import com.stockautopartes.importacion.NumeroParser;
import com.stockautopartes.marca.DetectorMarca;
import com.stockautopartes.marca.Marca;
import com.stockautopartes.marca.MarcaRepository;
import com.stockautopartes.usuario.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/** ABM manual del stock (sin Excel). Ojo: la próxima carga de Excel reemplaza todo, también esto. */
@Service
public class ItemStockService {

    private static final BigDecimal PRECIO_MAXIMO = new BigDecimal("9999999999.99");

    private final ItemStockRepository items;
    private final MarcaRepository marcas;
    private final DetectorMarca detector;
    private final Clock clock;

    public ItemStockService(ItemStockRepository items, MarcaRepository marcas, DetectorMarca detector, Clock clock) {
        this.items = items;
        this.marcas = marcas;
        this.detector = detector;
        this.clock = clock;
    }

    /** Resultado: el ítem guardado, o los errores para mostrar en el formulario. */
    public record Resultado(ItemStock item, List<String> errores) {
        public boolean ok() { return errores.isEmpty(); }
    }

    @Transactional
    public Resultado crear(Usuario yo, ItemStockForm f) {
        List<String> errores = new ArrayList<>();
        Datos d = validar(f, errores);
        if (!errores.isEmpty()) return new Resultado(null, errores);
        ItemStock i = items.save(ItemStock.manual(yo, d.codigo, d.descripcion, d.vehiculo, d.cantidad, d.precio,
                marca(f, d, yo), clock.instant()));
        return new Resultado(i, List.of());
    }

    @Transactional
    public Resultado modificar(Usuario yo, long id, ItemStockForm f) {
        ItemStock i = delVendedor(yo, id);
        List<String> errores = new ArrayList<>();
        Datos d = validar(f, errores);
        if (!errores.isEmpty()) return new Resultado(i, errores);
        i.modificar(d.codigo, d.descripcion, d.vehiculo, d.cantidad, d.precio, marca(f, d, yo), clock.instant());
        return new Resultado(i, List.of());
    }

    @Transactional
    public void borrar(Usuario yo, long id) {
        items.delete(delVendedor(yo, id));
    }

    /** El ítem, solo si es de este vendedor (si no, 404: no se revela que existe). */
    @Transactional(readOnly = true)
    public ItemStock delVendedor(Usuario yo, long id) {
        return items.findById(id)
                .filter(i -> i.getVendedor().getId().equals(yo.getId()))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }

    private record Datos(String codigo, String descripcion, String vehiculo, Integer cantidad, BigDecimal precio) {
    }

    private static Datos validar(ItemStockForm f, List<String> errores) {
        String codigo = f.getCodigo().strip();
        String descripcion = f.getDescripcion().strip();
        String vehiculo = f.getVehiculo().strip();
        if (codigo.isEmpty() || CodigoNormalizer.normalizar(codigo).isEmpty()) errores.add("Poné el código del repuesto.");
        else if (codigo.length() > 80) errores.add("El código puede tener hasta 80 caracteres.");
        if (descripcion.length() > 300) errores.add("La descripción puede tener hasta 300 caracteres.");
        if (vehiculo.length() > 150) errores.add("El vehículo puede tener hasta 150 caracteres.");

        Integer cantidad = null;
        String c = f.getCantidad().strip().toLowerCase(Locale.ROOT);
        if (!c.isEmpty() && !c.equals("consultar")) {
            cantidad = NumeroParser.parsearCantidad(c).orElse(null);
            if (cantidad == null) errores.add("La cantidad tiene que ser un número entero o \"consultar\".");
        }

        BigDecimal precio = null;
        String p = f.getPrecio().strip().toLowerCase(Locale.ROOT);
        if (!p.isEmpty() && !p.equals("consultar")) {
            precio = NumeroParser.parsear(p).orElse(null);
            if (precio == null || precio.signum() < 0 || precio.compareTo(PRECIO_MAXIMO) > 0) {
                errores.add("El precio no parece válido. Ej: 35,50 (o vacío = a consultar).");
                precio = null;
            }
        }
        return new Datos(codigo, descripcion.isEmpty() ? null : descripcion, vehiculo.isEmpty() ? null : vehiculo,
                cantidad, precio);
    }

    /** La marca que corresponde a un vehículo para este vendedor (null si no se puede saber). */
    public Marca detectarMarca(Usuario yo, String vehiculo) {
        Set<Integer> delVendedor = yo.getMarcasOrdenadas().stream().map(Marca::getId).collect(Collectors.toSet());
        String v = vehiculo == null || vehiculo.isBlank() ? null : vehiculo.strip();
        Integer id = detector.detectar(v, null, null, delVendedor);
        return id == null ? null : marcas.findById(id).orElse(null);
    }

    /** La marca elegida en el formulario o, en "Automática", la que detecta DetectorMarca. */
    private Marca marca(ItemStockForm f, Datos d, Usuario yo) {
        Set<Integer> delVendedor = yo.getMarcasOrdenadas().stream().map(Marca::getId).collect(Collectors.toSet());
        Integer id = null;
        if (!f.getMarca().isBlank()) {
            try {
                id = Integer.valueOf(f.getMarca().strip());
            } catch (NumberFormatException e) {
                id = null;
            }
        }
        if (id == null) id = detector.detectar(d.vehiculo, null, d.descripcion, delVendedor);
        return id == null ? null : marcas.findById(id).orElse(null);
    }
}
