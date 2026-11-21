package com.stockautopartes.ubicacion;

import jakarta.persistence.*;

/** Ciudad o pueblo de un estado de Venezuela (catálogo, migración V7). */
@Entity
@Table(name = "ciudad")
public class Ciudad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 60)
    private String estado;

    @Column(nullable = false, length = 80)
    private String nombre;

    protected Ciudad() {
    }

    public Integer getId() { return id; }
    public String getEstado() { return estado; }
    public String getNombre() { return nombre; }
}
