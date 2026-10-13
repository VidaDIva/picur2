package com.picur.demo.mapper;

import com.picur.demo.DTO.Respo.RecomendacionResponse;
import com.picur.demo.model.Recomendacion;

public class RecomendacionMapper {

    public static RecomendacionResponse dto(Recomendacion r) {
        return new RecomendacionResponse(
                r.getId(),
                r.getDispositivo(),
                r.getSeveridad(),
                r.getTitulo(),
                r.getRecomendacion(),
                r.getMetrica(),
                r.getIndice(),
                r.getFechaRegistro()
        );
    }
}