package com.stockautopartes.marca;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.*;

/**
 * Decide a qué marca de vehículo pertenece un repuesto, con estas reglas en orden:
 * <ol>
 *   <li>Si dice para qué vehículo es: la marca o el modelo que aparezca ahí ("Ranger 2015" → Ford).</li>
 *   <li>La columna marca del Excel, si es una marca de vehículo ("FORD", "Motorcraft").</li>
 *   <li>Si el vendedor trabaja una sola marca: esa.</li>
 *   <li>La marca o el modelo que aparezca en la descripción ("Pastillas Corolla" → Toyota), si es una
 *       de las marcas del vendedor.</li>
 * </ol>
 * Si un modelo está en varias marcas, gana la que trabaja el vendedor; si no trabaja ninguna, queda sin marca.
 */
@Component
public class DetectorMarca {

    /** Otras formas de escribir algunas marcas. */
    private static final Map<String, List<String>> ALIAS = Map.of(
            "chevy", List.of("Chevrolet"),
            "vw", List.of("Volkswagen"),
            "mercedes", List.of("Mercedes-Benz"),
            "benz", List.of("Mercedes-Benz"),
            "motorcraft", List.of("Ford"),
            "acdelco", List.of("Chevrolet"),
            "mopar", List.of("Chrysler", "Jeep", "Dodge", "RAM"),
            "toyotagenuine", List.of("Toyota"));

    private static final int MAX_PALABRAS = 3;

    private final JdbcTemplate jdbc;
    private volatile Catalogo catalogo;

    @Autowired
    public DetectorMarca(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Para tests: con un catálogo armado a mano. */
    DetectorMarca(Catalogo catalogo) {
        this.jdbc = null;
        this.catalogo = catalogo;
    }

    public Integer detectar(String vehiculo, String marcaRepuesto, String descripcion, Set<Integer> marcasVendedor) {
        Catalogo c = catalogo();
        Set<Integer> delVendedor = marcasVendedor == null ? Set.of() : marcasVendedor;
        Integer r;
        if (hayTexto(vehiculo) && (r = c.enTexto(vehiculo, delVendedor, true)) != null) return r;
        if (hayTexto(marcaRepuesto) && (r = c.enTexto(marcaRepuesto, delVendedor, false)) != null) return r;
        if (delVendedor.size() == 1) return delVendedor.iterator().next();
        if (hayTexto(descripcion) && (r = c.enTexto(descripcion, delVendedor, true)) != null) {
            // En la descripción hay palabras sueltas ("mini", "seat"): solo vale una marca que el vendedor trabaje
            return delVendedor.isEmpty() || delVendedor.contains(r) ? r : null;
        }
        return null;
    }

    private static boolean hayTexto(String s) {
        return s != null && !s.isBlank();
    }

    private Catalogo catalogo() {
        Catalogo c = catalogo;
        if (c == null) {
            List<Object[]> marcas = jdbc.query("SELECT id, nombre FROM marca WHERE activa ORDER BY tipo, id",
                    (rs, i) -> new Object[]{rs.getInt(1), rs.getString(2)});
            List<Object[]> modelos = jdbc.query("SELECT marca_id, nombre FROM modelo ORDER BY id",
                    (rs, i) -> new Object[]{rs.getInt(1), rs.getString(2)});
            c = Catalogo.de(marcas, modelos);
            catalogo = c;
        }
        return c;
    }

    /** Nombres de marcas y modelos "compactados" (sin acentos, espacios ni guiones) → ids de marca. */
    static final class Catalogo {
        private final Map<String, List<Integer>> marcas = new HashMap<>();
        private final Map<String, List<Integer>> modelos = new HashMap<>();

        /** @param marcas filas {id, nombre}, autos primero. @param modelos filas {marcaId, nombre}. */
        static Catalogo de(List<Object[]> marcas, List<Object[]> modelos) {
            Catalogo c = new Catalogo();
            Map<String, List<Integer>> porNombre = new HashMap<>();
            for (Object[] m : marcas) {
                int id = (Integer) m[0];
                String nombre = (String) m[1];
                porNombre.computeIfAbsent(nombre, k -> new ArrayList<>()).add(id);
                for (String parte : nombre.split("/")) {      // "SsangYong / KGM"
                    agregar(c.marcas, compactar(parte), id);
                }
            }
            ALIAS.forEach((alias, nombres) -> nombres.forEach(n ->
                    porNombre.getOrDefault(n, List.of()).forEach(id -> agregar(c.marcas, alias, id))));
            for (Object[] m : modelos) {
                String clave = compactar((String) m[1]);
                if (clave.length() >= 3) agregar(c.modelos, clave, (Integer) m[0]);
            }
            return c;
        }

        private static void agregar(Map<String, List<Integer>> mapa, String clave, int id) {
            if (clave.isEmpty()) return;
            List<Integer> ids = mapa.computeIfAbsent(clave, k -> new ArrayList<>());
            if (!ids.contains(id)) ids.add(id);
        }

        /** Primero busca nombres de marca en el texto; si no hay, modelos. */
        Integer enTexto(String texto, Set<Integer> delVendedor, boolean conModelos) {
            List<String> palabras = palabras(texto);
            Integer r = buscar(palabras, marcas, delVendedor, false);
            if (r == null && conModelos) r = buscar(palabras, modelos, delVendedor, true);
            return r;
        }

        private static Integer buscar(List<String> palabras, Map<String, List<Integer>> mapa,
                                      Set<Integer> delVendedor, boolean ambiguoEsNulo) {
            LinkedHashSet<Integer> encontrados = new LinkedHashSet<>();
            for (int i = 0; i < palabras.size(); i++) {
                // en cada posición, la coincidencia más larga ("grand vitara" antes que "vitara")
                for (int n = Math.min(MAX_PALABRAS, palabras.size() - i); n >= 1; n--) {
                    List<Integer> ids = mapa.get(String.join("", palabras.subList(i, i + n)));
                    if (ids != null) {
                        if (!ambiguoEsNulo) return elegir(ids, delVendedor); // marca: la primera que aparece
                        encontrados.addAll(ids);
                        i += n - 1;
                        break;
                    }
                }
            }
            if (encontrados.isEmpty()) return null;
            for (Integer id : encontrados) {
                if (delVendedor.contains(id)) return id;
            }
            return encontrados.size() == 1 ? encontrados.iterator().next() : null;
        }

        /** Varias marcas con el mismo nombre (Honda autos y motos): la del vendedor, si no la primera (autos). */
        private static Integer elegir(List<Integer> ids, Set<Integer> delVendedor) {
            for (Integer id : ids) {
                if (delVendedor.contains(id)) return id;
            }
            return ids.get(0);
        }

        static List<String> palabras(String texto) {
            String s = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
            return s.isEmpty() ? List.of() : Arrays.asList(s.split(" "));
        }

        static String compactar(String nombre) {
            return String.join("", palabras(nombre));
        }
    }
}
