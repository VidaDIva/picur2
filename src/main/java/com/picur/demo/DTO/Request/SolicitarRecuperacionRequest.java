package com.picur.demo.DTO.Request;

import jakarta.validation.constraints.NotBlank;

public record SolicitarRecuperacionRequest(
    @NotBlank(message = "El correo es obligatorio")
    String contacto
) {

}