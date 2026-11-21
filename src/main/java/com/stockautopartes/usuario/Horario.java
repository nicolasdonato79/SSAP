package com.stockautopartes.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Horario de atención de un día (1 = lunes ... 7 = domingo). */
@Embeddable
public class Horario {

    static final String[] DIAS = {"", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
    private static final String[] DIAS_CORTOS = {"", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("H:mm");

    @Column(nullable = false)
    private int dia;

    @Column(nullable = false)
    private LocalTime desde;

    @Column(nullable = false)
    private LocalTime hasta;

    protected Horario() {
    }

    public Horario(int dia, LocalTime desde, LocalTime hasta) {
        this.dia = dia;
        this.desde = desde;
        this.hasta = hasta;
    }

    public int getDia() { return dia; }
    public LocalTime getDesde() { return desde; }
    public LocalTime getHasta() { return hasta; }

    /**
     * Resumen legible, agrupando días seguidos con el mismo horario:
     * "Lun–Vie 8:00–17:00 · Sáb 8:00–12:00". Vacío si no cargó horarios.
     */
    public static String resumen(List<Horario> horarios) {
        List<Horario> orden = new ArrayList<>(horarios);
        orden.sort(Comparator.comparingInt(Horario::getDia));
        List<String> partes = new ArrayList<>();
        int i = 0;
        while (i < orden.size()) {
            Horario inicio = orden.get(i);
            int j = i;
            while (j + 1 < orden.size()
                    && orden.get(j + 1).dia == orden.get(j).dia + 1
                    && Objects.equals(orden.get(j + 1).desde, inicio.desde)
                    && Objects.equals(orden.get(j + 1).hasta, inicio.hasta)) {
                j++;
            }
            String dias = (i == j) ? DIAS_CORTOS[inicio.dia]
                    : DIAS_CORTOS[inicio.dia] + "–" + DIAS_CORTOS[orden.get(j).dia];
            partes.add(dias + " " + HORA.format(inicio.desde) + "–" + HORA.format(inicio.hasta));
            i = j + 1;
        }
        return String.join(" · ", partes);
    }
}
