package com.stockautopartes.marca;

import jakarta.persistence.*;

/** Marca de vehículo (Ford, Toyota, Yamaha...). Los vendedores eligen cuáles trabajan. */
@Entity
@Table(name = "marca")
public class Marca {

    public enum Tipo {
        AUTO("Autos y camionetas"),
        CAMION("Camiones y buses"),
        MOTO("Motos");

        private final String titulo;

        Tipo(String titulo) {
            this.titulo = titulo;
        }

        public String getTitulo() {
            return titulo;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Tipo tipo;

    @Column(nullable = false)
    private boolean activa = true;

    protected Marca() {
    }

    public Marca(String nombre, Tipo tipo) {
        this.nombre = nombre;
        this.tipo = tipo;
    }

    /** Nombre para mostrar; las de motos llevan aclaración si comparten nombre con una de autos (Honda, Suzuki). */
    public String getEtiqueta() {
        return tipo == Tipo.MOTO ? nombre + " (motos)" : nombre;
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public Tipo getTipo() { return tipo; }
    public boolean isActiva() { return activa; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Marca m && id != null && id.equals(m.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
