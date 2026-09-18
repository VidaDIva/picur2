package com.picur.demo.service;

import com.picur.demo.DTO.Request.CrearCuentaRequest;
import com.picur.demo.DTO.Respo.CrearCuentaResponse;

import java.util.List;

public interface CrearCuentaService {

    CrearCuentaResponse crearCuenta(CrearCuentaRequest request);

    List<CrearCuentaResponse> listarUsuarios();

    CrearCuentaResponse buscarPorId(Long id);

    CrearCuentaResponse editarCuenta(
            Long id,
            CrearCuentaRequest request
    );

    void eliminarCuenta(Long id);
}

