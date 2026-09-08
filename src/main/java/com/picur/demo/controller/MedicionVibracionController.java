package com.picur.demo.controller;


import com.picur.demo.DTO.Request.MedicionVibracionRequest;
import com.picur.demo.DTO.Respo.MedicionVibracionResponse;
import com.picur.demo.service.MedicionVibracionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mediciones")
@CrossOrigin(origins = "*")
public class MedicionVibracionController {

    private final MedicionVibracionService service;

    public MedicionVibracionController(MedicionVibracionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MedicionVibracionResponse> guardar(
            @RequestBody MedicionVibracionRequest dto) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.guardar(dto));
    }

    @GetMapping
    public ResponseEntity<List<MedicionVibracionResponse>> listar() {

        return ResponseEntity.ok(service.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MedicionVibracionResponse> buscarPorId(
            @PathVariable Long id) {

        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long id) {

        service.eliminar(id);

        return ResponseEntity.noContent().build();
    }
}