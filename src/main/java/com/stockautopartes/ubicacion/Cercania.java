package com.stockautopartes.ubicacion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Qué tan cerca está un vendedor del comprador, para mostrarlo en los resultados.
 * Usa la ubicación aproximada de cada estado (tabla estado_ubicacion, la misma con la que se ordena).
 * En las vistas: {@code @cercania.texto(estadoVendedor, ciudadVendedor, estadoComprador, ciudadComprador)}.
 */
@Component("cercania")
public class Cercania {

    private final JdbcTemplate jdbc;
    private volatile Map<String, double[]> ubicaciones;

    public Cercania(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** "En tu ciudad", "En tu estado", "A unos 350 km", o null si falta algún dato. */
    public String texto(String estadoVendedor, String ciudadVendedor, String estadoComprador, String ciudadComprador) {
        if (estadoVendedor == null || estadoComprador == null) return null;
        if (estadoVendedor.equals(estadoComprador)) {
            return ciudadVendedor != null && ciudadVendedor.equalsIgnoreCase(ciudadComprador)
                    ? "En tu ciudad" : "En tu estado";
        }
        double[] a = ubicaciones().get(estadoVendedor);
        double[] b = ubicaciones().get(estadoComprador);
        if (a == null || b == null) return null;
        long km = Math.round(distanciaKm(a[0], a[1], b[0], b[1]) / 10.0) * 10;
        return "A unos " + km + " km";
    }

    /**
     * Estado cuya capital está más cerca del punto (la ubicación que compartió un visitante sin cuenta).
     * null si el punto está a más de 400 km de todas (fuera de Venezuela).
     */
    public String estadoMasCercano(double lat, double lon) {
        String mejor = null;
        double minimo = 400;
        for (Map.Entry<String, double[]> e : ubicaciones().entrySet()) {
            double d = distanciaKm(lat, lon, e.getValue()[0], e.getValue()[1]);
            if (d < minimo) {
                minimo = d;
                mejor = e.getKey();
            }
        }
        return mejor;
    }

    static double distanciaKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * 6371 * Math.asin(Math.sqrt(h));
    }

    private Map<String, double[]> ubicaciones() {
        Map<String, double[]> m = ubicaciones;
        if (m == null) {
            Map<String, double[]> nuevo = new HashMap<>();
            jdbc.query("SELECT estado, lat, lon FROM estado_ubicacion",
                    rs -> { nuevo.put(rs.getString(1), new double[]{rs.getDouble(2), rs.getDouble(3)}); });
            ubicaciones = m = nuevo;
        }
        return m;
    }
}
