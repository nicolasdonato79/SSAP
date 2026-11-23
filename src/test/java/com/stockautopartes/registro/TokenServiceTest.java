package com.stockautopartes.registro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {

    @Test
    void elHashEsSha256EnHexYSiempreIgual() {
        String h = TokenService.hash("abc");
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h);
        assertEquals(h, TokenService.hash("abc"));
        assertNotEquals(h, TokenService.hash("abd"));
    }
}
