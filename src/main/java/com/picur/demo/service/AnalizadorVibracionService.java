package com.picur.demo.service;

import com.picur.demo.enums.EstadoVibracion;

public interface AnalizadorVibracionService {

    Analisis analizar(Double rms);

    record Analisis(EstadoVibracion estado, Double indiceAnomalia) {
    }
}