package com.picur.demo.mapper;

import com.picur.demo.DTO.Request.CrearCuentaRequest;
import com.picur.demo.DTO.Respo.CrearCuentaResponse;
import com.picur.demo.model.CrearCuenta;

public class CrearCuentaMapper {

    public static CrearCuenta toEntity (CrearCuentaRequest dto){
        CrearCuenta CC = new CrearCuenta();
        CC.setNombre(dto.nombre());
        CC.setApellido(dto.apellido());
        CC.setCelular(dto.celular());
        CC.setDireccion(dto.direccion());
        CC.setFechaNacimiento(dto.fechaNacimiento());
        CC.setCorreo(dto.correo());
        CC.setContrasena(dto.contrasena());
        return CC;
    }

    public static CrearCuentaResponse dto (CrearCuenta CCs){
        return new CrearCuentaResponse(
            CCs.getId(),
            CCs.getNombre(),
            CCs.getApellido(),
            CCs.getCelular(),
            CCs.getDireccion(),
            CCs.getFechaNacimiento(),
            CCs.getCorreo()
        );
    }
    
}
