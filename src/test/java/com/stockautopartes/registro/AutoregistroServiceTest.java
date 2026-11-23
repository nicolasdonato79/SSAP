package com.stockautopartes.registro;

import com.stockautopartes.auth.OtpService;
import com.stockautopartes.email.EnviadorEmail;
import com.stockautopartes.usuario.Usuario;
import com.stockautopartes.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AutoregistroServiceTest {

    private UsuarioRepository usuarios;
    private OtpService otp;
    private TokenService tokens;
    private AutoregistroService servicio;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        otp = mock(OtpService.class);
        when(usuarios.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(usuarios.findByTelefono(anyString())).thenReturn(Optional.empty());
        when(usuarios.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(otp.solicitar(anyString())).thenReturn(Optional.of("123456"));
        tokens = mock(TokenService.class);
        servicio = servicio(true);
    }

    private AutoregistroService servicio(boolean verificarTelefono) {
        return new AutoregistroService(usuarios, new BCryptPasswordEncoder(4), otp,
                tokens, mock(EnviadorEmail.class), Clock.systemUTC(), "http://localhost:8080", true, verificarTelefono);
    }

    @Test
    void sinTelefonoMandaElLinkYSeActivaConElEmail() {
        when(tokens.crear(any(), any(), any())).thenReturn(Optional.of("tok"));
        AutoregistroService soloEmail = servicio(false);
        Usuario u = soloEmail.registrar(form()).usuario();
        verify(otp, never()).solicitar(anyString());
        verify(tokens).crear(any(), eq(TokenUsuario.Tipo.VERIFICAR_EMAIL), any());

        when(tokens.consumir("tok", TokenUsuario.Tipo.VERIFICAR_EMAIL)).thenReturn(Optional.of(u));
        soloEmail.confirmarEmail("tok");
        assertFalse(u.isPendienteVerificacion());
        assertFalse(u.isTelefonoVerificado());
    }

    private static RegistroForm form() {
        RegistroForm f = new RegistroForm();
        f.setNombre("Repuestos Pepe");
        f.setUsername("Pepe");
        f.setTelefono("0412-1234567");
        f.setEmail("Pepe@Mail.com");
        f.setPassword("secreta123");
        f.setRepetida("secreta123");
        return f;
    }

    @Test
    void registraPendienteYMandaElCodigo() {
        AutoregistroService.Paso paso = servicio.registrar(form());
        Usuario u = paso.usuario();
        assertEquals("pepe", u.getUsername());
        assertEquals("+584121234567", u.getTelefono());
        assertEquals("pepe@mail.com", u.getEmail());
        assertTrue(u.isPendienteVerificacion());
        assertEquals("123456", paso.paraMostrar());
        verify(otp).solicitar("+584121234567");
    }

    @Test
    void datosInvalidosDevuelvenTodosLosErrores() {
        RegistroForm f = new RegistroForm();
        f.setUsername("a");
        f.setTelefono("123");
        f.setEmail("no-es-email");
        f.setPassword("corta");
        RegistroException e = assertThrows(RegistroException.class, () -> servicio.registrar(f));
        assertEquals(5, e.getErrores().size(), e.getErrores().toString());
        verify(usuarios, never()).save(any());
    }

    @Test
    void unaCuentaActivaConElMismoEmailNoSePuedeUsar() {
        Usuario activo = new Usuario("otro", "x", "Otro", Usuario.Rol.USUARIO);
        when(usuarios.findByEmailIgnoreCase("pepe@mail.com")).thenReturn(Optional.of(activo));
        RegistroException e = assertThrows(RegistroException.class, () -> servicio.registrar(form()));
        assertTrue(e.getMessage().contains("email"));
    }

    @Test
    void unRegistroPendienteConLosMismosDatosSePisa() {
        Usuario viejo = Usuario.autoregistro("pepe", "x", "Pepe", "+584121234567", "pepe@mail.com");
        when(usuarios.findByUsernameIgnoreCase("Pepe")).thenReturn(Optional.of(viejo));
        when(usuarios.findByTelefono("+584121234567")).thenReturn(Optional.of(viejo));
        servicio.registrar(form());
        verify(usuarios).deleteAll(Set.of(viejo));
        verify(usuarios).save(any(Usuario.class));
    }
}
