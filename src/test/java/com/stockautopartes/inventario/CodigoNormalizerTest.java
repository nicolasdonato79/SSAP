package com.stockautopartes.inventario;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CodigoNormalizerTest {

    @Test
    void ignoraGuionesEspaciosYMayusculas() {
        assertEquals("0446502220", CodigoNormalizer.normalizar("04465-02220"));
        assertEquals("0446502220", CodigoNormalizer.normalizar(" 04465 02220 "));
        assertEquals("ABC123", CodigoNormalizer.normalizar("abc.123"));
    }

    @Test
    void sacaAcentos() {
        assertEquals("BUJIA", CodigoNormalizer.normalizar("bujía"));
    }

    @Test
    void nuloDevuelveVacio() {
        assertEquals("", CodigoNormalizer.normalizar(null));
    }
}
