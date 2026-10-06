package com.picur.demo.service;

import com.picur.demo.enums.EstadoVibracion;
import com.picur.demo.model.MedicionVibracion;

/**
 * Motor de detección de anomalías.
 *
 * La implementación actual clasifica con rangos y puntuación, pero está
 * pensada para que en el futuro se complemente o reemplace por un modelo
 * de Machine Learning (p. ej. Isolation Forest) sin tocar el resto del
 * sistema.
 */
public interface AnalizadorVibracionService {

    Analisis analizar(MedicionVibracion medicion);

    record Analisis(EstadoVibracion estado, Double indiceAnomalia) {
    }
}