package com.picur.demo.controller;

import com.picur.demo.DTO.Request.MedicionVibracionSensorRequest;
import com.picur.demo.DTO.Respo.MedicionVibracionSensorResponse;
import com.picur.demo.service.MedicionVibracionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sensores")
@CrossOrigin(origins = "http://localhost:4321")
public class SensorController {

    private final MedicionVibracionService service;

    public SensorController(MedicionVibracionService service) {
        this.service = service;
    }

    @PostMapping("/medicion")
    public ResponseEntity<MedicionVibracionSensorResponse> recibirMedicion(
            @Valid @RequestBody MedicionVibracionSensorRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.guardarDesdeSensor(request));
    }
}