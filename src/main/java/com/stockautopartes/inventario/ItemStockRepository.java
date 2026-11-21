package com.stockautopartes.inventario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ItemStockRepository extends JpaRepository<ItemStock, Long> {

    long countByVendedor_Id(Long vendedorId);

    List<ItemStock> findTop300ByVendedor_IdOrderByCodigoOriginalAsc(Long vendedorId);

    /**
     * Por código (el buscado más sus reemplazos/equivalentes) o por descripción parecida (pg_trgm).
     * Orden: código exacto, después reemplazos/equivalentes, después por similitud de descripción.
     */
    @Query(value = """
            SELECT i.* FROM item_stock i
            JOIN usuario u ON u.id = i.usuario_id
            WHERE (i.codigo_normalizado IN (:codigos) OR i.descripcion % :texto)
              AND (CAST(:estado AS VARCHAR) IS NULL OR u.estado = CAST(:estado AS VARCHAR))
              AND (CAST(:ciudad AS VARCHAR) IS NULL OR lower(u.ciudad) = lower(CAST(:ciudad AS VARCHAR)))
              AND (CAST(:marcaId AS INTEGER) IS NULL OR EXISTS (
                     SELECT 1 FROM usuario_marca um
                     WHERE um.usuario_id = u.id AND um.marca_id = CAST(:marcaId AS INTEGER)))
              AND (i.cantidad IS NULL OR i.cantidad > 0)
            ORDER BY (i.codigo_normalizado = :codigo) DESC,
                     (i.codigo_normalizado IN (:codigos)) DESC,
                     similarity(COALESCE(i.descripcion, ''), :texto) DESC
            LIMIT 50
            """, nativeQuery = true)
    List<ItemStock> buscar(@Param("codigo") String codigo,
                           @Param("codigos") Collection<String> codigos,
                           @Param("texto") String texto,
                           @Param("estado") String estado,
                           @Param("ciudad") String ciudad,
                           @Param("marcaId") Integer marcaId);
}
