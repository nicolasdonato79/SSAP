package com.stockautopartes.solicitud;

import com.stockautopartes.usuario.Usuario;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class SolicitudTest {

    private final Usuario comprador = new Usuario("jean", "x", "Jean", Usuario.Rol.USUARIO);
    private final Usuario vendedor = new Usuario("carlos", "x", "Carlos", Usuario.Rol.USUARIO);

    private Solicitud pedido(String precioPublicado) {
        return new Solicitud(comprador, vendedor, "FB3Z-9A543-J",
                precioPublicado == null ? null : new BigDecimal(precioPublicado), 2);
    }

    @Test
    void conPrecioAConsultarElVendedorOfreceYElCompradorAcepta() {
        Solicitud s = pedido(null);
        assertNull(s.getTotalUsd());
        s.ofertar(new BigDecimal("1800.00"));
        assertEquals(EstadoSolicitud.OFERTADA, s.getEstado());
        assertEquals(new BigDecimal("3600.00"), s.getTotalUsd());
        s.aceptarOferta();
        assertTrue(s.isAceptada());
        assertEquals(new BigDecimal("1800.00"), s.getPrecioVigente());
    }

    @Test
    void contraofertaAceptadaQuedaAlPrecioDelComprador() {
        Solicitud s = pedido("1850.00");
        s.ofertar(new BigDecimal("1850.00"));
        s.contraofertar(new BigDecimal("1700.00"));
        assertEquals(EstadoSolicitud.CONTRAOFERTA, s.getEstado());
        s.aceptarContraoferta();
        assertTrue(s.isAceptada());
        assertEquals(new BigDecimal("3400.00"), s.getTotalUsd());
    }

    @Test
    void contraofertaRechazadaNoEsSinStock() {
        Solicitud s = pedido("10.00");
        s.ofertar(new BigDecimal("10.00"));
        s.contraofertar(new BigDecimal("8.00"));
        s.rechazarContraoferta();
        assertEquals(EstadoSolicitud.RECHAZADA, s.getEstado());
        assertFalse(s.isSinStock());
    }

    @Test
    void sinStockCierraElPedido() {
        Solicitud s = pedido("10.00");
        s.sinStock();
        assertTrue(s.isSinStock());
        assertThrows(SolicitudException.class, s::cancelar);
    }

    @Test
    void noSePuedeSaltearPasos() {
        Solicitud s = pedido("10.00");
        assertThrows(SolicitudException.class, s::aceptarOferta);          // todavía no hay oferta
        assertThrows(SolicitudException.class, () -> s.contraofertar(BigDecimal.ONE));
        assertThrows(SolicitudException.class, s::aceptarContraoferta);
        s.ofertar(BigDecimal.TEN);
        assertThrows(SolicitudException.class, () -> s.ofertar(BigDecimal.ONE)); // ya ofertó
        s.aceptarOferta();
        assertThrows(SolicitudException.class, s::cancelar);               // trato cerrado
    }

    @Test
    void leePreciosEnFormatoLocal() {
        assertEquals(new BigDecimal("1250.50"), SolicitudService.leerPrecio("1.250,50"));
        assertEquals(new BigDecimal("35.50"), SolicitudService.leerPrecio("35,5"));
        assertThrows(SolicitudException.class, () -> SolicitudService.leerPrecio("0"));
        assertThrows(SolicitudException.class, () -> SolicitudService.leerPrecio("abc"));
    }
}
