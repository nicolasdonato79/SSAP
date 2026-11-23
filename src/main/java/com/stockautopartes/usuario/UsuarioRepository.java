package com.stockautopartes.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByTelefono(String telefono);

    Optional<Usuario> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    Optional<Usuario> findByEmailIgnoreCase(String email);

    List<Usuario> findAllByOrderByUsernameAsc();

    /** Registros que nunca terminaron de verificarse (para borrarlos). */
    List<Usuario> findByPendienteVerificacionTrueAndCreadoEnBefore(Instant antes);
}
