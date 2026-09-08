package com.picur.demo.DTO.Request;

public record MedicionVibracionRequest(
    Double aceleracionX,
    Double aceleracionY,
    Double aceleracionZ,
    Double frecuencia,
    Double rms,
    Double inclinacion
) {
    
}
