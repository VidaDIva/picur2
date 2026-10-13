package com.picur.demo.service;

/**
 * Datos que se le entregan al asistente IA para enriquecer una recomendacion.
 * Solo contiene informacion numerica agregada del sensor, sin datos personales.
 */
public record ContextoVibracion(
        String dispositivo,
        String severidad,
        String metrica,
        Double indice,
        double zMaxima,
        boolean tendenciaCreciente,
        String tituloBase,
        String recomendacionBase,
        String resumenSensores
) {
}
