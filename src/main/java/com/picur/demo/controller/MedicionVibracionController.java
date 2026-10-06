package com.picur.demo.controller;


import com.picur.demo.DTO.Request.MedicionVibracionRequest;
import com.picur.demo.DTO.Respo.MedicionVibracionResponse;
import com.picur.demo.DTO.Respo.MedicionVibracionResumenResponse;
import com.picur.demo.enums.EstadoVibracion;
import com.picur.demo.service.MedicionVibracionService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/mediciones")
@CrossOrigin(origins = "http://localhost:4321")
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

    @GetMapping("/resumen")
    public ResponseEntity<MedicionVibracionResumenResponse> resumen() {
        return ResponseEntity.ok(service.resumen());
    }

    @GetMapping
    public ResponseEntity<Page<MedicionVibracionResponse>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "fechaRegistro,desc") String sort) {

        List<EstadoVibracion> estados = parsearEstados(estado);

        return ResponseEntity.ok(service.listar(page, size, estados, sort));
    }

    private List<EstadoVibracion> parsearEstados(String estado) {
        if (estado == null || estado.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.stream(estado.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toUpperCase)
                    .map(EstadoVibracion::valueOf)
                    .toList();
        } catch (IllegalArgumentException e) {
            throw new com.picur.demo.exception.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Valor de estado inválido: " + estado);
        }
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