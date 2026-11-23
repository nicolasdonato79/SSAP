package com.stockautopartes.usuario;

import com.stockautopartes.auth.TelefonoNormalizer;
import com.stockautopartes.marca.MarcaRepository;
import com.stockautopartes.ubicacion.CiudadController;
import com.stockautopartes.ubicacion.CiudadRepository;
import com.stockautopartes.web.Estados;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Valida y guarda la configuración de un usuario (la usa él mismo y el admin). */
@Service
public class PerfilService {

    private static final String PATRON_EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    public static final int MIN_PASSWORD = 6;

    private final UsuarioRepository usuarios;
    private final MarcaRepository marcas;
    private final PasswordEncoder encoder;
    private final CiudadRepository ciudades;

    public PerfilService(UsuarioRepository usuarios, MarcaRepository marcas, PasswordEncoder encoder,
                         CiudadRepository ciudades) {
        this.usuarios = usuarios;
        this.marcas = marcas;
        this.encoder = encoder;
        this.ciudades = ciudades;
    }

    /**
     * Para mostrar el formulario: si la ciudad guardada no está en el catálogo del estado,
     * el desplegable queda en "Otra" con el texto cargado.
     */
    public PerfilForm paraVista(PerfilForm f) {
        if (!f.getCiudad().isBlank() && !CiudadController.OTRA.equals(f.getCiudad())
                && (f.getEstado().isBlank() || ciudades.buscar(f.getEstado(), f.getCiudad()).isEmpty())) {
            f.setCiudadOtra(f.getCiudad());
            f.setCiudad(CiudadController.OTRA);
        }
        return f;
    }

    /** Ciudades del estado elegido, para el desplegable. */
    public List<String> ciudadesDe(String estado) {
        return estado == null || estado.isBlank() ? List.of() : ciudades.nombresDe(estado);
    }

    /**
     * @param exigirContacto true cuando lo guarda el propio usuario (teléfono, estado y ciudad obligatorios);
     *                       false cuando lo edita el admin (puede dejarlos vacíos)
     * @return lista de errores; si está vacía, los cambios quedaron guardados.
     */
    @Transactional
    public List<String> guardar(long usuarioId, PerfilForm f, boolean exigirContacto) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow();
        List<String> errores = new ArrayList<>();

        String nombre = f.getNombre().trim();
        boolean ciudadOtra = CiudadController.OTRA.equals(f.getCiudad());
        String ciudad = (ciudadOtra ? f.getCiudadOtra() : f.getCiudad()).trim();
        String estado = f.getEstado().isBlank() ? null : f.getEstado();
        boolean sinTelefono = f.getTelefono().isBlank();
        if (nombre.length() < 2 || nombre.length() > 150) errores.add("Poné un nombre.");
        if (estado == null ? exigirContacto : !Estados.TODOS.contains(estado)) errores.add("Elegí el estado.");
        if (ciudad.isEmpty() ? exigirContacto : (ciudad.length() < 2 || ciudad.length() > 80)) {
            errores.add(ciudadOtra ? "Escribí el nombre de la ciudad o pueblo." : "Elegí la ciudad o pueblo.");
        } else if (!ciudad.isEmpty() && estado != null && !ciudadOtra) {
            // tiene que ser del catálogo de ese estado; se guarda con el nombre exacto del catálogo
            Optional<String> delCatalogo = ciudades.buscar(estado, ciudad);
            if (delCatalogo.isEmpty()) {
                errores.add("Esa ciudad no es de " + estado + ". Elegila de la lista o usá \"Otra\".");
            } else {
                ciudad = delCatalogo.get();
            }
        }

        // El propio usuario no puede cambiar un teléfono o email ya verificado (el admin sí; al cambiar, deja de estar verificado)
        if (exigirContacto && u.isTelefonoVerificado()) f.setTelefono(u.getTelefono());
        if (exigirContacto && u.isEmailVerificado()) f.setEmail(u.getEmail());

        String email = f.getEmail().trim();
        Optional<String> telefono = TelefonoNormalizer.normalizar(f.getTelefono());
        if (sinTelefono && !exigirContacto) {
            // el admin puede dejarlo vacío
        } else if (telefono.isEmpty()) {
            errores.add("El teléfono no parece válido. Ej: 0412-1234567, o +54 9 11 1234-5678 si no es de Venezuela.");
        } else if (usuarios.findByTelefono(telefono.get()).filter(o -> !o.getId().equals(u.getId())).isPresent()) {
            errores.add("Ese teléfono ya lo usa otro usuario.");
        }

        if (!email.isEmpty()) {
            if (email.length() > 150 || !email.matches(PATRON_EMAIL)) {
                errores.add("El email no parece válido.");
            } else if (usuarios.findByEmailIgnoreCase(email).filter(o -> !o.getId().equals(u.getId())).isPresent()) {
                errores.add("Ese email ya lo usa otro usuario.");
            }
        }

        List<Horario> horarios = new ArrayList<>();
        for (PerfilForm.Dia d : f.getDias()) {
            if (!d.isActivo()) continue;
            try {
                LocalTime desde = LocalTime.parse(d.getDesde());
                LocalTime hasta = LocalTime.parse(d.getHasta());
                if (!hasta.isAfter(desde)) {
                    errores.add(d.getNombre() + ": la hora de cierre tiene que ser después de la de apertura.");
                } else {
                    horarios.add(new Horario(d.getDia(), desde, hasta));
                }
            } catch (DateTimeParseException | NullPointerException e) {
                errores.add(d.getNombre() + ": horario inválido.");
            }
        }

        if (!errores.isEmpty()) {
            return errores;
        }
        u.actualizarPerfil(nombre, telefono.orElse(null), email.isEmpty() ? null : email.toLowerCase(),
                estado, ciudad.isEmpty() ? null : ciudad);
        u.reemplazarMarcas(f.getMarcas().isEmpty() ? List.of() : marcas.findByIdInAndActivaTrue(f.getMarcas()));
        u.reemplazarHorarios(horarios);
        return errores;
    }

    /** Cambio de contraseña hecho por el propio usuario: pide la actual. */
    @Transactional
    public Optional<String> cambiarPassword(long usuarioId, String actual, String nueva, String repetida) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow();
        if (u.getPasswordHash() == null || !encoder.matches(actual, u.getPasswordHash())) {
            return Optional.of("La contraseña actual no es correcta.");
        }
        if (nueva.length() < MIN_PASSWORD) {
            return Optional.of("La contraseña nueva tiene que tener al menos " + MIN_PASSWORD + " caracteres.");
        }
        if (!nueva.equals(repetida)) {
            return Optional.of("Las contraseñas nuevas no coinciden.");
        }
        u.cambiarPassword(encoder.encode(nueva));
        return Optional.empty();
    }
}
