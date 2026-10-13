package com.picur.demo.controller;

import com.picur.demo.DTO.Respo.AsistenteResponse;
import com.picur.demo.DTO.Respo.RecomendacionResponse;
import com.picur.demo.service.AsistenteService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/asistente")
@CrossOrigin(origins = "http://localhost:4321")
public class AsistenteController {

    private final AsistenteService service;

    public AsistenteController(AsistenteService service) {
        this.service = service;
    }

    @GetMapping("/estado")
    public ResponseEntity<AsistenteResponse> estado() {
        return ResponseEntity.ok(service.estado());
    }

    @GetMapping("/recomendaciones")
    public ResponseEntity<Page<RecomendacionResponse>> recomendaciones(
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "10") int tamano) {

        return ResponseEntity.ok(service.listarRecomendaciones(pagina, tamano));
    }
}