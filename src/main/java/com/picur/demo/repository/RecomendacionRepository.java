package com.picur.demo.repository;

import com.picur.demo.model.Recomendacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecomendacionRepository
        extends JpaRepository<Recomendacion, Long> {

    Page<Recomendacion> findAllByOrderByFechaRegistroDesc(Pageable pageable);

    Optional<Recomendacion> findTopByOrderByFechaRegistroDesc();

    long countBySeveridad(String severidad);
}