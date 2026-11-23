package com.stockautopartes.importacion;

import com.stockautopartes.inventario.CodigoNormalizer;
import com.stockautopartes.inventario.EquivalenciaService;
import com.stockautopartes.inventario.RelacionCodigo;
import com.stockautopartes.inventario.StockWriter;
import com.stockautopartes.matching.MatchingService;
import com.stockautopartes.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Procesa el Excel en segundo plano: lee, valida, reemplaza el stock y dispara el matching.
 */
@Component
public class ImportacionProcessor {

    private static final Logger log = LoggerFactory.getLogger(ImportacionProcessor.class);

    private final ImportacionRepository importaciones;
    private final UsuarioRepository usuarios;
    private final ExcelStockReader lector;
    private final StockWriter stockWriter;
    private final MatchingService matching;
    private final EquivalenciaService equivalencias;
    private final com.stockautopartes.marca.DetectorMarca detectorMarca;
    private final com.stockautopartes.inventario.AsignadorMarca asignadorMarca;
    private final TransactionTemplate tx;
    private final int maxErroresGuardados;

    public ImportacionProcessor(ImportacionRepository importaciones,
                                UsuarioRepository usuarios,
                                ExcelStockReader lector,
                                StockWriter stockWriter,
                                MatchingService matching,
                                EquivalenciaService equivalencias,
                                com.stockautopartes.marca.DetectorMarca detectorMarca,
                                com.stockautopartes.inventario.AsignadorMarca asignadorMarca,
                                TransactionTemplate tx,
                                @Value("${app.importacion.max-errores-guardados}") int maxErroresGuardados) {
        this.importaciones = importaciones;
        this.usuarios = usuarios;
        this.lector = lector;
        this.stockWriter = stockWriter;
        this.matching = matching;
        this.equivalencias = equivalencias;
        this.detectorMarca = detectorMarca;
        this.asignadorMarca = asignadorMarca;
        this.tx = tx;
        this.maxErroresGuardados = maxErroresGuardados;
    }

    @Async
    public void procesar(long importacionId, Path archivo) {
        actualizar(importacionId, Importacion::procesando);
        try {
            ResultadoLectura resultado = lector.leer(archivo);
            Instant ahora = Instant.now();

            tx.executeWithoutResult(status -> {
                Importacion imp = importaciones.findById(importacionId).orElseThrow();
                Set<Integer> marcasVendedor = asignadorMarca.marcasDe(imp.getUsuarioId());
                stockWriter.reemplazarStock(imp.getUsuarioId(), resultado.filasOk(), ahora,
                        f -> detectorMarca.detectar(f.vehiculo(), f.marca(), f.descripcion(), marcasVendedor));
                equivalencias.registrar(relaciones(resultado.filasOk()), imp.getUsuarioId());
                usuarios.findById(imp.getUsuarioId()).ifPresent(u -> u.setUltimaCarga(ahora));
                String errores = resultado.errores().stream()
                        .limit(maxErroresGuardados)
                        .collect(Collectors.joining("\n"));
                imp.terminar(resultado.filasOk().size(), resultado.errores().size(), errores);
            });

            Set<String> codigos = resultado.filasOk().stream()
                    .map(f -> CodigoNormalizer.normalizar(f.codigo()))
                    .collect(Collectors.toSet());
            Importacion imp = importaciones.findById(importacionId).orElseThrow();
            matching.buscarDemandasPara(imp.getUsuarioId(), codigos);

        } catch (Exception e) {
            log.error("Falló la importación {}", importacionId, e);
            actualizar(importacionId, imp -> imp.fallar("No se pudo leer el archivo. ¿Es un Excel (.xlsx o .xls) sin contraseña?"));
        } finally {
            try {
                Files.deleteIfExists(archivo);
            } catch (Exception e) {
                log.warn("No se pudo borrar el temporal {}", archivo);
            }
        }
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

    private void actualizar(long id, java.util.function.Consumer<Importacion> cambio) {
        tx.executeWithoutResult(status -> importaciones.findById(id).ifPresent(cambio));
    }
}
