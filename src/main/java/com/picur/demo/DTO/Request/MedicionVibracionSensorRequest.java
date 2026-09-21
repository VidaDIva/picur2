package com.picur.demo.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MedicionVibracionSensorRequest(
    @NotBlank(message = "El dispositivo es obligatorio")
    @Size(max = 30, message = "El dispositivo no puede superar 30 caracteres")
    String dispositivo,

    @NotBlank(message = "El timestamp es obligatorio")
    String timestamp,

    @NotNull(message = "La aceleracion X es obligatoria")
    Double aceleracionX,

    @NotNull(message = "La aceleracion Y es obligatoria")
    Double aceleracionY,

    @NotNull(message = "La aceleracion Z es obligatoria")
    Double aceleracionZ,

    Double frecuencia,

    Double rms,

    Double inclinacion
) {
    
}