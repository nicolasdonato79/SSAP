package com.stockautopartes.demanda;

import com.stockautopartes.inventario.CodigoNormalizer;
import com.stockautopartes.usuario.Usuario;
import jakarta.persistence.*;

import java.time.Instant;

/** Algo que un comprador busca y no encontró: queda abierta hasta que alguien lo cargue. */
@Entity
@Table(name = "demanda")
public class Demanda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario comprador;

    @Column(nullable = false, length = 300)
    private String texto;

    @Column(name = "codigo_normalizado", length = 80)
    private String codigoNormalizado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDemanda estado = EstadoDemanda.ABIERTA;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn = Instant.now();

    protected Demanda() {
    }

    public Demanda(Usuario comprador, String texto) {
        this.comprador = comprador;
        this.texto = texto;
        this.codigoNormalizado = CodigoNormalizer.normalizar(texto);
    }

    public Long getId() { return id; }
    public Usuario getComprador() { return comprador; }
    public String getTexto() { return texto; }
    public String getCodigoNormalizado() { return codigoNormalizado; }
    public EstadoDemanda getEstado() { return estado; }
    public Instant getCreadaEn() { return creadaEn; }
}
