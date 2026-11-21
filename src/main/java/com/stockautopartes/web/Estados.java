package com.stockautopartes.web;

import java.util.List;

/** Estados de Venezuela, para el registro y el filtro de búsqueda. */
public final class Estados {

    public static final List<String> TODOS = List.of(
            "Amazonas", "Anzoátegui", "Apure", "Aragua", "Barinas", "Bolívar", "Carabobo",
            "Cojedes", "Delta Amacuro", "Distrito Capital", "Falcón", "Guárico", "La Guaira",
            "Lara", "Mérida", "Miranda", "Monagas", "Nueva Esparta", "Portuguesa", "Sucre",
            "Táchira", "Trujillo", "Yaracuy", "Zulia");

    private Estados() {
    }
}
