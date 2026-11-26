package com.stockautopartes.importacion;

import com.stockautopartes.matching.MatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Procesa el Excel en segundo plano: lee, valida, reemplaza el stock (CargaStock) y dispara el matching.
 */
@Component
public class ImportacionProcessor {

    private static final Logger log = LoggerFactory.getLogger(ImportacionProcessor.class);

    private final ImportacionRepository importaciones;
    private final ExcelStockReader lector;
    private final CargaStock carga;
    private final MatchingService matching;
    private final TransactionTemplate tx;
    private final int maxErroresGuardados;

    public ImportacionProcessor(ImportacionRepository importaciones,
                                ExcelStockReader lector,
                                CargaStock carga,
                                MatchingService matching,
                                TransactionTemplate tx,
                                @Value("${app.importacion.max-errores-guardados}") int maxErroresGuardados) {
        this.importaciones = importaciones;
        this.lector = lector;
        this.carga = carga;
        this.matching = matching;
        this.tx = tx;
        this.maxErroresGuardados = maxErroresGuardados;
    }

    @Async
    public void procesar(long importacionId, Path archivo) {
        actualizar(importacionId, Importacion::procesando);
        try {
            ResultadoLectura resultado = lector.leer(archivo);
            if (resultado.filasOk().isEmpty()) {
                // Nunca vaciar el stock por un archivo que no se pudo leer (sin títulos reconocibles, todo con errores)
                String motivo = resultado.errores().isEmpty() ? "El archivo no trae ningún repuesto."
                        : "No se cargó nada (tu stock quedó como estaba): " + resultado.errores().get(0);
                actualizar(importacionId, imp -> imp.fallar(motivo));
                return;
            }
            Instant ahora = Instant.now();

            tx.executeWithoutResult(status -> {
                Importacion imp = importaciones.findById(importacionId).orElseThrow();
                carga.aplicar(imp.getUsuarioId(), resultado.filasOk(), ahora, ahora);
                String errores = resultado.errores().stream()
                        .limit(maxErroresGuardados)
                        .collect(Collectors.joining("\n"));
                imp.terminar(resultado.filasOk().size(), resultado.errores().size(), errores);
            });

            Importacion imp = importaciones.findById(importacionId).orElseThrow();
            matching.buscarDemandasPara(imp.getUsuarioId(), CargaStock.codigos(resultado.filasOk()));

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

    private void actualizar(long id, java.util.function.Consumer<Importacion> cambio) {
        tx.executeWithoutResult(status -> importaciones.findById(id).ifPresent(cambio));
    }
}
