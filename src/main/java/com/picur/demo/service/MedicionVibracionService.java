package com.picur.demo.service;

import java.util.List;

import com.picur.demo.DTO.Request.MedicionVibracionRequest;
import com.picur.demo.DTO.Request.MedicionVibracionSensorRequest;
import com.picur.demo.DTO.Respo.MedicionVibracionResponse;
import com.picur.demo.DTO.Respo.MedicionVibracionResumenResponse;
import com.picur.demo.DTO.Respo.MedicionVibracionSensorResponse;
import com.picur.demo.enums.EstadoVibracion;

import org.springframework.data.domain.Page;

public interface MedicionVibracionService {
    
MedicionVibracionResponse guardar(MedicionVibracionRequest request);

    MedicionVibracionSensorResponse guardarDesdeSensor(MedicionVibracionSensorRequest request);

    Page<MedicionVibracionResponse> listar(
            int pagina, int tamano, List<EstadoVibracion> estados, String orden);

    MedicionVibracionResumenResponse resumen();

    MedicionVibracionResponse buscarPorId(Long id);

    void eliminar(Long id);
    
}