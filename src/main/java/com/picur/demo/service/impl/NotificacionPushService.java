package com.picur.demo.service.impl;

import com.picur.demo.DTO.Request.SuscripcionPushRequest;
import com.picur.demo.model.SuscripcionPush;
import com.picur.demo.repository.SuscripcionPushRepository;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.jwt.jose4j.JOSE4jJwtFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.http.HttpResponse;
import java.security.Security;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificacionPushService {

    private static final Logger log =
            LoggerFactory.getLogger(NotificacionPushService.class);

    private static final String TEMA = "picur-alerta";
    private static final int TTL_SEGUNDOS = 3600;

    private final SuscripcionPushRepository repository;
    private final String clavePublica;
    private final long cooldownSegundos;
    private final PushService pushService;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public NotificacionPushService(
            SuscripcionPushRepository repository,
            @Value("${picur.push.vapid-public-key:}") String clavePublica,
            @Value("${picur.push.vapid-private-key:}") String clavePrivada,
            @Value("${picur.push.subject:mailto:picur.no-reply@localhost}") String asunto,
            @Value("${picur.push.cooldown-segundos:120}") long cooldownSegundos) {

        this.repository = repository;
        this.clavePublica = clavePublica == null ? "" : clavePublica.trim();
        this.cooldownSegundos = cooldownSegundos;
        this.pushService = construirServicio(this.clavePublica, clavePrivada, asunto);
    }

    private PushService construirServicio(String publica, String privada,
                                          String asunto) {
        if (publica.isBlank() || privada == null || privada.isBlank()) {
            log.warn("Web Push deshabilitado: faltan PICUR_VAPID_PUBLIC_KEY / "
                    + "PICUR_VAPID_PRIVATE_KEY");
            return null;
        }

        try {
            return PushService.builder()
                    .withVapidPublicKey(publica)
                    .withVapidPrivateKey(privada.trim())
                    .withVapidSubject(asunto)
                    .withJwtFactory(new JOSE4jJwtFactory())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo inicializar Web Push con las claves VAPID", e);
        }
    }

    public String clavePublica() {
        return clavePublica;
    }

    public boolean estaConfigurado() {
        return pushService != null;
    }

    @Transactional
    public SuscripcionPush registrar(SuscripcionPushRequest request) {

        SuscripcionPush suscripcion = repository
                .findByEndpoint(request.endpoint())
                .orElseGet(() -> {
                    SuscripcionPush nueva = new SuscripcionPush();
                    nueva.setEndpoint(request.endpoint());
                    nueva.setFechaRegistro(LocalDateTime.now());
                    return nueva;
                });

        suscripcion.setUsuarioId(request.usuarioId());
        suscripcion.setP256dh(request.p256dh());
        suscripcion.setAuth(request.auth());
        suscripcion.setUserAgent(request.userAgent());

        return repository.save(suscripcion);
    }

    @Transactional
    public void eliminar(String endpoint) {
        repository.deleteByEndpoint(endpoint);
    }

    /**
     * Envia la alerta a todos los dispositivos suscritos. Se ejecuta desde el
     * hilo async de notificaciones, por lo que puede bloquear sin afectar la
     * ingesta del sensor.
     */
    public void enviarATodos(String titulo, String cuerpo, String url) {

        if (pushService == null) {
            log.debug("Push omitido: VAPID no configurado");
            return;
        }

        List<SuscripcionPush> suscripciones = repository.findAll();
        if (suscripciones.isEmpty()) {
            return;
        }

        String payload = crearPayload(titulo, cuerpo, url);
        LocalDateTime ahora = LocalDateTime.now();

        for (SuscripcionPush suscripcion : suscripciones) {
            if (enCooldown(suscripcion, ahora)) {
                continue;
            }
            enviar(suscripcion, payload, ahora);
        }
    }

    private void enviar(SuscripcionPush suscripcion, String payload,
                        LocalDateTime ahora) {
        try {
            Notification notificacion = Notification.builder()
                    .endpoint(suscripcion.getEndpoint())
                    .userPublicKey(suscripcion.getP256dh())
                    .userAuth(suscripcion.getAuth())
                    .payload(payload)
                    .ttl(TTL_SEGUNDOS)
                    .urgency(Notification.Urgency.HIGH)
                    .topic(TEMA)
                    .build();

            HttpResponse<Void> respuesta =
                    pushService.send(notificacion, Encoding.AES_128_GCM);

            int estado = respuesta.statusCode();

            if (estado >= 200 && estado < 300) {
                suscripcion.setUltimaNotificacion(ahora);
                repository.save(suscripcion);
            } else if (estado == 404 || estado == 410) {
                repository.delete(suscripcion);
                log.info("Suscripcion push expirada eliminada: {}",
                        suscripcion.getEndpoint());
            } else {
                log.warn("Push rechazado (HTTP {}) para {}",
                        estado, suscripcion.getEndpoint());
            }
        } catch (Exception e) {
            log.warn("No se pudo enviar push a {}: {}",
                    suscripcion.getEndpoint(), e.getMessage());
        }
    }

    private boolean enCooldown(SuscripcionPush suscripcion, LocalDateTime ahora) {
        if (cooldownSegundos <= 0 || suscripcion.getUltimaNotificacion() == null) {
            return false;
        }
        return suscripcion.getUltimaNotificacion()
                .plusSeconds(cooldownSegundos)
                .isAfter(ahora);
    }

    private String crearPayload(String titulo, String cuerpo, String url) {
        return "{\"title\":\"" + escaparJson(titulo == null ? "Picur" : titulo)
                + "\",\"body\":\""
                + escaparJson(cuerpo == null ? "Nueva alerta del sensor" : cuerpo)
                + "\",\"url\":\""
                + escaparJson(url == null || url.isBlank()
                        ? "/recomendaciones" : url)
                + "\",\"tag\":\"" + TEMA + "\"}";
    }

    private String escaparJson(String texto) {
        StringBuilder salida = new StringBuilder(texto.length() + 16);
        for (char caracter : texto.toCharArray()) {
            switch (caracter) {
                case '"' -> salida.append("\\\"");
                case '\\' -> salida.append("\\\\");
                case '\n' -> salida.append("\\n");
                case '\r' -> salida.append("\\r");
                case '\t' -> salida.append("\\t");
                default -> {
                    if (caracter < 0x20) {
                        salida.append(String.format("\\u%04x", (int) caracter));
                    } else {
                        salida.append(caracter);
                    }
                }
            }
        }
        return salida.toString();
    }
}
