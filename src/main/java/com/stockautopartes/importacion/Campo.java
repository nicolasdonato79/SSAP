package com.stockautopartes.importacion;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Columnas que entiende la carga de stock y los nombres con que los vendedores suelen escribirlas.
 * El encabezado se compara sin acentos, mayúsculas, espacios ni símbolos: "Precio $" = "precio".
 */
public enum Campo {

    CODIGO(true, List.of("codigo", "cod", "codigoproducto", "codproducto", "codigooem", "oem", "referencia", "ref",
            "sku", "parte", "nroparte", "nrodeparte", "nparte", "ndeparte", "numeroparte", "numerodeparte", "partnumber", "pn",
            "recambio", "pieza"),
            List.of("codigo", "cod", "numerodere", "numerore", "nrodere", "nrore", "nderecambio", "nrecambio",
                    "numerodepieza", "numeropieza", "nrodepieza", "nropieza", "ndepieza", "npieza")),
    DESCRIPCION(false, List.of("descripcion", "desc", "producto", "nombre", "articulo", "detalle", "repuesto"),
            List.of("descrip")),
    MARCA(false, List.of("marca", "fabricante", "marcarepuesto"), List.of("marca")),
    VEHICULO(false, List.of("vehiculo", "aplicacion", "modelo", "compatible", "compatibilidad", "carro", "auto"),
            List.of("vehic", "aplica")),
    CANTIDAD(true, List.of("cantidad", "cant", "stock", "existencia", "existencias", "disponible", "disponibles",
            "qty", "unidades", "und"), List.of("cant", "exist")),
    PRECIO(false, List.of("precio", "preciousd", "valor", "pvp", "precioventa", "preciodeventa", "usd", "montousd",
            "precio$", "costo", "importe"), List.of("precio", "valor")),
    /** Códigos anteriores que este código reemplaza (ej. "Reemplaza a: BK3Z-9A543-A / FB3Z-9A543-F"). */
    REEMPLAZA(false, List.of("reemplaza", "reemplazaa", "reemplazo", "reemplazos", "sustituye", "sustituyea",
            "codigoanterior", "codigosanteriores", "codanterior", "anterior", "supersede", "supersedes"),
            List.of("reempla", "sustitu")),
    /** Códigos intercambiables en los dos sentidos (ej. "Alterno", "Equivalente"). */
    EQUIVALENTES(false, List.of("equivalente", "equivalentes", "equivalencia", "equivalencias", "alterno",
            "alternos", "codigoalterno", "codigosalternos", "codalterno", "intercambiable", "intercambiables",
            "crossreference", "cruce"), List.of("equival", "altern", "intercamb"));

    private final boolean obligatorio;
    private final List<String> nombres;
    private final List<String> prefijos;

    Campo(boolean obligatorio, List<String> nombres, List<String> prefijos) {
        this.obligatorio = obligatorio;
        this.nombres = nombres;
        this.prefijos = prefijos;
    }

    public boolean obligatorio() {
        return obligatorio;
    }

    /** Nombre para mostrar en los mensajes de error. */
    public String etiqueta() {
        return switch (this) {
            case CODIGO -> "código";
            case DESCRIPCION -> "descripción";
            case MARCA -> "marca";
            case VEHICULO -> "vehículo";
            case CANTIDAD -> "cantidad";
            case PRECIO -> "precio";
            case REEMPLAZA -> "reemplaza a";
            case EQUIVALENTES -> "equivalentes";
        };
    }

    /** Qué campo representa el texto de un encabezado, si alguno. Primero nombres exactos, después prefijos. */
    public static Optional<Campo> reconocer(String encabezado) {
        String n = normalizar(encabezado);
        if (n.isEmpty()) {
            return Optional.empty();
        }
        for (Campo c : values()) {
            if (c.nombres.contains(n)) return Optional.of(c);
        }
        for (Campo c : values()) {
            for (String p : c.prefijos) {
                if (n.startsWith(p)) return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    static String normalizar(String s) {
        if (s == null) return "";
        String sinAcentos = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9$]", "");
    }
}
