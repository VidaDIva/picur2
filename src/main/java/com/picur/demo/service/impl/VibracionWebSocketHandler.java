package com.picur.demo.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canal unidireccional hacia el front: cada sesión conectada recibe las
 * mediciones nuevas en cuanto se guardan en la base de datos.
 */
@Component
public class VibracionWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log =
            LoggerFactory.getLogger(VibracionWebSocketHandler.class);

    private final Set<WebSocketSession> sesiones =
            ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sesiones.add(session);
        log.debug("WebSocket conectado id={} sesiones={}",
                session.getId(), sesiones.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
            CloseStatus status) {
        sesiones.remove(session);
        log.debug("WebSocket cerrado id={} status={} sesiones={}",
                session.getId(), status, sesiones.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session,
            Throwable exception) throws Exception {
        sesiones.remove(session);
        log.warn("Error de transporte WebSocket id={}: {}",
                session.getId(), exception.getMessage());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    public void broadcast(String json) {
        TextMessage mensaje = new TextMessage(json);
        for (WebSocketSession sesion : sesiones) {
            try {
                if (sesion.isOpen()) {
                    sesion.sendMessage(mensaje);
                }
            } catch (IOException e) {
                sesiones.remove(sesion);
                log.warn("No se pudo enviar por WebSocket id={}: {}",
                        sesion.getId(), e.getMessage());
            }
        }
    }
}