package com.stockautopartes.importacion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Recibe las filas de la planilla (de cualquier formato) y arma las filas de stock.
 *
 * - Busca la fila de encabezados en las primeras 10 filas no vacías (puede haber un título arriba).
 * - Reconoce las columnas por nombre, en cualquier orden (ver {@link Campo}).
 * - Columnas obligatorias: código y cantidad. Si falta alguna, el archivo entero se rechaza.
 * - Cantidad: un número, o la palabra "consultar" (se publica como "disponibilidad a consultar").
 * - Precio: opcional. Vacío, 0 o "consultar" = "precio a consultar".
 */
public class ProcesadorFilas {

    private static final int FILAS_PARA_BUSCAR_ENCABEZADO = 10;

    private final List<FilaStock> ok = new ArrayList<>();
    private final List<String> errores = new ArrayList<>();
    private Map<Campo, Integer> columnas;
    private String errorFatal;
    private int filasNoVaciasVistas;

    /** @param numeroFila base 0, como en POI. @param celdas índice de columna → texto de la celda. */
    public void fila(int numeroFila, Map<Integer, String> celdas) {
        if (errorFatal != null || vacia(celdas)) {
            return;
        }
        if (columnas == null) {
            if (++filasNoVaciasVistas > FILAS_PARA_BUSCAR_ENCABEZADO) {
                return;
            }
            intentarEncabezado(celdas);
            return;
        }
        validar(numeroFila + 1, celdas);
    }

    public ResultadoLectura resultado() {
        if (errorFatal != null) {
            return new ResultadoLectura(List.of(), List.of(errorFatal));
        }
        if (columnas == null) {
            return new ResultadoLectura(List.of(), List.of(
                    "No encontré la fila de títulos. Tiene que tener al menos las columnas de código y cantidad."));
        }
        return new ResultadoLectura(ok, errores);
    }

    private void intentarEncabezado(Map<Integer, String> celdas) {
        Map<Campo, Integer> encontradas = new EnumMap<>(Campo.class);
        celdas.forEach((col, texto) -> Campo.reconocer(texto)
                .ifPresent(campo -> encontradas.merge(campo, col, Math::min)));
        if (encontradas.size() < 2) {
            return; // título u otra cosa: seguimos buscando
        }
        List<String> faltan = new ArrayList<>();
        for (Campo c : Campo.values()) {
            if (c.obligatorio() && !encontradas.containsKey(c)) faltan.add(c.etiqueta());
        }
        if (!faltan.isEmpty()) {
            errorFatal = "Falta la columna de " + String.join(", ", faltan)
                    + ". Columnas reconocidas: " + encontradas.keySet().stream()
                    .map(Campo::etiqueta).collect(Collectors.joining(", ")) + ".";
            return;
        }
        columnas = encontradas;
    }

    private void validar(int filaExcel, Map<Integer, String> celdas) {
        String codigo = valor(celdas, Campo.CODIGO);
        if (codigo == null) {
            errores.add("Fila " + filaExcel + ": falta el código");
            return;
        }
        String cantTexto = valor(celdas, Campo.CANTIDAD);
        Integer cantidad = null; // null = "consultar"
        if (!esConsultar(cantTexto)) {
            Optional<Integer> leida = NumeroParser.parsearCantidad(cantTexto);
            if (leida.isEmpty()) {
                errores.add("Fila " + filaExcel + " (" + codigo + "): cantidad inválida" + mostrar(cantTexto)
                        + ". Poné un número o \"consultar\".");
                return;
            }
            cantidad = leida.get();
        }
        String precioTexto = valor(celdas, Campo.PRECIO);
        BigDecimal precio = null; // null = precio a consultar
        if (precioTexto != null && !esConsultar(precioTexto)) {
            Optional<BigDecimal> leido = NumeroParser.parsear(precioTexto);
            if (leido.isEmpty() || leido.get().signum() < 0) {
                errores.add("Fila " + filaExcel + " (" + codigo + "): precio inválido" + mostrar(precioTexto)
                        + ". Poné un número, o dejalo vacío si es a consultar.");
                return;
            }
            if (leido.get().signum() > 0) {
                precio = leido.get().setScale(2, java.math.RoundingMode.HALF_UP);
            }
        }
        ok.add(new FilaStock(
                recortar(codigo, 80),
                recortar(valor(celdas, Campo.DESCRIPCION), 300),
                recortar(valor(celdas, Campo.MARCA), 80),
                recortar(valor(celdas, Campo.VEHICULO), 150),
                cantidad,
                precio,
                lista(valor(celdas, Campo.REEMPLAZA), codigo),
                lista(valor(celdas, Campo.EQUIVALENTES), codigo)));
    }

    private String valor(Map<Integer, String> celdas, Campo campo) {
        Integer col = columnas.get(campo);
        if (col == null) return null;
        String v = celdas.get(col);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    /** "A / B, C; D" → [A, B, C, D], sin vacíos ni el propio código. Separa por / , ; | y saltos de línea. */
    static List<String> lista(String texto, String propio) {
        if (texto == null) return List.of();
        String propioNorm = com.stockautopartes.inventario.CodigoNormalizer.normalizar(propio);
        List<String> codigos = new ArrayList<>();
        for (String parte : texto.split("[/,;|\\n\\r]+")) {
            String c = parte.trim();
            String norm = com.stockautopartes.inventario.CodigoNormalizer.normalizar(c);
            if (!norm.isEmpty() && !norm.equals(propioNorm) && c.length() <= 80 && codigos.size() < 20) {
                codigos.add(c);
            }
        }
        return codigos;
    }

    /** "consultar", "Consultar", "a consultar", "consulte"... */
    static boolean esConsultar(String texto) {
        if (texto == null) return false;
        String n = Campo.normalizar(texto);
        return n.startsWith("consult") || n.startsWith("aconsult");
    }

    private static boolean vacia(Map<Integer, String> celdas) {
        return celdas.values().stream().allMatch(v -> v == null || v.isBlank());
    }

    private static String mostrar(String texto) {
        return texto == null ? " (vacía)" : " (" + texto + ")";
    }

    private static String recortar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
