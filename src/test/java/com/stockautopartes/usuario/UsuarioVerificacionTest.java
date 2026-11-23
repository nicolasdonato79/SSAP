package com.stockautopartes.usuario;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UsuarioVerificacionTest {

    @Test
    void elAutoregistroQuedaPendienteHastaVerificarTelefonoYEmail() {
        Usuario u = Usuario.autoregistro("pepe", "hash", "Pepe", "+584121234567", "pepe@mail.com");
        assertTrue(u.isPendienteVerificacion());
        u.marcarTelefonoVerificado();
        assertTrue(u.isPendienteVerificacion());
        u.marcarEmailVerificado();
        assertFalse(u.isPendienteVerificacion());
    }

    @Test
    void cambiarTelefonoOEmailLosDejaSinVerificar() {
        Usuario u = Usuario.autoregistro("pepe", "hash", "Pepe", "+584121234567", "pepe@mail.com");
        u.marcarTelefonoVerificado();
        u.marcarEmailVerificado();

        u.actualizarPerfil("Pepe", "+584121234567", "PEPE@mail.com", "Táchira", "Rubio"); // mismos datos
        assertTrue(u.isTelefonoVerificado());
        assertTrue(u.isEmailVerificado());

        u.actualizarPerfil("Pepe", "+584149999999", "otro@mail.com", "Táchira", "Rubio");
        assertFalse(u.isTelefonoVerificado());
        assertFalse(u.isEmailVerificado());
        assertFalse(u.isPendienteVerificacion()); // ya activada: sigue pudiendo entrar
    }
}
