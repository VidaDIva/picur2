package com.picur.demo.service;

import com.picur.demo.DTO.Request.RestablecerContrasenaRequest;
import com.picur.demo.DTO.Request.SolicitarRecuperacionRequest;
import com.picur.demo.DTO.Respo.MensajeResponse;

public interface RecuperacionContrasenaService {

    MensajeResponse solicitar(SolicitarRecuperacionRequest request);

    MensajeResponse restablecer(RestablecerContrasenaRequest request);
}