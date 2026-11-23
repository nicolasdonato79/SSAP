package com.stockautopartes.marca;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DetectorMarcaTest {

    private static final int FORD = 1, TOYOTA = 2, CHEVROLET = 3, SUZUKI = 4, HONDA = 5, HONDA_MOTOS = 6;

    private final DetectorMarca detector = new DetectorMarca(DetectorMarca.Catalogo.de(
            List.of(new Object[]{FORD, "Ford"}, new Object[]{TOYOTA, "Toyota"}, new Object[]{CHEVROLET, "Chevrolet"},
                    new Object[]{SUZUKI, "Suzuki"}, new Object[]{HONDA, "Honda"}, new Object[]{HONDA_MOTOS, "Honda"}),
            List.of(new Object[]{FORD, "Ranger"}, new Object[]{FORD, "F-150"}, new Object[]{TOYOTA, "Corolla"},
                    new Object[]{CHEVROLET, "Grand Vitara"}, new Object[]{SUZUKI, "Grand Vitara"},
                    new Object[]{SUZUKI, "Vitara"})));

    @Test
    void porElModeloDelVehiculo() {
        assertEquals(FORD, detector.detectar("Ranger 2012-2019", null, "Disco de freno", Set.of(TOYOTA)));
        assertEquals(FORD, detector.detectar("F150 Fortaleza", null, null, Set.of()));
        assertEquals(TOYOTA, detector.detectar("COROLLA 1.8", null, null, Set.of(FORD, TOYOTA)));
    }

    @Test
    void porLaMarcaEscritaEnElVehiculoOEnLaColumnaMarca() {
        assertEquals(TOYOTA, detector.detectar("Toyota todos", null, null, Set.of(FORD)));
        assertEquals(FORD, detector.detectar(null, "MOTORCRAFT", null, Set.of(FORD, TOYOTA)));
    }

    @Test
    void sinModeloUsaLaMarcaDelVendedorSiTrabajaUna() {
        assertEquals(FORD, detector.detectar(null, null, "Pastillas Corolla", Set.of(FORD)));
        assertEquals(FORD, detector.detectar("", "Bosch", "Filtro de aceite", Set.of(FORD)));
    }

    @Test
    void multimarcaSinModeloMiraLaDescripcion() {
        assertEquals(TOYOTA, detector.detectar(null, null, "Pastillas Corolla", Set.of(FORD, TOYOTA)));
        assertNull(detector.detectar(null, null, "Filtro de aceite", Set.of(FORD, TOYOTA)));
        assertNull(detector.detectar(null, null, "Bujía Honda", Set.of(FORD, TOYOTA)));
    }

    @Test
    void modeloEnDosMarcasGanaLaDelVendedor() {
        assertEquals(CHEVROLET, detector.detectar("Grand Vitara", null, null, Set.of(CHEVROLET, FORD)));
        assertEquals(SUZUKI, detector.detectar("Grand Vitara", null, null, Set.of(SUZUKI)));
        assertNull(detector.detectar("Grand Vitara", null, null, Set.of(FORD, TOYOTA)));
    }

    @Test
    void mismaMarcaEnAutosYMotos() {
        assertEquals(HONDA, detector.detectar("Honda Civic", null, null, Set.of()));
        assertEquals(HONDA_MOTOS, detector.detectar("Honda", null, null, Set.of(HONDA_MOTOS)));
    }
}
