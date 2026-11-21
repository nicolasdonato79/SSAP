package com.stockautopartes.marca;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface MarcaRepository extends JpaRepository<Marca, Integer> {

    List<Marca> findByActivaTrueOrderByTipoAscNombreAsc();

    List<Marca> findByIdInAndActivaTrue(Collection<Integer> ids);

    boolean existsByNombreIgnoreCaseAndTipo(String nombre, Marca.Tipo tipo);

    /** Marcas activas agrupadas: Autos, Camiones, Motos (para checkboxes y selects). */
    default Map<Marca.Tipo, List<Marca>> agrupadas() {
        Map<Marca.Tipo, List<Marca>> grupos = new LinkedHashMap<>();
        for (Marca.Tipo t : Marca.Tipo.values()) {
            grupos.put(t, new java.util.ArrayList<>());
        }
        findByActivaTrueOrderByTipoAscNombreAsc().forEach(m -> grupos.get(m.getTipo()).add(m));
        return grupos;
    }
}
