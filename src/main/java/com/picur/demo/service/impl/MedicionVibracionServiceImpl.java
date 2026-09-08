package com.picur.demo.service.impl;


import com.picur.demo.DTO.Request.MedicionVibracionRequest;
import com.picur.demo.DTO.Respo.MedicionVibracionResponse;
import com.picur.demo.enums.EstadoVibracion;
import com.picur.demo.mapper.MedicionVibracionMapper;

import com.picur.demo.model.MedicionVibracion;
import com.picur.demo.repository.MedicionVibracionRepository;
import com.picur.demo.service.MedicionVibracionService;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MedicionVibracionServiceImpl implements MedicionVibracionService {

    private final MedicionVibracionRepository repository;

    public MedicionVibracionServiceImpl(MedicionVibracionRepository repository) {
        this.repository = repository;
    }

    @Override
    public MedicionVibracionResponse guardar(MedicionVibracionRequest dto) {

        MedicionVibracion medicion = MedicionVibracionMapper.toEntity(dto);

        medicion.setEstado(EstadoVibracion.NORMAL);
        medicion.setIndiceAnomalia(0.0);
        medicion.setFechaRegistro(LocalDateTime.now());

        MedicionVibracion guardada = repository.save(medicion);

        return MedicionVibracionMapper.dto(guardada);
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
                        new RuntimeException(
                                "No se encontró la medición con ID: " + id
                        )
                );

        return MedicionVibracionMapper.dto(medicion);
    }

    @Override
    public void eliminar(Long id) {

        if (!repository.existsById(id)) {
            throw new RuntimeException(
                    "No se encontró la medición con ID: " + id
            );
        }

        repository.deleteById(id);
    }
}