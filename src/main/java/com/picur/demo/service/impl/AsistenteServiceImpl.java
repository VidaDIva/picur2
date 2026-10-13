package com.picur.demo.service.impl;

import com.picur.demo.DTO.Respo.AsistenteResponse;
import com.picur.demo.DTO.Respo.RecomendacionResponse;
import com.picur.demo.mapper.RecomendacionMapper;
import com.picur.demo.model.MedicionVibracion;
import com.picur.demo.model.Recomendacion;
import com.picur.demo.repository.MedicionVibracionRepository;
import com.picur.demo.repository.RecomendacionRepository;
import com.picur.demo.service.AsistenteService;
import com.picur.demo.service.ContextoVibracion;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AsistenteServiceImpl implements AsistenteService {

    private static final int TAMANO_SEMILLA = 200;
    private static final String SEVERIDAD_PRECAUCION = "PRECAUCION";
    private static final String SEVERIDAD_ANOMALA = "ANOMALA";

    private final MotorAprendizaje motor;
    private final RecomendacionRepository recomendacionRepository;
    private final MedicionVibracionRepository medicionRepository;
    private final NotificacionRecomendacionService notificador;

    private final Map<String, String> estadoAnterior = new ConcurrentHashMap<>();

    public AsistenteServiceImpl(MotorAprendizaje motor,
                                RecomendacionRepository recomendacionRepository,
                                MedicionVibracionRepository medicionRepository,
                                NotificacionRecomendacionService notificador) {
        this.motor = motor;
        this.recomendacionRepository = recomendacionRepository;
        this.medicionRepository = medicionRepository;
        this.notificador = notificador;
    }

    @PostConstruct
    public void aprenderHistorico() {
        List<MedicionVibracion> historico =
                medicionRepository.findTop200ByOrderByFechaRegistroDesc();
        for (int i = historico.size() - 1; i >= 0; i--) {
            motor.aprender(historico.get(i));
        }
    }

    @Override
    public void analizar(MedicionVibracion medicion) {

        motor.aprender(medicion);

        double indice = medicion.getIndiceAnomalia() == null
                ? 0.0
                : medicion.getIndiceAnomalia();

        MotorAprendizaje.Resultado resultado = motor.evaluar(medicion, indice);

        String dispositivo = dispositivo(medicion);
        String actual = resultado.severidad().name();
        String previo = estadoAnterior.getOrDefault(dispositivo, "DESCONOCIDO");

        if (actual.equals(previo)) {
            return;
        }

        estadoAnterior.put(dispositivo, actual);

        Recomendacion recomendacion = construir(medicion, resultado);
        recomendacion = recomendacionRepository.save(recomendacion);

        if (!MotorAprendizaje.Severidad.NORMAL.name().equals(actual)) {
            notificador.procesar(recomendacion.getId(),
                    contexto(medicion, resultado, recomendacion));
        }
    }

    @Override
    public AsistenteResponse estado() {

        Optional<Recomendacion> ultima =
                recomendacionRepository.findTopByOrderByFechaRegistroDesc();

        long total = recomendacionRepository.count();
        long precaucion = recomendacionRepository.countBySeveridad(SEVERIDAD_PRECAUCION);
        long anomalas = recomendacionRepository.countBySeveridad(SEVERIDAD_ANOMALA);

        String estadoMedicion = medicionRepository.findTopByOrderByFechaRegistroDesc()
                .map(m -> m.getEstado() == null ? "SIN_DATOS" : m.getEstado().name())
                .orElse("SIN_DATOS");

        String nivel = normalizarNivel(estadoMedicion);
        String humor = humor(nivel);
        String recomendacion = recomendacionNivel(nivel);
        String mensaje = mensaje(nivel, ultima, recomendacion);

        return new AsistenteResponse(
                humor,
                nivel,
                mensaje,
                recomendacion,
                ultima.map(RecomendacionMapper::dto).orElse(null),
                new AsistenteResponse.Resumen(total, precaucion, anomalas),
                estadoMedicion
        );
    }

    @Override
    public Page<RecomendacionResponse> listarRecomendaciones(int pagina, int tamano) {
        return recomendacionRepository
                .findAllByOrderByFechaRegistroDesc(PageRequest.of(pagina, tamano))
                .map(RecomendacionMapper::dto);
    }

    private String normalizarNivel(String estadoMedicion) {
        String estado = estadoMedicion == null
                ? "SIN_DATOS"
                : estadoMedicion.toUpperCase(Locale.ROOT);
        if (estado.equals(SEVERIDAD_ANOMALA) || estado.equals("ANOMALIA")) {
            return SEVERIDAD_ANOMALA;
        }
        if (estado.equals(SEVERIDAD_PRECAUCION)) {
            return SEVERIDAD_PRECAUCION;
        }
        if (estado.equals("NORMAL")) {
            return "NORMAL";
        }
        return "SIN_DATOS";
    }

    private String humor(String nivel) {
        return switch (nivel) {
            case SEVERIDAD_ANOMALA -> "alerta";
            case SEVERIDAD_PRECAUCION -> "atenta";
            case "NORMAL" -> "tranquila";
            default -> "espera";
        };
    }

    private String recomendacionNivel(String nivel) {
        return switch (nivel) {
            case "NORMAL" -> "Nivel normal: los valores se mantienen en el rango "
                    + "aprendido. Manten el monitoreo periodico.";
            case SEVERIDAD_PRECAUCION -> "Nivel de precaucion: hay valores por "
                    + "encima de lo normal. Revisa el montaje del sensor y observa "
                    + "las proximas lecturas.";
            case SEVERIDAD_ANOMALA -> "Nivel anomalo: los valores se alejan del "
                    + "historico. Inspecciona el sensor y la estructura cuanto antes.";
            default -> "Todavia no hay mediciones. Cuando lleguen te recomendare "
                    + "segun el nivel detectado.";
        };
    }

    private String mensaje(String nivel, Optional<Recomendacion> ultima,
                           String recomendacion) {
        boolean hayAlerta = nivel.equals(SEVERIDAD_ANOMALA)
                || nivel.equals(SEVERIDAD_PRECAUCION);
        if (hayAlerta && ultima.isPresent()) {
            Recomendacion r = ultima.get();
            return r.getTitulo() + " " + r.getRecomendacion();
        }
        return recomendacion;
    }

    private Recomendacion construir(MedicionVibracion medicion,
                                    MotorAprendizaje.Resultado resultado) {

        Texto texto = texto(resultado);

        Recomendacion recomendacion = new Recomendacion();
        recomendacion.setDispositivo(dispositivo(medicion));
        recomendacion.setSeveridad(resultado.severidad().name());
        recomendacion.setTitulo(texto.titulo());
        recomendacion.setRecomendacion(texto.recomendacion());
        recomendacion.setMetrica(resultado.metrica());
        recomendacion.setIndice(medicion.getIndiceAnomalia());
        recomendacion.setFechaRegistro(LocalDateTime.now());
        return recomendacion;
    }

    private Texto texto(MotorAprendizaje.Resultado resultado) {

        boolean critico = resultado.severidad() == MotorAprendizaje.Severidad.ANOMALA;
        String metrica = resultado.metrica() == null ? "" : resultado.metrica();

        if (resultado.severidad() == MotorAprendizaje.Severidad.NORMAL) {
            return new Texto("Nivel normal",
                    "El comportamiento se mantiene en el rango aprendido. "
                            + "Manten el monitoreo periodico.");
        }

        String titulo;
        String recomendacion;

        switch (metrica) {
            case "vibracion" -> {
                titulo = critico
                        ? "Vibracion muy superior a lo aprendido"
                        : "Vibracion por encima de lo normal";
                recomendacion = critico
                        ? "Revisa el apriete del sensor y del soporte: un montaje flojo amplifica la vibracion."
                        : "Mantén el monitoreo y verifica el montaje antes de que la vibracion siga subiendo.";
            }
            case "rms" -> {
                titulo = critico
                        ? "Energia de vibracion critica"
                        : "Energia de vibracion elevada";
                recomendacion = critico
                        ? "La energia RMS esta muy por encima de lo normal: inspecciona el equipo y reduce la carga si es posible."
                        : "La energia RMS esta subiendo. Revisa el estado de apoyo y anclajes.";
            }
            case "inclinacion" -> {
                titulo = critico
                        ? "Inclinacion fuera del comportamiento aprendido"
                        : "Inclinacion ligeramente anormal";
                recomendacion = critico
                        ? "Comprueba el nivel y la fijacion de la estructura: puede haber un desplazamiento."
                        : "Comprueba el nivel del soporte para confirmar que no haya un desplazamiento incipiente.";
            }
            case "frecuencia" -> {
                titulo = critico
                        ? "Cambio fuerte en la frecuencia dominante"
                        : "Cambio en la frecuencia dominante";
                recomendacion = critico
                        ? "Un cambio de frecuencia puede indicar perdida de rigidez: programa una inspeccion del soporte."
                        : "La frecuencia se movio respecto de lo aprendido. Valida el sensor con una prueba manual.";
            }
            case "aceleracionX", "aceleracionY", "aceleracionZ" -> {
                titulo = critico
                        ? "Aceleracion anomala en el eje " + metrica.substring(11)
                        : "Aceleracion por encima de lo normal en el eje " + metrica.substring(11);
                recomendacion = critico
                        ? "Revisa las condiciones del entorno y los soportes: hay impactos o vibraciones que no se venian registrando."
                        : "Observa las lecturas de ese eje durante los proximos minutos.";
            }
            default -> {
                titulo = critico
                        ? "Comportamiento anomalo detectado"
                        : "Desviacion leve del comportamiento normal";
                recomendacion = critico
                        ? "Observa las lecturas, valida el sensor con una prueba manual y considera una revision presencial."
                        : "Segui monitoreando; si la desviacion se repite, conviene revisar el sensor.";
            }
        }

        if (resultado.tendenciaCreciente()) {
            titulo = "Tendencia creciente · " + titulo;
            recomendacion = recomendacion
                    + " El valor viene subiendo en las ultimas lecturas: actua pronto.";
        }

        return new Texto(titulo, recomendacion);
    }

    private ContextoVibracion contexto(MedicionVibracion medicion,
                                       MotorAprendizaje.Resultado resultado,
                                       Recomendacion recomendacion) {
        return new ContextoVibracion(
                dispositivo(medicion),
                resultado.severidad().name(),
                resultado.metrica(),
                medicion.getIndiceAnomalia(),
                resultado.zMaxima(),
                resultado.tendenciaCreciente(),
                recomendacion.getTitulo(),
                recomendacion.getRecomendacion(),
                resumenSensores(medicion));
    }

    private String resumenSensores(MedicionVibracion m) {
        StringBuilder sb = new StringBuilder();
        agregar(sb, "vibracion", m.getVibracion());
        agregar(sb, "rms", m.getRms());
        agregar(sb, "frecuencia", m.getFrecuencia());
        agregar(sb, "inclinacion", m.getInclinacion());
        agregar(sb, "aceleracionX", m.getAceleracionX());
        agregar(sb, "aceleracionY", m.getAceleracionY());
        agregar(sb, "aceleracionZ", m.getAceleracionZ());
        return sb.toString();
    }

    private void agregar(StringBuilder sb, String nombre, Double valor) {
        if (valor == null) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(nombre).append('=').append(valor);
    }

    private String dispositivo(MedicionVibracion m) {
        return m.getDispositivo() == null || m.getDispositivo().isBlank()
                ? "sensor-picur"
                : m.getDispositivo();
    }

    private record Texto(String titulo, String recomendacion) {
    }
}
