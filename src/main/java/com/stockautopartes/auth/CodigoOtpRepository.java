package com.stockautopartes.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface CodigoOtpRepository extends JpaRepository<CodigoOtp, Long> {

    long countByTelefonoAndCreadoEnAfter(String telefono, Instant desde);

    Optional<CodigoOtp> findFirstByTelefonoAndUsadoFalseOrderByCreadoEnDesc(String telefono);

    long deleteByCreadoEnBefore(Instant antes);
}
