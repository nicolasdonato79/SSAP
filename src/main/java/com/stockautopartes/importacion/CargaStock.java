package com.stockautopartes.importacion;

import com.stockautopartes.inventario.AsignadorMarca;
import com.stockautopartes.inventario.CodigoNormalizer;
import com.stockautopartes.inventario.EquivalenciaService;
import com.stockautopartes.inventario.RelacionCodigo;
import com.stockautopartes.inventario.StockWriter;
import com.stockautopartes.marca.DetectorMarca;
import com.stockautopartes.usuario.UsuarioRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aplica las filas ya leídas de un Excel al stock de un vendedor: reemplaza el stock, registra los
 * reemplazos/equivalentes y marca la fecha de última carga. La usan la carga desde la web
 * ({@link ImportacionProcessor}) y la que llega desde un equipo sin conexión (paquete sync).
 * Debe llamarse dentro de una transacción. El matching con las demandas va después del commit.
 */
@Component
public class CargaStock {

    private final StockWriter stockWriter;
    private final EquivalenciaService equivalencias;
    private final DetectorMarca detectorMarca;
    private final AsignadorMarca asignadorMarca;
    private final UsuarioRepository usuarios;

    public CargaStock(StockWriter stockWriter, EquivalenciaService equivalencias, DetectorMarca detectorMarca,
                      AsignadorMarca asignadorMarca, UsuarioRepository usuarios) {
        this.stockWriter = stockWriter;
        this.equivalencias = equivalencias;
        this.detectorMarca = detectorMarca;
        this.asignadorMarca = asignadorMarca;
        this.usuarios = usuarios;
    }

    /**
     * @param cargadoEn fecha de "stock confirmado"
     * @param corte     los ítems cambiados en el servidor después de esta hora se conservan (ver StockWriter)
     */
    public StockWriter.Resumen aplicar(long usuarioId, List<FilaStock> filas, Instant cargadoEn, Instant corte) {
        return aplicar(usuarioId, filas, cargadoEn, corte, f -> null);
    }

    /** @param uuidDe uuid que la app sin conexión ya le dio a cada fila (null = el servidor genera uno). */
    public StockWriter.Resumen aplicar(long usuarioId, List<FilaStock> filas, Instant cargadoEn, Instant corte,
                                       java.util.function.Function<FilaStock, java.util.UUID> uuidDe) {
        Set<Integer> marcasVendedor = asignadorMarca.marcasDe(usuarioId);
        StockWriter.Resumen resumen = stockWriter.reemplazarStock(usuarioId, filas, cargadoEn, corte,
                f -> detectorMarca.detectar(f.vehiculo(), f.marca(), f.descripcion(), marcasVendedor), uuidDe);
        equivalencias.registrar(relaciones(filas), usuarioId);
        usuarios.findById(usuarioId).ifPresent(u -> u.setUltimaCarga(cargadoEn));
        return resumen;
    }

    /** Códigos normalizados de las filas (para el matching con las demandas). */
    public static Set<String> codigos(List<FilaStock> filas) {
        return filas.stream().map(f -> CodigoNormalizer.normalizar(f.codigo())).collect(Collectors.toSet());
    }

    /** Columnas opcionales "Reemplaza a" y "Equivalentes" del Excel → relaciones entre códigos. */
    private static List<RelacionCodigo> relaciones(List<FilaStock> filas) {
        List<RelacionCodigo> lista = new ArrayList<>();
        for (FilaStock f : filas) {
            f.reemplaza().forEach(anterior -> lista.add(RelacionCodigo.reemplazo(anterior, f.codigo())));
            f.equivalentes().forEach(otro -> lista.add(RelacionCodigo.equivalente(f.codigo(), otro)));
        }
        return lista;
    }
}
