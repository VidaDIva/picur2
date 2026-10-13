package com.picur.demo.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SolicitarRecuperacionRequest(
    @NotBlank(message = "El correo o celular es obligatorio")
    String contacto,

    @NotBlank(message = "Debes elegir un medio de envío")
    @Pattern(regexp = "EMAIL|SMS",
             message = "El medio debe ser EMAIL o SMS")
    String metodo
) {

}