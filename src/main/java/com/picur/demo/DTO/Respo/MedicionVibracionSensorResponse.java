package com.picur.demo.DTO.Respo;

import com.picur.demo.enums.EstadoVibracion;

public record MedicionVibracionSensorResponse(
    boolean success,
    String message,
    Double frecuencia,
    Double rms,
    Double inclinacion,
    EstadoVibracion estado,
    Double indiceAnomalia
) {
    
}