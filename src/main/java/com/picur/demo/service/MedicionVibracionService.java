package com.picur.demo.service;

import java.util.List;

import com.picur.demo.DTO.Request.MedicionVibracionRequest;
import com.picur.demo.DTO.Request.MedicionVibracionSensorRequest;
import com.picur.demo.DTO.Respo.MedicionVibracionResponse;
import com.picur.demo.DTO.Respo.MedicionVibracionSensorResponse;

public interface MedicionVibracionService {
    
MedicionVibracionResponse guardar(MedicionVibracionRequest request);

    MedicionVibracionSensorResponse guardarDesdeSensor(MedicionVibracionSensorRequest request);

    List<MedicionVibracionResponse> listar();

    MedicionVibracionResponse buscarPorId(Long id);

    void eliminar(Long id);
    
}
