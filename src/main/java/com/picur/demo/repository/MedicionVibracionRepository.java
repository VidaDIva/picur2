package com.picur.demo.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.picur.demo.enums.EstadoVibracion;
import com.picur.demo.model.MedicionVibracion;

import java.util.Collection;
import java.util.Optional;

public interface MedicionVibracionRepository extends JpaRepository<MedicionVibracion, Long> {

    Page<MedicionVibracion> findByEstadoIn(Collection<EstadoVibracion> estados, Pageable pageable);

    long countByEstado(EstadoVibracion estado);

    long countByEstadoIn(Collection<EstadoVibracion> estados);

    Optional<MedicionVibracion> findTopByOrderByFechaRegistroDesc();

    java.util.List<MedicionVibracion> findTop200ByOrderByFechaRegistroDesc();
}
