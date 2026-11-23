package com.stockautopartes.registro;

/** Datos del formulario "Crear cuenta". */
public class RegistroForm {

    private String nombre = "";
    private String username = "";
    private String telefono = "";
    private String email = "";
    private String password = "";
    private String repetida = "";

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre == null ? "" : nombre; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username == null ? "" : username; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono == null ? "" : telefono; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email == null ? "" : email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password == null ? "" : password; }
    public String getRepetida() { return repetida; }
    public void setRepetida(String repetida) { this.repetida = repetida == null ? "" : repetida; }
}
