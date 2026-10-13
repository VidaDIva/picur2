package com.picur.demo.DTO.Respo;

public record AsistenteResponse(
    String humor,
    String nivel,
    String mensaje,
    String recomendacion,
    RecomendacionResponse ultimaRecomendacion,
    Resumen resumen,
    String estadoMedicion
) {

    public record Resumen(long total, long precaucion, long anomalas) {
    }
}