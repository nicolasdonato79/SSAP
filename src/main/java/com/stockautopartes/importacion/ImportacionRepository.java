package com.stockautopartes.importacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImportacionRepository extends JpaRepository<Importacion, Long> {

    Optional<Importacion> findFirstByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);
}
