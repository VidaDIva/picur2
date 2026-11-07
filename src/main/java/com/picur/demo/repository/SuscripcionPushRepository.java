package com.picur.demo.repository;

import com.picur.demo.model.SuscripcionPush;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SuscripcionPushRepository
        extends JpaRepository<SuscripcionPush, Long> {

    Optional<SuscripcionPush> findByEndpoint(String endpoint);

    List<SuscripcionPush> findByUsuarioId(Long usuarioId);

    void deleteByEndpoint(String endpoint);

    void deleteByUsuarioId(Long usuarioId);
}
