package com.picur.demo.repository;

import com.picur.demo.model.CodigoRecuperacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CodigoRecuperacionRepository
        extends JpaRepository<CodigoRecuperacion, Long> {

    Optional<CodigoRecuperacion> findTopByContactoAndUsadoFalseAndExpiracionAfterOrderByCreadoEnDesc(
            String contacto, LocalDateTime ahora);

    @Transactional
    @Modifying
    @Query("UPDATE CodigoRecuperacion c SET c.usado = true " +
            "WHERE c.contacto = :contacto AND c.usado = false")
    void invalidarCodigosPrevios(@Param("contacto") String contacto);
}