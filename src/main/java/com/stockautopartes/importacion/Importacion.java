package com.stockautopartes.importacion;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "importacion")
public class Importacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "nombre_archivo", nullable = false)
    private String nombreArchivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoImportacion estado = EstadoImportacion.PENDIENTE;

    @Column(name = "filas_ok", nullable = false)
    private int filasOk;

    @Column(name = "filas_error", nullable = false)
    private int filasError;

    @Column(columnDefinition = "TEXT")
    private String errores;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    @Column(name = "terminado_en")
    private Instant terminadoEn;

    protected Importacion() {
    }

    public Importacion(Long usuarioId, String nombreArchivo) {
        this.usuarioId = usuarioId;
        this.nombreArchivo = nombreArchivo;
    }

    public void procesando() {
        this.estado = EstadoImportacion.PROCESANDO;
    }

    public void terminar(int filasOk, int filasError, String errores) {
        this.estado = EstadoImportacion.TERMINADA;
        this.filasOk = filasOk;
        this.filasError = filasError;
        this.errores = errores;
        this.terminadoEn = Instant.now();
    }

    public void fallar(String motivo) {
        this.estado = EstadoImportacion.FALLIDA;
        this.errores = motivo;
        this.terminadoEn = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getNombreArchivo() { return nombreArchivo; }
    public EstadoImportacion getEstado() { return estado; }
    public int getFilasOk() { return filasOk; }
    public int getFilasError() { return filasError; }
    public String getErrores() { return errores; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getTerminadoEn() { return terminadoEn; }
}
