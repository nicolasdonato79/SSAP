package com.stockautopartes.inventario;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface ItemStockRepository extends JpaRepository<ItemStock, Long> {

    long countByVendedor_Id(Long vendedorId);

    /** Grilla de "Mi stock", por páginas. */
    @EntityGraph(attributePaths = "marca")
    Page<ItemStock> findByVendedor_IdOrderByCodigoOriginalAsc(Long vendedorId, Pageable pagina);

    /** Condiciones de la búsqueda (las usan la consulta y el conteo para paginar). */
    String DESDE_BUSQUEDA = """
            FROM item_stock i
            JOIN usuario u ON u.id = i.usuario_id
            WHERE (i.codigo_normalizado IN (:codigos)
                   OR i.descripcion % :texto
                   OR NOT EXISTS (
                        SELECT 1 FROM regexp_split_to_table(texto_para_buscar(CAST(:texto AS VARCHAR)), '[[:space:]]+') w
                        WHERE w <> '' AND i.texto_busqueda NOT LIKE '%' || w || '%'))
              AND (CAST(:estado AS VARCHAR) IS NULL OR u.estado = CAST(:estado AS VARCHAR))
              AND (CAST(:ciudad AS VARCHAR) IS NULL OR lower(u.ciudad) = lower(CAST(:ciudad AS VARCHAR)))
              AND (CAST(:marcaId AS INTEGER) IS NULL OR i.marca_id = CAST(:marcaId AS INTEGER))
              AND (CAST(:modelo AS VARCHAR) IS NULL
                   OR COALESCE(i.vehiculo, '') ILIKE CONCAT('%', CAST(:modelo AS VARCHAR), '%')
                   OR COALESCE(i.descripcion, '') ILIKE CONCAT('%', CAST(:modelo AS VARCHAR), '%'))
              AND (i.cantidad IS NULL OR i.cantidad > 0)
            """;

    /**
     * Por código (el buscado más sus reemplazos/equivalentes), por descripción parecida (pg_trgm), o
     * porque cada palabra buscada aparece en código, descripción, marca, vehículo o marca del vehículo
     * (columna texto_busqueda, ver V13): "disco ranger", "pastillas toyota", "bujia civic".
     * Orden: código exacto, después reemplazos/equivalentes, después por similitud de descripción.
     *
     * Marca (del vehículo): la que tiene asignada cada ítem (ver DetectorMarca).
     * Modelo: texto libre que tiene que aparecer en el vehículo o en la descripción (ej. "Ranger").
     * Con texto vacío (solo filtros) trae todo lo que cumple los filtros.
     */
    @Query(value = "SELECT i.* " + DESDE_BUSQUEDA + """
            ORDER BY (i.codigo_normalizado = :codigo) DESC,
                     (i.codigo_normalizado IN (:codigos)) DESC,
                     similarity(COALESCE(i.descripcion, ''), :texto) DESC,
                     i.id
            """,
            countQuery = "SELECT count(*) " + DESDE_BUSQUEDA,
            nativeQuery = true)
    Page<ItemStock> buscar(@Param("codigo") String codigo,
                           @Param("codigos") Collection<String> codigos,
                           @Param("texto") String texto,
                           @Param("estado") String estado,
                           @Param("ciudad") String ciudad,
                           @Param("marcaId") Integer marcaId,
                           @Param("modelo") String modelo,
                           Pageable pagina);
}
