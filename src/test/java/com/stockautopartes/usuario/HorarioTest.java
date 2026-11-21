package com.stockautopartes.usuario;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HorarioTest {

    private static final LocalTime H8 = LocalTime.of(8, 0);
    private static final LocalTime H12 = LocalTime.of(12, 0);
    private static final LocalTime H17 = LocalTime.of(17, 0);

    @Test
    void agrupaDiasSeguidosConElMismoHorario() {
        List<Horario> h = List.of(new Horario(1, H8, H17), new Horario(2, H8, H17), new Horario(3, H8, H17),
                new Horario(4, H8, H17), new Horario(5, H8, H17), new Horario(6, H8, H12));
        assertEquals("Lun–Vie 8:00–17:00 · Sáb 8:00–12:00", Horario.resumen(h));
    }

    @Test
    void noAgrupaDiasSalteados() {
        List<Horario> h = List.of(new Horario(3, H8, H17), new Horario(1, H8, H17));
        assertEquals("Lun 8:00–17:00 · Mié 8:00–17:00", Horario.resumen(h));
    }

    @Test
    void sinHorariosEsVacio() {
        assertEquals("", Horario.resumen(List.of()));
    }
}
