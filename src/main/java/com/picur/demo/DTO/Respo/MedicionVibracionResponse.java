package com.picur.demo.DTO.Respo;

import java.time.LocalDateTime;

import com.picur.demo.enums.EstadoVibracion;

public record MedicionVibracionResponse(
    Long id,
    Double aceleracionX,
    Double aceleracionY,
    Double aceleracionZ,
    Double frecuencia,
    Double rms,
    Double inclinacion,
    EstadoVibracion estado,
    Double indiceAnomalia,
    LocalDateTime fechaRegistro

) {
    
}
