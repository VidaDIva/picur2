package com.picur.demo.service;

import com.picur.demo.DTO.Respo.AsistenteResponse;
import com.picur.demo.DTO.Respo.RecomendacionResponse;
import com.picur.demo.model.MedicionVibracion;
import org.springframework.data.domain.Page;

public interface AsistenteService {

    void analizar(MedicionVibracion medicion);

    AsistenteResponse estado();

    Page<RecomendacionResponse> listarRecomendaciones(int pagina, int tamano);
}