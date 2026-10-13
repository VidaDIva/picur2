package com.picur.demo.service.impl;

import com.picur.demo.model.CrearCuenta;
import com.picur.demo.model.Recomendacion;
import com.picur.demo.repository.CrearCuentaRepository;
import com.picur.demo.repository.RecomendacionRepository;
import com.picur.demo.service.AsistenteIaService;
import com.picur.demo.service.ContextoVibracion;
import com.picur.demo.service.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Enriquece una recomendacion ya guardada con el asistente IA (si esta
 * disponible) y envia los correos de alerta. Se ejecuta de forma asincrona
 * para no frenar la ingesta de mediciones del sensor.
 */
@Service
public class NotificacionRecomendacionService {

    private static final Logger log =
            LoggerFactory.getLogger(NotificacionRecomendacionService.class);

    private static final String SEVERIDAD_ANOMALA = "ANOMALA";

    private final RecomendacionRepository recomendacionRepository;
    private final CrearCuentaRepository usuarioRepository;
    private final NotificacionService notificaciones;
    private final AsistenteIaService asistenteIa;

    public NotificacionRecomendacionService(
            RecomendacionRepository recomendacionRepository,
            CrearCuentaRepository usuarioRepository,
            NotificacionService notificaciones,
            AsistenteIaService asistenteIa) {
        this.recomendacionRepository = recomendacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificaciones = notificaciones;
        this.asistenteIa = asistenteIa;
    }

    @Async("notificacionesExecutor")
    public void procesar(Long recomendacionId, ContextoVibracion contexto) {

        Recomendacion recomendacion = recomendacionRepository
                .findById(recomendacionId)
                .orElse(null);

        if (recomendacion == null) {
            return;
        }

        asistenteIa.enriquecer(contexto).ifPresent(texto -> {
            recomendacion.setRecomendacion(texto);
            recomendacionRepository.save(recomendacion);
            log.info("Recomendacion {} enriquecida con IA", recomendacionId);
        });

        alertarUsuarios(recomendacion);
    }

    private void alertarUsuarios(Recomendacion recomendacion) {

        boolean critico = SEVERIDAD_ANOMALA.equals(recomendacion.getSeveridad());
        String prefijo = critico ? "[PICUR ANOMALA] " : "[PICUR PRECAUCION] ";
        String asunto = prefijo + recomendacion.getTitulo();

        for (CrearCuenta usuario : usuarioRepository.findAll()) {
            notificaciones.enviarCorreo(usuario.getCorreo(), asunto,
                    cuerpoCorreo(recomendacion));
        }
    }

    private String cuerpoCorreo(Recomendacion r) {
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
}
