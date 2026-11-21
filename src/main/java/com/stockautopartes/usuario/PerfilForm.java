package com.stockautopartes.usuario;

import com.stockautopartes.marca.Marca;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Datos del formulario de configuración (propio o editado por el admin). */
public class PerfilForm {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private String nombre = "";
    private String telefono = "";
    private String email = "";
    private String estado = "";
    /** Valor del desplegable: una ciudad del catálogo, o "__otra__" (y entonces vale ciudadOtra). */
    private String ciudad = "";
    private String ciudadOtra = "";
    private List<Integer> marcas = new ArrayList<>();
    private List<Dia> dias = new ArrayList<>();

    public PerfilForm() {
        for (int d = 1; d <= 7; d++) {
            dias.add(new Dia(d));
        }
    }

    public static PerfilForm desde(Usuario u) {
        PerfilForm f = new PerfilForm();
        f.nombre = valor(u.getNombre());
        f.telefono = valor(u.getTelefono());
        f.email = valor(u.getEmail());
        f.estado = valor(u.getEstado());
        f.ciudad = valor(u.getCiudad());
        u.getMarcas().stream().map(Marca::getId).forEach(f.marcas::add);
        for (Horario h : u.getHorarios()) {
            Dia d = f.dias.get(h.getDia() - 1);
            d.setActivo(true);
            d.setDesde(HORA.format(h.getDesde()));
            d.setHasta(HORA.format(h.getHasta()));
        }
        return f;
    }

    private static String valor(String s) {
        return s == null ? "" : s;
    }

    public static class Dia {
        private int dia;
        private boolean activo;
        private String desde = "08:00";
        private String hasta = "17:00";

        public Dia() {
        }

        Dia(int dia) {
            this.dia = dia;
        }

        public String getNombre() { return Horario.DIAS[dia]; }
        public int getDia() { return dia; }
        public void setDia(int dia) { this.dia = dia; }
        public boolean isActivo() { return activo; }
        public void setActivo(boolean activo) { this.activo = activo; }
        public String getDesde() { return desde; }
        public void setDesde(String desde) { this.desde = desde; }
        public String getHasta() { return hasta; }
        public void setHasta(String hasta) { this.hasta = hasta; }
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad == null ? "" : ciudad; }
    public String getCiudadOtra() { return ciudadOtra; }
    public void setCiudadOtra(String ciudadOtra) { this.ciudadOtra = ciudadOtra == null ? "" : ciudadOtra; }
    public List<Integer> getMarcas() { return marcas; }
    public void setMarcas(List<Integer> marcas) { this.marcas = marcas == null ? new ArrayList<>() : marcas; }
    public List<Dia> getDias() { return dias; }
    public void setDias(List<Dia> dias) { this.dias = dias; }
}
