package com.picur.demo.service.impl;

import com.picur.demo.DTO.Request.RestablecerContrasenaRequest;
import com.picur.demo.DTO.Request.SolicitarRecuperacionRequest;
import com.picur.demo.DTO.Respo.MensajeResponse;
import com.picur.demo.exception.ApiException;
import com.picur.demo.model.CodigoRecuperacion;
import com.picur.demo.model.CrearCuenta;
import com.picur.demo.repository.CodigoRecuperacionRepository;
import com.picur.demo.repository.CrearCuentaRepository;
import com.picur.demo.service.NotificacionService;
import com.picur.demo.service.RecuperacionContrasenaService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class RecuperacionContrasenaServiceImpl
        implements RecuperacionContrasenaService {

    private static final int DURACION_MINUTOS = 10;
    private static final int MAX_INTENTOS = 5;
    private static final int LONGITUD_CODIGO = 6;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final CrearCuentaRepository usuarioRepository;
    private final CodigoRecuperacionRepository codigoRepository;
    private final NotificacionService notificaciones;
    private final PasswordEncoder passwordEncoder;

    public RecuperacionContrasenaServiceImpl(
            CrearCuentaRepository usuarioRepository,
            CodigoRecuperacionRepository codigoRepository,
            NotificacionService notificaciones,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.codigoRepository = codigoRepository;
        this.notificaciones = notificaciones;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public MensajeResponse solicitar(SolicitarRecuperacionRequest request) {

        CrearCuenta usuario = buscarUsuario(request.contacto());

        String destino = usuario.getCorreo();
        codigoRepository.invalidarCodigosPrevios(destino);

        String codigo = generarCodigo();

        CodigoRecuperacion entidad = new CodigoRecuperacion();
        entidad.setContacto(destino);
        entidad.setMetodo("EMAIL");
        entidad.setCodigoHash(passwordEncoder.encode(codigo));
        entidad.setExpiracion(LocalDateTime.now().plusMinutes(DURACION_MINUTOS));
        entidad.setUsado(false);
        entidad.setIntentos(0);
        entidad.setCreadoEn(LocalDateTime.now());
        codigoRepository.save(entidad);

        notificaciones.enviarCorreo(destino,
                "Recuperacion de contrasena - Picur",
                cuerpoCorreo(usuario.getNombre(), codigo));

        return new MensajeResponse(
                "Codigo enviado a " + enmascarar(destino)
                        + ". Vence en " + DURACION_MINUTOS + " minutos.");
    }

    @Override
    public MensajeResponse restablecer(RestablecerContrasenaRequest request) {

        CrearCuenta usuario = buscarUsuario(request.contacto());

        CodigoRecuperacion codigo = codigoRepository
                .findTopByContactoAndUsadoFalseAndExpiracionAfterOrderByCreadoEnDesc(
                        usuario.getCorreo(), LocalDateTime.now())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Codigo invalido o expirado. Solicita uno nuevo."));

        if (codigo.getIntentos() >= MAX_INTENTOS) {
            codigo.setUsado(true);
            codigoRepository.save(codigo);
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "Demasiados intentos fallidos. Solicita un codigo nuevo.");
        }

        if (!passwordEncoder.matches(request.codigo().trim(),
                codigo.getCodigoHash())) {
            codigo.setIntentos(codigo.getIntentos() + 1);
            codigoRepository.save(codigo);
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "Codigo incorrecto. Revisa el codigo enviado.");
        }

        codigo.setUsado(true);
        codigoRepository.save(codigo);

        usuario.setContrasena(passwordEncoder.encode(request.nuevaContrasena()));
        usuarioRepository.save(usuario);

        return new MensajeResponse(
                "Contrasena actualizada correctamente. Ya puedes iniciar sesion.");
    }

    private CrearCuenta buscarUsuario(String contacto) {
        String valor = contacto == null ? "" : contacto.trim();

        Optional<CrearCuenta> resultado = valor.contains("@")
                ? usuarioRepository.findByCorreo(valor)
                : usuarioRepository.findByCelular(valor);

        return resultado.orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "Correo o celular no registrado"));
    }

    private String generarCodigo() {
        StringBuilder codigo = new StringBuilder();
        for (int i = 0; i < LONGITUD_CODIGO; i++) {
            codigo.append(ALEATORIO.nextInt(10));
        }
        return codigo.toString();
    }

    private String enmascarar(String valor) {
        if (valor == null || valor.length() <= 3) {
            return valor;
        }
        int arroba = valor.indexOf('@');
        if (arroba > 0) {
            return valor.substring(0, 2) + "***" + valor.substring(arroba);
        }
        return valor.substring(0, 3) + "***"
                + valor.substring(valor.length() - 3);
    }

    private String cuerpoCorreo(String nombre, String codigo) {
        return "<div style=\"font-family:Arial,sans-serif;max-width:480px;margin:0 auto;\">"
                + "<h2 style=\"color:#2563eb;\">Picur - Recupera tu contrasena</h2>"
                + "<p>Hola <strong>" + nombre + "</strong>,</p>"
                + "<p>Usa este codigo para restablecer tu contrasena:</p>"
                + "<p style=\"font-size:28px;font-weight:800;letter-spacing:6px;color:#172033;"
                + "background:#f4f7fb;padding:14px;border-radius:10px;text-align:center;\">"
                + codigo + "</p>"
                + "<p>El codigo vence en " + DURACION_MINUTOS
                + " minutos. Si no lo solicitaste, ignora este mensaje.</p>"
                + "</div>";
    }
}