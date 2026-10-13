package com.picur.demo.DTO.Respo;

import java.time.LocalDateTime;

public record RecomendacionResponse(
    Long id,
    String dispositivo,
    String severidad,
    String titulo,
    String recomendacion,
    String metrica,
    Double indice,
    LocalDateTime fechaRegistro
) {

}