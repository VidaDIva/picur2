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

    Double inclinacion,

    /** Índice de vibración calculado por el dispositivo (opcional: firmware antiguo no lo envía). */
    Double vibracion,

    /** Umbral dinámico aprendido por el dispositivo (opcional). */
    Double umbral,

    /** Estado clasificado por el dispositivo: NORMAL | ANOMALIA | PELIGRO (opcional). */
    String estado
) {
    
}