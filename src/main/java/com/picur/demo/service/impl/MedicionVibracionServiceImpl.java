package com.picur.demo.service.impl;


import com.picur.demo.DTO.Request.MedicionVibracionRequest;
import com.picur.demo.DTO.Request.MedicionVibracionSensorRequest;
import com.picur.demo.DTO.Respo.MedicionVibracionResponse;
import com.picur.demo.DTO.Respo.MedicionVibracionResumenResponse;
import com.picur.demo.DTO.Respo.MedicionVibracionSensorResponse;
import com.picur.demo.enums.EstadoVibracion;
import com.picur.demo.exception.ApiException;
import com.picur.demo.mapper.MedicionVibracionMapper;
import com.picur.demo.mapper.MedicionVibracionSensorMapper;

import com.picur.demo.model.MedicionVibracion;
import com.picur.demo.repository.MedicionVibracionRepository;
import com.picur.demo.service.AnalizadorVibracionService;
import com.picur.demo.service.AsistenteService;
import com.picur.demo.service.MedicionVibracionService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class MedicionVibracionServiceImpl implements MedicionVibracionService {

    private static final String MENSAJE_GUARDADO =
            "{\"tipo\":\"MEDICION\",\"datos\":%s}";

    private final MedicionVibracionRepository repository;
    private final AnalizadorVibracionService analizador;
    private final VibracionWebSocketHandler notificador;
    private final AsistenteService asistente;

    public MedicionVibracionServiceImpl(MedicionVibracionRepository repository,
            AnalizadorVibracionService analizador,
            VibracionWebSocketHandler notificador,
            AsistenteService asistente) {
        this.repository = repository;
        this.analizador = analizador;
        this.notificador = notificador;
        this.asistente = asistente;
    }

    @Override
    public MedicionVibracionResponse guardar(MedicionVibracionRequest dto) {

        MedicionVibracion medicion = MedicionVibracionMapper.toEntity(dto);

        aplicarAnalisis(medicion);
        medicion.setFechaRegistro(LocalDateTime.now());

        MedicionVibracion guardada = repository.save(medicion);

        notificar(guardada);
        asistente.analizar(guardada);

        return MedicionVibracionMapper.dto(guardada);
    }

    @Override
    public MedicionVibracionSensorResponse guardarDesdeSensor(
            MedicionVibracionSensorRequest request) {

        MedicionVibracion medicion =
                MedicionVibracionSensorMapper.toEntity(request);

        // El analizador llena el índice de anomalía y deja un estado de
        // respaldo por si el dispositivo no envía el suyo.
        aplicarAnalisis(medicion);

        // El dispositivo es la fuente de verdad del estado cuando lo envía.
        EstadoVibracion estadoDispositivo =
                mapearEstadoDispositivo(request.estado());
        if (estadoDispositivo != null) {
            medicion.setEstado(estadoDispositivo);
        }

        medicion.setFechaRegistro(sanearTimestamp(request.timestamp()));

        repository.save(medicion);

        notificar(medicion);
        asistente.analizar(medicion);

        return new MedicionVibracionSensorResponse(
                true,
                "Medición recibida correctamente",
                medicion.getVibracion(),
                medicion.getUmbral(),
                medicion.getFrecuencia(),
                medicion.getRms(),
                medicion.getInclinacion(),
                medicion.getEstado(),
                medicion.getIndiceAnomalia()
        );
    }

    /**
     * Mapea el estado clasificado por el Pico al enum del backend:
     * NORMAL -> NORMAL, ANOMALIA -> PRECAUCION, PELIGRO -> ANOMALA.
     * Devuelve null si no viene o es desconocido (en ese caso manda
     * el analizador del servidor).
     */
    private EstadoVibracion mapearEstadoDispositivo(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        return switch (estado.trim().toUpperCase()) {
            case "NORMAL" -> EstadoVibracion.NORMAL;
            case "ANOMALIA" -> EstadoVibracion.PRECAUCION;
            case "PELIGRO" -> EstadoVibracion.ANOMALA;
            default -> null;
        };
    }

    /**
     * Publica la medición recién guardada por WebSocket: el front la pinta
     * en el acto y no necesita volver a consultar toda la lista.
     */
    private void notificar(MedicionVibracion medicion) {
        notificador.broadcast(String.format(MENSAJE_GUARDADO,
                aJson(MedicionVibracionMapper.dto(medicion))));
    }

    /**
     * Serializa la respuesta como JSON manual, igual que la expone la API
     * REST (mismo nombre de campos y formato de fecha ISO sin zona horaria).
     */
    private String aJson(MedicionVibracionResponse r) {
        return "{\"id\":" + r.id()
                + ",\"aceleracionX\":" + r.aceleracionX()
                + ",\"aceleracionY\":" + r.aceleracionY()
                + ",\"aceleracionZ\":" + r.aceleracionZ()
                + ",\"frecuencia\":" + r.frecuencia()
                + ",\"rms\":" + r.rms()
                + ",\"inclinacion\":" + r.inclinacion()
                + ",\"vibracion\":" + r.vibracion()
                + ",\"umbral\":" + r.umbral()
                + ",\"estado\":\"" + r.estado() + "\""
                + ",\"indiceAnomalia\":" + r.indiceAnomalia()
                + ",\"fechaRegistro\":\"" + r.fechaRegistro() + "\"}";
    }

    private void aplicarAnalisis(MedicionVibracion medicion) {
        AnalizadorVibracionService.Analisis analisis =
                analizador.analizar(medicion);
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
    public Page<MedicionVibracionResponse> listar(
            int pagina, int tamano, List<EstadoVibracion> estados,
            String orden) {

        Page<MedicionVibracion> paginaRegistros = estados == null
                || estados.isEmpty()
                ? repository.findAll(PageRequest.of(
                        pagina, tamano, parsearOrden(orden)))
                : repository.findByEstadoIn(estados, PageRequest.of(
                        pagina, tamano, parsearOrden(orden)));

        return paginaRegistros.map(MedicionVibracionMapper::dto);
    }

    @Override
    public MedicionVibracionResumenResponse resumen() {

        long normales = repository.countByEstado(EstadoVibracion.NORMAL);
        long precaucion = repository.countByEstado(EstadoVibracion.PRECAUCION);
        long anomalas = repository.countByEstado(EstadoVibracion.ANOMALA)
                + repository.countByEstado(EstadoVibracion.ANOMALIA);

        return new MedicionVibracionResumenResponse(
                repository.count(), normales, precaucion, anomalas);
    }

    private Sort parsearOrden(String orden) {
        if (orden == null || orden.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "fechaRegistro")
                    .and(Sort.by(Sort.Direction.DESC, "id"));
        }

        String[] partes = orden.split(",");
        String propiedad = partes[0].trim();

        Sort.Direction direccion;
        if (partes.length > 1) {
            try {
                direccion = Sort.Direction.fromString(partes[1].trim());
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Dirección de orden inválida: " + partes[1]);
            }
        } else {
            direccion = Sort.Direction.DESC;
        }

        return Sort.by(direccion, propiedad);
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