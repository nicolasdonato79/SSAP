package com.stockautopartes.importacion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProcesadorFilasTest {

    private static Map<Integer, String> fila(String... valores) {
        Map<Integer, String> m = new HashMap<>();
        for (int i = 0; i < valores.length; i++) {
            if (valores[i] != null) m.put(i, valores[i]);
        }
        return m;
    }

    @Test
    void reconoceEncabezadosConOtrosNombresYEnOtroOrden() {
        ProcesadorFilas p = new ProcesadorFilas();
        p.fila(0, fila("LISTA DE PRECIOS NOVIEMBRE"));
        p.fila(2, fila("Descripción", "Ref", "Existencia", "Precio $", "Marca"));
        p.fila(3, fila("Pastillas", "04465-02220", "4", "35,50", "Toyota"));

        ResultadoLectura r = p.resultado();
        assertTrue(r.errores().isEmpty());
        assertEquals(1, r.filasOk().size());
        FilaStock f = r.filasOk().get(0);
        assertEquals("04465-02220", f.codigo());
        assertEquals(4, f.cantidad());
        assertEquals(new BigDecimal("35.50"), f.precioUsd());
        assertEquals("Toyota", f.marca());
    }

    @Test
    void descripcionEsOpcional() {
        ProcesadorFilas p = new ProcesadorFilas();
        p.fila(0, fila("Código", "Cantidad", "Precio"));
        p.fila(1, fila("ABC-1", "2", "10"));
        FilaStock f = p.resultado().filasOk().get(0);
        assertNull(f.descripcion());
    }

    @Test
    void filasInvalidasVanALosErrores() {
        ProcesadorFilas p = new ProcesadorFilas();
        p.fila(0, fila("Código", "Cantidad", "Precio"));
        p.fila(1, fila("A", "x", "10"));      // cantidad inválida
        p.fila(2, fila("B", "1", "abc"));     // precio inválido
        p.fila(3, fila(null, "1", "10"));     // sin código
        p.fila(4, fila("C", "", "10"));       // cantidad vacía (solo vale un número o "consultar")
        ResultadoLectura r = p.resultado();
        assertEquals(0, r.filasOk().size());
        assertEquals(4, r.errores().size());
    }

    @Test
    void precioVacioYCantidadConsultarSonValidos() {
        ProcesadorFilas p = new ProcesadorFilas();
        p.fila(0, fila("Código", "Existencia", "Precio $"));
        p.fila(1, fila("A", "consultar", "10"));
        p.fila(2, fila("B", "3", ""));
        p.fila(3, fila("C", "A consultar", "Consultar"));
        p.fila(4, fila("D", "2", "0"));
        ResultadoLectura r = p.resultado();
        assertTrue(r.errores().isEmpty(), r.errores().toString());
        assertEquals(4, r.filasOk().size());
        assertNull(r.filasOk().get(0).cantidad());
        assertEquals(new BigDecimal("10.00"), r.filasOk().get(0).precioUsd());
        assertEquals(3, r.filasOk().get(1).cantidad());
        assertNull(r.filasOk().get(1).precioUsd());
        assertNull(r.filasOk().get(2).cantidad());
        assertNull(r.filasOk().get(2).precioUsd());
        assertNull(r.filasOk().get(3).precioUsd());
    }

    @Test
    void laColumnaDePrecioEsOpcional() {
        ProcesadorFilas p = new ProcesadorFilas();
        p.fila(0, fila("Código", "Descripción", "Cantidad"));
        p.fila(1, fila("A", "b", "1"));
        ResultadoLectura r = p.resultado();
        assertEquals(1, r.filasOk().size());
        assertNull(r.filasOk().get(0).precioUsd());
    }

    @Test
    void sinColumnaDeCantidadSeRechazaElArchivo() {
        ProcesadorFilas p = new ProcesadorFilas();
        p.fila(0, fila("Código", "Descripción", "Precio"));
        p.fila(1, fila("A", "b", "10"));
        ResultadoLectura r = p.resultado();
        assertTrue(r.filasOk().isEmpty());
        assertTrue(r.errores().get(0).contains("cantidad"));
    }

    @Test
    void leeReemplazosYEquivalentes() {
        ProcesadorFilas p = new ProcesadorFilas();
        p.fila(0, fila("Número de recambio", "Descripción", "Cant", "Precio", "Reemplaza a", "Alterno"));
        p.fila(1, fila("FB3Z-9A543-J", "BOMBA DE INYECCIÓN", "1", "1.850,00",
                "BK3Z-9A543-A / FB3Z-9A543-A; FB3Z-9A543-F / fb3z9a543j", "0445010511"));
        FilaStock f = p.resultado().filasOk().get(0);
        assertEquals("FB3Z-9A543-J", f.codigo());
        assertEquals(new BigDecimal("1850.00"), f.precioUsd());
        // el propio código no se cuenta como reemplazo de sí mismo
        assertEquals(java.util.List.of("BK3Z-9A543-A", "FB3Z-9A543-A", "FB3Z-9A543-F"), f.reemplaza());
        assertEquals(java.util.List.of("0445010511"), f.equivalentes());
    }

    @Test
    void reconoceTitulosDeCatalogo() {
        assertEquals(Optional.of(Campo.CODIGO), Campo.reconocer("Número de recambio"));
        assertEquals(Optional.of(Campo.CODIGO), Campo.reconocer("Nº de pieza"));
        assertEquals(Optional.of(Campo.EQUIVALENTES), Campo.reconocer("Cód. alterno"));
        assertEquals(Optional.of(Campo.REEMPLAZA), Campo.reconocer("Reemplaza a"));
    }

    @Test
    void numerosEnFormatoLocal() {
        assertEquals(Optional.of(new BigDecimal("1234.56")), NumeroParser.parsear("1.234,56"));
        assertEquals(Optional.of(new BigDecimal("1234.56")), NumeroParser.parsear("1,234.56"));
        assertEquals(Optional.of(new BigDecimal("35.50")), NumeroParser.parsear("35,50"));
        assertEquals(Optional.of(new BigDecimal("1500")), NumeroParser.parsear("1.500"));
        assertEquals(Optional.of(10), NumeroParser.parsearCantidad("10 und"));
        assertEquals(Optional.empty(), NumeroParser.parsearCantidad("2,5"));
    }
}
