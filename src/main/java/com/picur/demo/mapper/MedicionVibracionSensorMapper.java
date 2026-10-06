package com.picur.demo.mapper;

import com.picur.demo.DTO.Request.MedicionVibracionSensorRequest;
import com.picur.demo.model.MedicionVibracion;

public class MedicionVibracionSensorMapper {

    public static MedicionVibracion toEntity(MedicionVibracionSensorRequest request) {
        MedicionVibracion medicion = new MedicionVibracion();
        medicion.setDispositivo(request.dispositivo());
        medicion.setAceleracionX(request.aceleracionX());
        medicion.setAceleracionY(request.aceleracionY());
        medicion.setAceleracionZ(request.aceleracionZ());
        medicion.setFrecuencia(request.frecuencia());
        medicion.setRms(request.rms());
        medicion.setInclinacion(request.inclinacion());
        medicion.setVibracion(request.vibracion());
        medicion.setUmbral(request.umbral());
        return medicion;
    }
}