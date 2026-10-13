package com.picur.demo.service.impl;

import com.picur.demo.service.NotificacionService;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
public class NotificacionServiceImpl implements NotificacionService {

    private static final Logger log =
            LoggerFactory.getLogger(NotificacionServiceImpl.class);

    private final String remitente;
    private final JavaMailSenderImpl mailSender;

    public NotificacionServiceImpl(
            @Value("${picur.notificaciones.remitente:picur.no-reply@localhost}")
            String remitente,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.port:587}") int puerto,
            @Value("${spring.mail.username:}") String usuario,
            @Value("${spring.mail.password:}") String clave) {

        this.remitente = remitente;

        if (usuario == null || usuario.isBlank()
                || clave == null || clave.isBlank()) {
            this.mailSender = null;
            log.info("SMTP sin credenciales: los correos se simularan en la consola.");
            return;
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(puerto);
        sender.setUsername(usuario);
        sender.setPassword(clave);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        this.mailSender = sender;
    }

    @Override
    @Async("notificacionesExecutor")
    public void enviarCorreo(String destino, String asunto, String cuerpoHtml) {
        if (destino == null || destino.isBlank()) {
            return;
        }

        if (mailSender == null) {
            log.info("[CORREO simulado] para={} asunto={}\n{}",
                    destino, asunto, cuerpoHtml);
            return;
        }

        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(mensaje, true, "UTF-8");
            helper.setFrom(remitente);
            helper.setTo(destino);
            helper.setSubject(asunto);
            helper.setText(cuerpoHtml, true);
            mailSender.send(mensaje);
            log.info("Correo enviado a {}", destino);
        } catch (Exception e) {
            log.error("No se pudo enviar el correo a {}: {}",
                    destino, e.getMessage());
        }
    }
}