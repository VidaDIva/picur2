package com.picur.demo.controller;

import com.picur.demo.DTO.Request.MedicionVibracionSensorRequest;
import com.picur.demo.DTO.Respo.MedicionVibracionSensorResponse;
import com.picur.demo.service.MedicionVibracionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sensores")
public class SensorController {

    private static final Logger log =
            LoggerFactory.getLogger(SensorController.class);

    private final MedicionVibracionService service;

    public SensorController(MedicionVibracionService service) {
        this.service = service;
    }

    @PostMapping("/medicion")
    public ResponseEntity<MedicionVibracionSensorResponse> recibirMedicion(
            @Valid @RequestBody MedicionVibracionSensorRequest request,
            HttpServletRequest httpRequest) {

        long inicio = System.nanoTime();

        MedicionVibracionSensorResponse respuesta =
                service.guardarDesdeSensor(request);

        long duracionMs = (System.nanoTime() - inicio) / 1_000_000;

        log.info("POST /api/sensores/medicion device={} ip={} ts={} "
                        + "estado={} vib={} umbral={} -> 201 ({} ms)",
                request.dispositivo(),
                httpRequest.getRemoteAddr(),
                request.timestamp(),
                request.estado(),
                request.vibracion(),
                request.umbral(),
                duracionMs);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(respuesta);
    }
}