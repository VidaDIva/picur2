package com.picur.demo.config;

import com.picur.demo.service.impl.VibracionWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final VibracionWebSocketHandler handler;
    private final String[] origenesPermitidos;

    public WebSocketConfig(
            VibracionWebSocketHandler handler,
            @Value("${picur.websocket.allowed-origins:http://localhost:4321}")
            String origenesPermitidos) {
        this.handler = handler;
        this.origenesPermitidos = Arrays.stream(origenesPermitidos.split(","))
                .map(String::trim)
                .filter(origen -> !origen.isBlank())
                .toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .setAllowedOrigins(origenesPermitidos);
    }
}
