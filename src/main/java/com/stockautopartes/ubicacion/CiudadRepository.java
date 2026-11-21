package com.stockautopartes.ubicacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CiudadRepository extends JpaRepository<Ciudad, Integer> {

    @Query("select c.nombre from Ciudad c where c.estado = ?1 order by c.nombre")
    List<String> nombresDe(String estado);

    /** Devuelve el nombre tal cual está en el catálogo (con tildes y mayúsculas correctas). */
    @Query("select c.nombre from Ciudad c where c.estado = ?1 and lower(c.nombre) = lower(?2)")
    Optional<String> buscar(String estado, String nombre);
}
