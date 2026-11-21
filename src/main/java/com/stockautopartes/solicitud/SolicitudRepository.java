package com.stockautopartes.solicitud;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {

    List<Solicitud> findTop100ByComprador_IdOrderByCreadaEnDesc(Long compradorId);

    List<Solicitud> findTop100ByVendedor_IdOrderByCreadaEnDesc(Long vendedorId);

    boolean existsByComprador_IdAndVendedor_IdAndCodigoAndEstadoIn(Long compradorId, Long vendedorId,
                                                                   String codigo, Collection<EstadoSolicitud> estados);

    /** Pedidos donde le toca responder a este usuario (como vendedor o como comprador). */
    @Query("""
            select count(s) from Solicitud s
            where (s.vendedor.id = ?1 and s.estado in (com.stockautopartes.solicitud.EstadoSolicitud.PENDIENTE,
                                                       com.stockautopartes.solicitud.EstadoSolicitud.CONTRAOFERTA))
               or (s.comprador.id = ?1 and s.estado = com.stockautopartes.solicitud.EstadoSolicitud.OFERTADA)
            """)
    long countParaResponder(Long usuarioId);
}
