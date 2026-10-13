package com.picur.demo.controller;

import com.picur.demo.DTO.Request.RestablecerContrasenaRequest;
import com.picur.demo.DTO.Request.SolicitarRecuperacionRequest;
import com.picur.demo.DTO.Respo.MensajeResponse;
import com.picur.demo.service.RecuperacionContrasenaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios/recuperar")
@CrossOrigin(origins = "http://localhost:4321")
public class RecuperacionContrasenaController {

    private final RecuperacionContrasenaService service;

    public RecuperacionContrasenaController(RecuperacionContrasenaService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MensajeResponse> solicitar(
            @Valid @RequestBody SolicitarRecuperacionRequest request) {

        return ResponseEntity.ok(service.solicitar(request));
    }

    @PostMapping("/restablecer")
    public ResponseEntity<MensajeResponse> restablecer(
            @Valid @RequestBody RestablecerContrasenaRequest request) {

        return ResponseEntity.ok(service.restablecer(request));
    }
}