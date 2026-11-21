package com.stockautopartes.inventario;

/**
 * Relación entre dos números de pieza, con los códigos tal cual los escribió el usuario.
 * REEMPLAZO: {@code anterior} fue reemplazado por {@code nuevo}.
 * EQUIVALENTE: son intercambiables (el orden no importa).
 */
public record RelacionCodigo(Tipo tipo, String anterior, String nuevo) {

    public enum Tipo { REEMPLAZO, EQUIVALENTE }

    public static RelacionCodigo reemplazo(String anterior, String nuevo) {
        return new RelacionCodigo(Tipo.REEMPLAZO, anterior, nuevo);
    }

    public static RelacionCodigo equivalente(String a, String b) {
        return new RelacionCodigo(Tipo.EQUIVALENTE, a, b);
    }
}
