package com.stockautopartes.solicitud;

import com.stockautopartes.inventario.ItemStock;
import com.stockautopartes.usuario.Usuario;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Un comprador le pide a un vendedor una cantidad de un ítem, y negocian el precio:
 * el vendedor confirma stock y ofrece precio; el comprador acepta o contraoferta;
 * ante una contraoferta el vendedor acepta o rechaza.
 * Copia código, descripción y precio publicado porque el stock se reemplaza en cada carga.
 * Los teléfonos de cada uno se muestran recién cuando el pedido queda ACEPTADO.
 */
@Entity
@Table(name = "solicitud")
public class Solicitud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id para la sincronización (issue #9). */
    @Column(nullable = false, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comprador_id")
    private Usuario comprador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendedor_id")
    private Usuario vendedor;

    @Column(nullable = false, length = 80)
    private String codigo;

    @Column(length = 300)
    private String descripcion;

    /** Precio publicado al momento de pedir; null = era a consultar. */
    @Column(name = "precio_usd", precision = 12, scale = 2)
    private BigDecimal precioUsd;

    /** Precio unitario que ofrece el vendedor al confirmar stock. */
    @Column(name = "precio_oferta", precision = 12, scale = 2)
    private BigDecimal precioOferta;

    /** Precio unitario que propone el comprador. */
    @Column(name = "precio_contraoferta", precision = 12, scale = 2)
    private BigDecimal precioContraoferta;

    @Column(nullable = false)
    private int cantidad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn = Instant.now();

    @Column(name = "actualizada_en")
    private Instant actualizadaEn;

    @Column(name = "respondida_en")
    private Instant respondidaEn;

    protected Solicitud() {
    }

    public Solicitud(Usuario comprador, ItemStock item, int cantidad) {
        this.comprador = comprador;
        this.vendedor = item.getVendedor();
        this.codigo = item.getCodigoOriginal();
        this.descripcion = item.getDescripcion();
        this.precioUsd = item.getPrecioUsd();
        this.cantidad = cantidad;
    }

    /** Para tests. */
    Solicitud(Usuario comprador, Usuario vendedor, String codigo, BigDecimal precioUsd, int cantidad) {
        this.comprador = comprador;
        this.vendedor = vendedor;
        this.codigo = codigo;
        this.precioUsd = precioUsd;
        this.cantidad = cantidad;
    }

    // ---------- Transiciones ----------

    /** Vendedor: tiene stock y ofrece este precio unitario. */
    public void ofertar(BigDecimal precio) {
        exigir(EstadoSolicitud.PENDIENTE);
        this.precioOferta = precio;
        cambiar(EstadoSolicitud.OFERTADA);
        this.respondidaEn = actualizadaEn;
    }

    /** Vendedor: no lo tiene. */
    public void sinStock() {
        exigir(EstadoSolicitud.PENDIENTE);
        cambiar(EstadoSolicitud.RECHAZADA);
        this.respondidaEn = actualizadaEn;
    }

    /** Comprador: acepta el precio ofrecido. */
    public void aceptarOferta() {
        exigir(EstadoSolicitud.OFERTADA);
        cambiar(EstadoSolicitud.ACEPTADA);
    }

    /** Comprador: propone otro precio unitario. */
    public void contraofertar(BigDecimal precio) {
        exigir(EstadoSolicitud.OFERTADA);
        this.precioContraoferta = precio;
        cambiar(EstadoSolicitud.CONTRAOFERTA);
    }

    /** Vendedor: acepta la contraoferta. */
    public void aceptarContraoferta() {
        exigir(EstadoSolicitud.CONTRAOFERTA);
        cambiar(EstadoSolicitud.ACEPTADA);
    }

    /** Vendedor: rechaza la contraoferta. */
    public void rechazarContraoferta() {
        exigir(EstadoSolicitud.CONTRAOFERTA);
        cambiar(EstadoSolicitud.RECHAZADA);
    }

    /** Comprador: cancela mientras no esté cerrado. */
    public void cancelar() {
        if (estado.esFinal()) {
            throw new SolicitudException("El pedido ya está cerrado.");
        }
        cambiar(EstadoSolicitud.CANCELADA);
    }

    private void exigir(EstadoSolicitud esperado) {
        if (estado != esperado) {
            throw new SolicitudException("El pedido cambió mientras tanto. Recargá la página.");
        }
    }

    private void cambiar(EstadoSolicitud nuevo) {
        estado = nuevo;
        actualizadaEn = Instant.now();
    }

    // ---------- Lectura ----------

    /** El último precio unitario en juego: contraoferta, si no oferta, si no el publicado. Puede ser null. */
    public BigDecimal getPrecioVigente() {
        if (precioContraoferta != null) return precioContraoferta;
        if (precioOferta != null) return precioOferta;
        return precioUsd;
    }

    /** Total según el precio vigente; null si todavía no hay precio. */
    public BigDecimal getTotalUsd() {
        BigDecimal p = getPrecioVigente();
        return p == null ? null : p.multiply(BigDecimal.valueOf(cantidad));
    }

    /** Rechazada por no tener stock (y no por rechazar una contraoferta). */
    public boolean isSinStock() {
        return estado == EstadoSolicitud.RECHAZADA && precioContraoferta == null;
    }

    public boolean isPendiente() { return estado == EstadoSolicitud.PENDIENTE; }
    public boolean isOfertada() { return estado == EstadoSolicitud.OFERTADA; }
    public boolean isContraoferta() { return estado == EstadoSolicitud.CONTRAOFERTA; }
    public boolean isAceptada() { return estado == EstadoSolicitud.ACEPTADA; }
    public boolean isAbierta() { return !estado.esFinal(); }

    public Long getId() { return id; }
    public UUID getUuid() { return uuid; }
    public Usuario getComprador() { return comprador; }
    public Usuario getVendedor() { return vendedor; }
    public String getCodigo() { return codigo; }
    public String getDescripcion() { return descripcion; }
    public BigDecimal getPrecioUsd() { return precioUsd; }
    public BigDecimal getPrecioOferta() { return precioOferta; }
    public BigDecimal getPrecioContraoferta() { return precioContraoferta; }
    public int getCantidad() { return cantidad; }
    public EstadoSolicitud getEstado() { return estado; }
    public Instant getCreadaEn() { return creadaEn; }
    public Instant getActualizadaEn() { return actualizadaEn; }
    public Instant getRespondidaEn() { return respondidaEn; }
}
