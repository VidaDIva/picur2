package com.picur.demo.service;

import java.util.List;

import com.picur.demo.DTO.Request.MedicionVibracionRequest;
import com.picur.demo.DTO.Respo.MedicionVibracionResponse;

public interface MedicionVibracionService {
    
MedicionVibracionResponse guardar(MedicionVibracionRequest request);

    List<MedicionVibracionResponse> listar();

    MedicionVibracionResponse buscarPorId(Long id);

    void eliminar(Long id);
    
}
