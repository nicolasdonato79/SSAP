package com.stockautopartes.auth;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelefonoNormalizerTest {

    @Test
    void formatosVenezolanos() {
        assertEquals(Optional.of("+584121234567"), TelefonoNormalizer.normalizar("0412-1234567"));
        assertEquals(Optional.of("+584121234567"), TelefonoNormalizer.normalizar("412 1234567"));
        assertEquals(Optional.of("+584121234567"), TelefonoNormalizer.normalizar("+58 412 123 4567"));
        assertEquals(Optional.of("+584121234567"), TelefonoNormalizer.normalizar("58 4121234567"));
        assertEquals(Optional.of("+582761234567"), TelefonoNormalizer.normalizar("0276-1234567"));
    }

    @Test
    void otroPaisConMas() {
        assertEquals(Optional.of("+5491158576888"), TelefonoNormalizer.normalizar("+54 9 11 5857-6888"));
    }

    @Test
    void invalidos() {
        assertEquals(Optional.empty(), TelefonoNormalizer.normalizar(""));
        assertEquals(Optional.empty(), TelefonoNormalizer.normalizar("12345"));
        assertEquals(Optional.empty(), TelefonoNormalizer.normalizar(null));
    }
}
