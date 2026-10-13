package com.picur.demo.service.impl;

import com.picur.demo.DTO.Respo.AsistenteResponse;
import com.picur.demo.DTO.Respo.RecomendacionResponse;
import com.picur.demo.mapper.RecomendacionMapper;
import com.picur.demo.model.CrearCuenta;
import com.picur.demo.model.MedicionVibracion;
import com.picur.demo.model.Recomendacion;
import com.picur.demo.repository.CrearCuentaRepository;
import com.picur.demo.repository.MedicionVibracionRepository;
import com.picur.demo.repository.RecomendacionRepository;
import com.picur.demo.service.AsistenteService;
import com.picur.demo.service.NotificacionService;
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
    private final CrearCuentaRepository usuarioRepository;
    private final NotificacionService notificaciones;

    private final Map<String, String> estadoAnterior = new ConcurrentHashMap<>();

    public AsistenteServiceImpl(MotorAprendizaje motor,
                                RecomendacionRepository recomendacionRepository,
                                MedicionVibracionRepository medicionRepository,
                                CrearCuentaRepository usuarioRepository,
                                NotificacionService notificaciones) {
        this.motor = motor;
        this.recomendacionRepository = recomendacionRepository;
        this.medicionRepository = medicionRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificaciones = notificaciones;
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
        recomendacionRepository.save(recomendacion);

        if (!MotorAprendizaje.Severidad.NORMAL.name().equals(actual)) {
            alertarUsuarios(recomendacion);
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

    private void alertarUsuarios(Recomendacion recomendacion) {

        boolean critico = SEVERIDAD_ANOMALA.equals(recomendacion.getSeveridad());
        String prefijo = critico ? "[PICUR ANOMALA] " : "[PICUR PRECAUCION] ";
        String asunto = prefijo + recomendacion.getTitulo();

        StringBuilder sms = new StringBuilder();
        sms.append(recomendacion.getTitulo()).append(". ");
        sms.append(recomendacion.getRecomendacion());
        if (recomendacion.getIndice() != null) {
            sms.append(" Indice ").append(recomendacion.getIndice())
                    .append(". Sensor ").append(recomendacion.getDispositivo()).append(".");
        }

        for (CrearCuenta usuario : usuarioRepository.findAll()) {
            notificaciones.enviarSms(usuario.getCelular(), sms.toString());
            notificaciones.enviarCorreo(usuario.getCorreo(), asunto,
                    cuerpoCorreo(usuario.getNombre(), recomendacion));
        }
    }

    private String cuerpoCorreo(String nombre, Recomendacion r) {
        boolean critico = SEVERIDAD_ANOMALA.equals(r.getSeveridad());
        String color = critico ? "#dc2626" : "#f59e0b";

        return "<div style=\"font-family:Arial,sans-serif;max-width:520px;margin:0 auto;\">"
                + "<div style=\"background:" + color + ";color:white;padding:14px 18px;"
                + "border-radius:10px 10px 0 0;font-weight:800;letter-spacing:1px;\">"
                + (critico ? "ALERTA CRITICA" : "ALERTA DE PRECAUCION") + "</div>"
                + "<div style=\"border:1px solid #e8ebf0;border-top:none;padding:18px;"
                + "border-radius:0 0 10px 10px;\">"
                + "<h2 style=\"margin:0 0 8px;color:#172033;font-size:18px;\">"
                + r.getTitulo() + "</h2>"
                + "<p style=\"margin:0 0 12px;color:#4b5563;font-size:14px;\">"
                + r.getRecomendacion() + "</p>"
                + "<p style=\"margin:0;color:#6b7280;font-size:13px;\">"
                + "Sensor: " + r.getDispositivo()
                + " · Indice: " + (r.getIndice() == null ? "-" : r.getIndice())
                + " · " + (r.getMetrica() == null ? "sin metrica especifica" : r.getMetrica())
                + "</p></div></div>";
    }

    private String dispositivo(MedicionVibracion m) {
        return m.getDispositivo() == null || m.getDispositivo().isBlank()
                ? "sensor-picur"
                : m.getDispositivo();
    }

    private record Texto(String titulo, String recomendacion) {
    }
}