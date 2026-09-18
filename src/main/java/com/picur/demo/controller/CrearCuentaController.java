package com.picur.demo.controller;

import com.picur.demo.DTO.Request.CrearCuentaRequest;
import com.picur.demo.DTO.Respo.CrearCuentaResponse;
import com.picur.demo.service.CrearCuentaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "http://localhost:4321")
public class CrearCuentaController {

    private final CrearCuentaService service;

    public CrearCuentaController(CrearCuentaService service) {
        this.service = service;
    }


    // CREAR CUENTA
    @PostMapping
    public ResponseEntity<CrearCuentaResponse> crearCuenta(
            @Valid @RequestBody CrearCuentaRequest request) {

        CrearCuentaResponse response =
                service.crearCuenta(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // LISTAR TODOS LOS USUARIOS
    @GetMapping
    public ResponseEntity<List<CrearCuentaResponse>> listarUsuarios() {

        List<CrearCuentaResponse> usuarios =
                service.listarUsuarios();

        return ResponseEntity.ok(usuarios);
    }


    // BUSCAR USUARIO POR ID
    @GetMapping("/{id}")
    public ResponseEntity<CrearCuentaResponse> buscarPorId(
            @PathVariable Long id) {

        CrearCuentaResponse response =
                service.buscarPorId(id);

        return ResponseEntity.ok(response);
    }


    // EDITAR USUARIO
    @PutMapping("/{id}")
    public ResponseEntity<CrearCuentaResponse> editarCuenta(
            @PathVariable Long id,
            @Valid @RequestBody CrearCuentaRequest request) {

        CrearCuentaResponse response =
                service.editarCuenta(id, request);

        return ResponseEntity.ok(response);
    }


    // ELIMINAR USUARIO
    @DeleteMapping("/{id}")
    public ResponseEntity<String> eliminarCuenta(
            @PathVariable Long id) {

        service.eliminarCuenta(id);

        return ResponseEntity.ok(
                "Usuario eliminado correctamente"
        );
    }
}

