package com.picur.demo.controller;

import com.picur.demo.DTO.Request.SuscripcionPushRequest;
import com.picur.demo.model.SuscripcionPush;
import com.picur.demo.service.impl.NotificacionPushService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final NotificacionPushService service;

    public PushController(NotificacionPushService service) {
        this.service = service;
    }

    @GetMapping("/clave-publica")
    public ResponseEntity<Map<String, String>> clavePublica() {
        if (!service.estaConfigurado()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("mensaje",
                            "Las notificaciones push no estan configuradas"));
        }
        return ResponseEntity.ok(Map.of(
                "clavePublica", service.clavePublica()));
    }

    @PostMapping("/suscripciones")
    public ResponseEntity<Map<String, Object>> suscribir(
            @Valid @RequestBody SuscripcionPushRequest request) {

        SuscripcionPush suscripcion = service.registrar(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensaje", "Suscripcion registrada",
                "id", suscripcion.getId()));
    }

    @DeleteMapping("/suscripciones")
    public ResponseEntity<Map<String, String>> eliminar(
            @RequestParam String endpoint) {

        service.eliminar(endpoint);
        return ResponseEntity.ok(Map.of(
                "mensaje", "Suscripcion eliminada"));
    }
}
