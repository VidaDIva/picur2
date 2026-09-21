package com.picur.demo.service.impl;


import com.picur.demo.DTO.Request.MedicionVibracionRequest;
import com.picur.demo.DTO.Request.MedicionVibracionSensorRequest;
import com.picur.demo.DTO.Respo.MedicionVibracionResponse;
import com.picur.demo.DTO.Respo.MedicionVibracionSensorResponse;
import com.picur.demo.exception.ApiException;
import com.picur.demo.mapper.MedicionVibracionMapper;
import com.picur.demo.mapper.MedicionVibracionSensorMapper;

import com.picur.demo.model.MedicionVibracion;
import com.picur.demo.repository.MedicionVibracionRepository;
import com.picur.demo.service.AnalizadorVibracionService;
import com.picur.demo.service.MedicionVibracionService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class MedicionVibracionServiceImpl implements MedicionVibracionService {

    private final MedicionVibracionRepository repository;
    private final AnalizadorVibracionService analizador;

    public MedicionVibracionServiceImpl(MedicionVibracionRepository repository,
            AnalizadorVibracionService analizador) {
        this.repository = repository;
        this.analizador = analizador;
    }

    @Override
    public MedicionVibracionResponse guardar(MedicionVibracionRequest dto) {

        MedicionVibracion medicion = MedicionVibracionMapper.toEntity(dto);

        aplicarAnalisis(medicion);
        medicion.setFechaRegistro(LocalDateTime.now());

        MedicionVibracion guardada = repository.save(medicion);

        return MedicionVibracionMapper.dto(guardada);
    }

    @Override
    public MedicionVibracionSensorResponse guardarDesdeSensor(
            MedicionVibracionSensorRequest request) {

        MedicionVibracion medicion =
                MedicionVibracionSensorMapper.toEntity(request);

        aplicarAnalisis(medicion);
        medicion.setFechaRegistro(sanearTimestamp(request.timestamp()));

        repository.save(medicion);

        return new MedicionVibracionSensorResponse(
                true,
                "Medición recibida correctamente",
                medicion.getFrecuencia(),
                medicion.getRms(),
                medicion.getInclinacion(),
                medicion.getEstado(),
                medicion.getIndiceAnomalia()
        );
    }

    private void aplicarAnalisis(MedicionVibracion medicion) {
        AnalizadorVibracionService.Analisis analisis =
                analizador.analizar(medicion.getRms());
        medicion.setEstado(analisis.estado());
        medicion.setIndiceAnomalia(analisis.indiceAnomalia());
    }

    private LocalDateTime sanearTimestamp(String timestampIso) {
        try {
            LocalDateTime timestamp =
                    LocalDateTime.parse(timestampIso);

            int anioActual = LocalDate.now().getYear();
            int anioRecibido = timestamp.getYear();

            if (anioRecibido >= anioActual - 3
                    && anioRecibido <= anioActual + 1) {
                return timestamp;
            }
        } catch (DateTimeParseException e) {
            // Timestamp inválido -> se usa la hora del servidor
        }

        return LocalDateTime.now();
    }

    @Override
    public List<MedicionVibracionResponse> listar() {

        return repository.findAll()
                .stream()
                .map(MedicionVibracionMapper::dto)
                .toList();
    }

    @Override
    public MedicionVibracionResponse buscarPorId(Long id) {

        MedicionVibracion medicion = repository.findById(id)
                .orElseThrow(() ->
                        new ApiException(
                                HttpStatus.NOT_FOUND,
                                "No se encontró la medición con ID: " + id
                        )
                );

        return MedicionVibracionMapper.dto(medicion);
    }

    @Override
    public void eliminar(Long id) {

        if (!repository.existsById(id)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "No se encontró la medición con ID: " + id
            );
        }

        repository.deleteById(id);
    }
}