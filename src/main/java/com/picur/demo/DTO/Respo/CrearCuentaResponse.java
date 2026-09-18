package com.picur.demo.DTO.Respo;

import java.time.LocalDate;

public record CrearCuentaResponse(
    Long id, 
    String nombre, 
    String apellido, 
    String celular, 
    String direccion, 
    LocalDate fechaNacimiento, 
    String correo
) {
    
}
