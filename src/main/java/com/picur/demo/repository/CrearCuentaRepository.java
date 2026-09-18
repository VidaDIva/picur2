package com.picur.demo.repository;

import com.picur.demo.model.CrearCuenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CrearCuentaRepository extends JpaRepository<CrearCuenta, Long> {

    Optional<CrearCuenta> findByCorreo(String correo);

    boolean existsByCorreo(String correo);
}