package com.stockautopartes.sync;

/**
 * Cómo terminó una operación mandada por el equipo.
 * <pre>
 * APLICADA                se hizo
 * APLICADA_CON_CONFLICTO  se hizo, pero pisó un cambio hecho desde otro equipo (avisar al usuario)
 * CONFLICTO               no se hizo: alguien cambió lo mismo antes (el equipo baja el estado actual y avisa)
 * RECHAZADA               no se hizo: datos inválidos, no es suyo, etc. Reintentarla no sirve
 * ERROR                   falla del servidor: no quedó registrada, el equipo la reintenta más tarde
 * </pre>
 * Todas menos ERROR quedan guardadas: si la operación llega de nuevo se contesta lo mismo.
 */
public enum Resultado {
    APLICADA,
    APLICADA_CON_CONFLICTO,
    CONFLICTO,
    RECHAZADA,
    ERROR
}
