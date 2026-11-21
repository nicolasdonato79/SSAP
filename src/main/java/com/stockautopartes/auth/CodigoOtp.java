package com.stockautopartes.auth;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "codigo_otp")
public class CodigoOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String telefono;

    @Column(name = "codigo_hash", nullable = false, length = 100)
    private String codigoHash;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(nullable = false)
    private int intentos;

    @Column(nullable = false)
    private boolean usado;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    protected CodigoOtp() {
    }

    public CodigoOtp(String telefono, String codigoHash, Instant expiraEn) {
        this.telefono = telefono;
        this.codigoHash = codigoHash;
        this.expiraEn = expiraEn;
    }

    public boolean vencido(Instant ahora) {
        return ahora.isAfter(expiraEn);
    }

    public void sumarIntento() {
        intentos++;
    }

    public void marcarUsado() {
        usado = true;
    }

    public String getTelefono() { return telefono; }
    public String getCodigoHash() { return codigoHash; }
    public int getIntentos() { return intentos; }
    public boolean isUsado() { return usado; }
}
