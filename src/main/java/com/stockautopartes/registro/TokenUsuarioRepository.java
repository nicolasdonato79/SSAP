package com.stockautopartes.registro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface TokenUsuarioRepository extends JpaRepository<TokenUsuario, Long> {

    Optional<TokenUsuario> findByTokenHashAndTipo(String tokenHash, TokenUsuario.Tipo tipo);

    long countByUsuario_IdAndTipoAndCreadoEnAfter(Long usuarioId, TokenUsuario.Tipo tipo, Instant desde);

    long deleteByExpiraEnBefore(Instant antes);
}
