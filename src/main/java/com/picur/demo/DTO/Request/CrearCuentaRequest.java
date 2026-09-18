package com.picur.demo.DTO.Request;

import java.time.LocalDate;

public record CrearCuentaRequest(
    String nombre,
    String apellido,
    String celular,
    String direccion,
    LocalDate fechaNacimiento,
    String correo,
    String contrasena
) {
    
}