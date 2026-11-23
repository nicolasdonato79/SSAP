package com.stockautopartes.usuario;

import com.stockautopartes.marca.Marca;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "usuario")
public class Usuario {

    public enum Rol { USUARIO, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol = Rol.USUARIO;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(nullable = false, length = 150)
    private String nombre;

    /** Formato +58..., se completa en el perfil. Se usa para el contacto por WhatsApp. */
    @Column(unique = true, length = 30)
    private String telefono;

    @Column(length = 60)
    private String estado;

    @Column(length = 80)
    private String ciudad;

    @Column(length = 150)
    private String email;

    /** Marcas de vehículos que trabaja (vacío = no vende, o todavía no las cargó). */
    @ManyToMany
    @JoinTable(name = "usuario_marca",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "marca_id"))
    private Set<Marca> marcas = new HashSet<>();

    /** Horario de atención, un rango por día. */
    @ElementCollection
    @CollectionTable(name = "usuario_horario", joinColumns = @JoinColumn(name = "usuario_id"))
    private List<Horario> horarios = new ArrayList<>();

    @Column(nullable = false)
    private boolean verificado;

    @Column(name = "telefono_verificado", nullable = false)
    private boolean telefonoVerificado;

    @Column(name = "email_verificado", nullable = false)
    private boolean emailVerificado;

    /** Se registró solo y todavía no verificó teléfono y email: no puede entrar. */
    @Column(name = "pendiente_verificacion", nullable = false)
    private boolean pendienteVerificacion;

    @Column(name = "ultima_carga")
    private Instant ultimaCarga;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn = Instant.now();

    protected Usuario() {
    }

    public Usuario(String username, String passwordHash, String nombre, Rol rol) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.nombre = nombre;
        this.rol = rol;
    }

    /** Alta hecha por el propio usuario desde /registro: queda pendiente hasta verificar teléfono y email. */
    public static Usuario autoregistro(String username, String passwordHash, String nombre,
                                       String telefono, String email) {
        Usuario u = new Usuario(username, passwordHash, nombre, Rol.USUARIO);
        u.telefono = telefono;
        u.email = email;
        u.pendienteVerificacion = true;
        return u;
    }

    public void marcarTelefonoVerificado() {
        telefonoVerificado = true;
        activarSiCorresponde();
    }

    public void marcarEmailVerificado() {
        emailVerificado = true;
        activarSiCorresponde();
    }

    /** Alta sin verificación de teléfono: con el email verificado alcanza para activar la cuenta. */
    public void activarConEmailVerificado() {
        if (pendienteVerificacion && emailVerificado) {
            pendienteVerificacion = false;
        }
    }

    private void activarSiCorresponde() {
        if (pendienteVerificacion && telefonoVerificado && emailVerificado) {
            pendienteVerificacion = false;
        }
    }

    /** Teléfono, estado y ciudad cargados: puede publicar stock y hacer pedidos. */
    public boolean isPerfilCompleto() {
        return telefono != null && estado != null && ciudad != null;
    }

    public boolean isAdmin() {
        return rol == Rol.ADMIN;
    }

    /** Link directo a WhatsApp, sin el "+" ni espacios; null si no cargó teléfono. */
    public String getLinkWhatsapp() {
        return telefono == null ? null : "https://wa.me/" + telefono.replaceAll("\\D", "");
    }

    public void actualizarPerfil(String nombre, String telefono, String email, String estado, String ciudad) {
        this.nombre = nombre;
        // Si cambia el teléfono o el email, deja de estar verificado
        if (!Objects.equals(this.telefono, telefono)) telefonoVerificado = false;
        if (email == null ? this.email != null : !email.equalsIgnoreCase(String.valueOf(this.email))) emailVerificado = false;
        this.telefono = telefono;
        this.email = email;
        this.estado = estado;
        this.ciudad = ciudad;
    }

    public void reemplazarMarcas(Collection<Marca> nuevas) {
        marcas.clear();
        marcas.addAll(nuevas);
    }

    public void reemplazarHorarios(Collection<Horario> nuevos) {
        horarios.clear();
        horarios.addAll(nuevos);
    }

    /** Marcas ordenadas por nombre. */
    public List<Marca> getMarcasOrdenadas() {
        List<Marca> lista = new ArrayList<>(marcas);
        lista.sort(Comparator.comparing(Marca::getEtiqueta, String.CASE_INSENSITIVE_ORDER));
        return lista;
    }

    /** "Ford · Toyota", o "Multimarca (12 marcas)" si son muchas; vacío si no cargó. */
    public String getMarcasResumen() {
        if (marcas.isEmpty()) return "";
        if (marcas.size() > 3) return "Multimarca (" + marcas.size() + " marcas)";
        return String.join(" · ", getMarcasOrdenadas().stream().map(Marca::getEtiqueta).toList());
    }

    public String getHorarioResumen() {
        return Horario.resumen(horarios);
    }

    public void cambiarPassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public void setUltimaCarga(Instant ultimaCarga) { this.ultimaCarga = ultimaCarga; }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Rol getRol() { return rol; }
    public boolean isActivo() { return activo; }
    public String getNombre() { return nombre; }
    public String getTelefono() { return telefono; }
    public String getEstado() { return estado; }
    public String getCiudad() { return ciudad; }
    public String getEmail() { return email; }
    public Set<Marca> getMarcas() { return marcas; }
    public List<Horario> getHorarios() { return horarios; }
    public boolean isVerificado() { return verificado; }
    public boolean isTelefonoVerificado() { return telefonoVerificado; }
    public boolean isEmailVerificado() { return emailVerificado; }
    public boolean isPendienteVerificacion() { return pendienteVerificacion; }
    public Instant getUltimaCarga() { return ultimaCarga; }
    public Instant getCreadoEn() { return creadoEn; }
}
