package com.stockautopartes.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Ayuda para paginar. En las vistas: {@code @paginas.numeros(pagina)} para el paginador
 * (fragmentos/paginacion). Las páginas se numeran desde 1 en la URL.
 */
@Component("paginas")
public class Paginas {

    /** Cuántos por página puede elegir el usuario. */
    public static final List<Integer> TAMANIOS = List.of(10, 20, 30);
    public static final int TAMANIO_POR_DEFECTO = 10;

    /** Página pedida en la URL (desde 1; cualquier cosa menor es la 1). */
    public static PageRequest pedir(int pagina, int tamanio) {
        return PageRequest.of(Math.max(pagina, 1) - 1, tamanio);
    }

    /** Igual, pero el tamaño viene de la URL: si no es 10, 20 o 30 se usa 10. */
    public static PageRequest pedirElegido(int pagina, int tamanio) {
        return pedir(pagina, TAMANIOS.contains(tamanio) ? tamanio : TAMANIO_POR_DEFECTO);
    }

    public List<Integer> tamanios() {
        return TAMANIOS;
    }

    /**
     * Números a mostrar: la primera, la última y dos a cada lado de la actual; 0 = "…".
     * Ej. en la 7 de 20: 1 0 5 6 7 8 9 0 20.
     */
    public List<Integer> numeros(Page<?> pagina) {
        int total = pagina.getTotalPages();
        int actual = pagina.getNumber() + 1;
        List<Integer> lista = new ArrayList<>();
        for (int n = 1; n <= total; n++) {
            if (n == 1 || n == total || Math.abs(n - actual) <= 2) {
                lista.add(n);
            } else if (!lista.isEmpty() && lista.get(lista.size() - 1) != 0) {
                lista.add(0);
            }
        }
        // Un "…" que tapa una sola página no ahorra nada: se muestra el número (1 … 3 → 1 2 3)
        for (int i = 1; i < lista.size() - 1; i++) {
            if (lista.get(i) == 0 && lista.get(i + 1) - lista.get(i - 1) == 2) {
                lista.set(i, lista.get(i - 1) + 1);
            }
        }
        return lista;
    }
}
