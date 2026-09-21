package com.picur.demo.service.impl;

import com.picur.demo.enums.EstadoVibracion;
import com.picur.demo.service.AnalizadorVibracionService;
import org.springframework.stereotype.Service;

@Service
public class AnalizadorVibracionServiceImpl implements AnalizadorVibracionService {

    private static final double UMBRAL_RMS_ANOMALIA_G = 0.10;

    @Override
    public Analisis analizar(Double rms) {
        if (rms == null) {
            return new Analisis(EstadoVibracion.NORMAL, 0.0);
        }

        EstadoVibracion estado = rms >= UMBRAL_RMS_ANOMALIA_G
                ? EstadoVibracion.ANOMALIA
                : EstadoVibracion.NORMAL;

        double indice = Math.min(100.0, rms / UMBRAL_RMS_ANOMALIA_G * 100.0);
        double indiceRedondeado = Math.round(indice * 100.0) / 100.0;

        return new Analisis(estado, indiceRedondeado);
    }
}