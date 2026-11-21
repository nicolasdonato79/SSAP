package com.stockautopartes.inventario;

import com.stockautopartes.usuario.Usuario;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "item_stock")
public class ItemStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario vendedor;

    @Column(name = "codigo_original", nullable = false, length = 80)
    private String codigoOriginal;

    @Column(name = "codigo_normalizado", nullable = false, length = 80)
    private String codigoNormalizado;

    @Column(nullable = false, length = 300)
    private String descripcion;

    @Column(name = "marca_repuesto", length = 80)
    private String marcaRepuesto;

    @Column(length = 150)
    private String vehiculo;

    /** null = "consultar" (el vendedor no informó cuántas tiene). */
    private Integer cantidad;

    /** null = precio a consultar. */
    @Column(name = "precio_usd", precision = 12, scale = 2)
    private BigDecimal precioUsd;

    @Column(name = "cargado_en", nullable = false)
    private Instant cargadoEn;

    protected ItemStock() {
    }

    /** true si el stock no se actualizó en los últimos {@code dias} días. */
    public boolean noConfirmado(int dias) {
        return cargadoEn.isBefore(Instant.now().minus(Duration.ofDays(dias)));
    }

    public Long getId() { return id; }
    public Usuario getVendedor() { return vendedor; }
    public String getCodigoOriginal() { return codigoOriginal; }
    public String getCodigoNormalizado() { return codigoNormalizado; }
    public String getDescripcion() { return descripcion; }
    public String getMarcaRepuesto() { return marcaRepuesto; }
    public String getVehiculo() { return vehiculo; }
    public Integer getCantidad() { return cantidad; }
    public BigDecimal getPrecioUsd() { return precioUsd; }
    public Instant getCargadoEn() { return cargadoEn; }
}
