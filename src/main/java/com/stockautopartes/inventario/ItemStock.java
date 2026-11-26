package com.stockautopartes.inventario;

import com.stockautopartes.usuario.Usuario;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "item_stock")
public class ItemStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id para la sincronización (issue #9). Lo puede generar el equipo sin conexión; no cambia nunca. */
    @Column(nullable = false, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario vendedor;

    @Column(name = "codigo_original", nullable = false, length = 80)
    private String codigoOriginal;

    @Column(name = "codigo_normalizado", nullable = false, length = 80)
    private String codigoNormalizado;

    @Column(length = 300) // puede quedar vacía (V3)
    private String descripcion;

    @Column(name = "marca_repuesto", length = 80)
    private String marcaRepuesto;

    @Column(length = 150)
    private String vehiculo;

    /** Marca del vehículo (la asigna DetectorMarca). null = no se pudo saber. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id")
    private com.stockautopartes.marca.Marca marca;

    /** null = "consultar" (el vendedor no informó cuántas tiene). */
    private Integer cantidad;

    /** null = precio a consultar. */
    @Column(name = "precio_usd", precision = 12, scale = 2)
    private BigDecimal precioUsd;

    @Column(name = "cargado_en", nullable = false)
    private Instant cargadoEn;

    protected ItemStock() {
    }

    /** Alta manual desde "Mi stock" (sin Excel). cantidad/precio null = a consultar. */
    public static ItemStock manual(Usuario vendedor, String codigo, String descripcion, String vehiculo,
                                   Integer cantidad, BigDecimal precioUsd,
                                   com.stockautopartes.marca.Marca marca, Instant ahora) {
        return manual(UUID.randomUUID(), vendedor, codigo, descripcion, vehiculo, cantidad, precioUsd, marca, ahora);
    }

    /** Alta con el uuid que generó el equipo (alta hecha sin conexión). */
    public static ItemStock manual(UUID uuid, Usuario vendedor, String codigo, String descripcion, String vehiculo,
                                   Integer cantidad, BigDecimal precioUsd,
                                   com.stockautopartes.marca.Marca marca, Instant ahora) {
        ItemStock i = new ItemStock();
        i.uuid = uuid;
        i.vendedor = vendedor;
        i.modificar(codigo, descripcion, vehiculo, cantidad, precioUsd, marca, ahora);
        return i;
    }

    /** Cambio manual. Cuenta como stock confirmado hoy. La marca del repuesto (columna del Excel) no se toca. */
    public void modificar(String codigo, String descripcion, String vehiculo, Integer cantidad, BigDecimal precioUsd,
                          com.stockautopartes.marca.Marca marca, Instant ahora) {
        this.codigoOriginal = codigo;
        this.codigoNormalizado = CodigoNormalizer.normalizar(codigo);
        this.descripcion = descripcion;
        this.vehiculo = vehiculo;
        this.cantidad = cantidad;
        this.precioUsd = precioUsd;
        this.marca = marca;
        this.cargadoEn = ahora;
    }

    /** true si el stock no se actualizó en los últimos {@code dias} días. */
    public boolean noConfirmado(int dias) {
        return cargadoEn.isBefore(Instant.now().minus(Duration.ofDays(dias)));
    }

    public Long getId() { return id; }
    public UUID getUuid() { return uuid; }
    public Usuario getVendedor() { return vendedor; }
    public String getCodigoOriginal() { return codigoOriginal; }
    public String getCodigoNormalizado() { return codigoNormalizado; }
    public String getDescripcion() { return descripcion; }
    public String getMarcaRepuesto() { return marcaRepuesto; }
    public com.stockautopartes.marca.Marca getMarca() { return marca; }
    public String getVehiculo() { return vehiculo; }
    public Integer getCantidad() { return cantidad; }
    public BigDecimal getPrecioUsd() { return precioUsd; }
    public Instant getCargadoEn() { return cargadoEn; }
}
