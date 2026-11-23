package com.stockautopartes.registro;

import com.stockautopartes.usuario.Usuario;
import jakarta.persistence.*;

import java.time.Instant;

/** Link de un solo uso mandado por email (activar cuenta, recuperar contraseña). Se guarda solo el hash. */
@Entity
@Table(name = "token_usuario")
public class TokenUsuario {

    public enum Tipo { VERIFICAR_EMAIL, RECUPERAR_PASSWORD }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Tipo tipo;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    protected TokenUsuario() {
    }

    TokenUsuario(Usuario usuario, Tipo tipo, String tokenHash, Instant expiraEn) {
        this.usuario = usuario;
        this.tipo = tipo;
        this.tokenHash = tokenHash;
        this.expiraEn = expiraEn;
    }

    boolean vigente(Instant ahora) {
        return usadoEn == null && ahora.isBefore(expiraEn);
    }

    void usar(Instant ahora) {
        usadoEn = ahora;
    }

    public Usuario getUsuario() { return usuario; }
    public Tipo getTipo() { return tipo; }
}
