package com.stockautopartes.importacion;

public enum EstadoImportacion {
    PENDIENTE,
    PROCESANDO,
    TERMINADA,
    FALLIDA;

    public boolean esFinal() {
        return this == TERMINADA || this == FALLIDA;
    }
}
