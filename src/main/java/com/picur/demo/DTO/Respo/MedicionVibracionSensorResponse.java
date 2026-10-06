package com.picur.demo.DTO.Respo;

import com.picur.demo.enums.EstadoVibracion;

public record MedicionVibracionSensorResponse(
    boolean success,
    String message,
    Double vibracion,
    Double umbral,
    Double frecuencia,
    Double rms,
    Double inclinacion,
    EstadoVibracion estado,
    Double indiceAnomalia
) {
    
}