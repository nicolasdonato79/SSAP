package com.stockautopartes.inventario;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Recorre las cadenas de reemplazos y equivalencias entre números de pieza.
 *
 * Ejemplo (Ford): BK3Z-9A543-A → FB3Z-9A543-A → FB3Z-9A543-F → FB3Z-9A543-J
 *   - Quien busca BK3Z-9A543-A puede usar cualquiera de los cuatro.
 *   - Quien busca FB3Z-9A543-J solo ese (los anteriores no necesariamente le sirven).
 */
@Service
public class EquivalenciaService {

    /** Corta cadenas mal cargadas o ciclos largos. */
    private static final int MAX_NIVELES = 20;
    private static final int LOTE = 1000;

    /** Aristas "desde → hacia" en el sentido "quien busca 'desde' puede usar 'hacia'". */
    private static final String ARISTAS = """
            aristas(desde, hacia) AS (
                SELECT codigo_a, codigo_b FROM relacion_codigo WHERE tipo = 'REEMPLAZO'
                UNION ALL SELECT codigo_a, codigo_b FROM relacion_codigo WHERE tipo = 'EQUIVALENTE'
                UNION ALL SELECT codigo_b, codigo_a FROM relacion_codigo WHERE tipo = 'EQUIVALENTE'
            )""";

    private static final String SIRVEN_PARA = "WITH RECURSIVE " + ARISTAS + """
            ,
            sirve(codigo, nivel) AS (
                SELECT CAST(:codigo AS VARCHAR(80)), 0
                UNION
                SELECT a.hacia, s.nivel + 1 FROM sirve s JOIN aristas a ON a.desde = s.codigo
                WHERE s.nivel < :max
            )
            SELECT DISTINCT codigo FROM sirve
            """;

    /** Mismo recorrido pero al revés: qué códigos buscados quedan cubiertos por los que se cargaron. */
    private static final String SERVIDOS_POR = "WITH RECURSIVE " + ARISTAS + """
            ,
            servidos(codigo, nivel) AS (
                SELECT CAST(x.codigo AS VARCHAR(80)), 0 FROM unnest(CAST(ARRAY[:codigos] AS VARCHAR[])) AS x(codigo)
                UNION
                SELECT a.desde, s.nivel + 1 FROM servidos s JOIN aristas a ON a.hacia = s.codigo
                WHERE s.nivel < :max
            )
            SELECT DISTINCT codigo FROM servidos
            """;

    private static final String INSERTAR = """
            INSERT INTO relacion_codigo (codigo_a, codigo_b, codigo_a_original, codigo_b_original, tipo, usuario_id)
            VALUES (:a, :b, :aOriginal, :bOriginal, :tipo, :usuario)
            ON CONFLICT (codigo_a, codigo_b, tipo) DO NOTHING
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public EquivalenciaService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Códigos normalizados cuyo stock le sirve a quien busca {@code codigoNormalizado} (lo incluye). */
    public Set<String> codigosQueSirvenPara(String codigoNormalizado) {
        if (codigoNormalizado == null || codigoNormalizado.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(jdbc.queryForList(SIRVEN_PARA,
                new MapSqlParameterSource("codigo", codigoNormalizado).addValue("max", MAX_NIVELES),
                String.class));
    }

    /** Códigos normalizados que alguien podría estar buscando y que quedan cubiertos por {@code cargados}. */
    public Set<String> codigosServidosPor(Collection<String> cargados) {
        Set<String> resultado = new HashSet<>();
        List<String> lista = new ArrayList<>(cargados);
        for (int i = 0; i < lista.size(); i += LOTE) {
            List<String> lote = lista.subList(i, Math.min(i + LOTE, lista.size()));
            resultado.addAll(jdbc.queryForList(SERVIDOS_POR,
                    new MapSqlParameterSource("codigos", lote).addValue("max", MAX_NIVELES), String.class));
        }
        return resultado;
    }

    /** Guarda relaciones nuevas; las repetidas o de un código consigo mismo se ignoran. */
    public void registrar(Collection<RelacionCodigo> relaciones, Long usuarioId) {
        List<MapSqlParameterSource> filas = new ArrayList<>();
        for (RelacionCodigo r : relaciones) {
            String a = CodigoNormalizer.normalizar(r.anterior());
            String b = CodigoNormalizer.normalizar(r.nuevo());
            if (a.isEmpty() || b.isEmpty() || a.equals(b) || a.length() > 80 || b.length() > 80) {
                continue;
            }
            String aOrig = r.anterior().trim();
            String bOrig = r.nuevo().trim();
            if (r.tipo() == RelacionCodigo.Tipo.EQUIVALENTE && a.compareTo(b) > 0) {
                String t = a; a = b; b = t;
                t = aOrig; aOrig = bOrig; bOrig = t;
            }
            filas.add(new MapSqlParameterSource()
                    .addValue("a", a).addValue("b", b)
                    .addValue("aOriginal", recortar(aOrig)).addValue("bOriginal", recortar(bOrig))
                    .addValue("tipo", r.tipo().name())
                    .addValue("usuario", usuarioId));
        }
        if (!filas.isEmpty()) {
            jdbc.batchUpdate(INSERTAR, filas.toArray(new MapSqlParameterSource[0]));
        }
    }

    private static String recortar(String s) {
        return s.length() <= 80 ? s : s.substring(0, 80);
    }
}
