package com.picur.demo.service;

public interface NotificacionService {

    void enviarCorreo(String destino, String asunto, String cuerpoHtml);
}