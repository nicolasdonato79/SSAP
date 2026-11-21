package com.stockautopartes.demanda;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DemandaRepository extends JpaRepository<Demanda, Long> {

    List<Demanda> findByEstadoAndCodigoNormalizadoIn(EstadoDemanda estado, Collection<String> codigos);
}
