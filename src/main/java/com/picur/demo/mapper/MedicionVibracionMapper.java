package com.picur.demo.mapper;

import com.picur.demo.DTO.Request.MedicionVibracionRequest;
import com.picur.demo.DTO.Respo.MedicionVibracionResponse;
import com.picur.demo.model.MedicionVibracion;

public class MedicionVibracionMapper {

    public static MedicionVibracion toEntity (MedicionVibracionRequest dto){
        MedicionVibracion medir = new MedicionVibracion();
        medir.setAceleracionX(dto.aceleracionX());
        medir.setAceleracionY(dto.aceleracionY());
        medir.setAceleracionZ(dto.aceleracionZ());
        medir.setFrecuencia(dto.frecuencia());
        medir.setRms(dto.rms());
        medir.setInclinacion(dto.inclinacion());
        return medir;
    }
    
    public static MedicionVibracionResponse dto (MedicionVibracion  medicones){
        return new MedicionVibracionResponse(
            medicones.getId(),
            medicones.getAceleracionX(),
            medicones.getAceleracionY(),
            medicones.getAceleracionZ(),
            medicones.getFrecuencia(),
            medicones.getRms(),
            medicones.getInclinacion(),
            medicones.getEstado(),
            medicones.getIndiceAnomalia(),
            medicones.getFechaRegistro()
        ); 
    }

}
