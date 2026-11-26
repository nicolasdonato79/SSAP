package com.stockautopartes.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Todas las noches borra lo viejo de la sincronización: marcas de borrado, operaciones recibidas y equipos
 * vencidos o revocados. Un equipo que no sincroniza desde antes de la purga baja todo de nuevo
 * (SyncPullService lo detecta con sync_estado.purgado_hasta_xid).
 */
@Component
public class SyncLimpieza {

    private static final Logger log = LoggerFactory.getLogger(SyncLimpieza.class);

    private final JdbcTemplate jdbc;
    private final int dias;

    public SyncLimpieza(JdbcTemplate jdbc, @Value("${app.sync.dias-purga:30}") int dias) {
        this.jdbc = jdbc;
        this.dias = dias;
    }

    @Scheduled(cron = "${app.sync.cron-purga:0 17 4 * * *}", zone = "UTC")
    @Transactional
    public void purgar() {
        String limite = "now() - make_interval(days => " + dias + ")";
        // Primero se anota hasta qué transacción se purga, después se borra
        jdbc.update("""
                UPDATE sync_estado SET purgado_hasta_xid = greatest(purgado_hasta_xid,
                    coalesce((SELECT max(sync_xid) FROM sync_borrado WHERE borrado_en < %s), '0'))
                WHERE id = 1
                """.formatted(limite));
        int borrados = jdbc.update("DELETE FROM sync_borrado WHERE borrado_en < " + limite);
        int operaciones = jdbc.update("DELETE FROM sync_operacion WHERE recibida_en < " + limite);
        int equipos = jdbc.update("DELETE FROM dispositivo WHERE expira_en < " + limite
                + " OR revocado_en < " + limite);
        if (borrados + operaciones + equipos > 0) {
            log.info("Sync: se purgaron {} marcas de borrado, {} operaciones y {} equipos", borrados, operaciones, equipos);
        }
    }
}
