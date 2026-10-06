package com.picur.demo.DTO.Respo;

/**
 * Conteos globales para las tarjetas de resumen del dashboard y de alertas.
 */
public record MedicionVibracionResumenResponse(
        long total,
        long normales,
        long precaucion,
        long anomalas
) {
}