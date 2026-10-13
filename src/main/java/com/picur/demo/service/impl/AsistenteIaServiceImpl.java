package com.picur.demo.service.impl;

import com.picur.demo.service.AsistenteIaService;
import com.picur.demo.service.ContextoVibracion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cliente de Gemini (Google Generative Language API) que reescribe las
 * recomendaciones del motor estadistico. Si esta deshabilitado, no hay API key
 * o la llamada falla, devuelve {@link Optional#empty()} para que el llamador
 * conserve la recomendacion base.
 */
@Service
public class AsistenteIaServiceImpl implements AsistenteIaService {

    private static final Logger log =
            LoggerFactory.getLogger(AsistenteIaServiceImpl.class);

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com";
    private static final int MAX_LONGITUD = 480;
    private static final int MAX_TOKENS = 180;

    private final String modelo;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AsistenteIaServiceImpl(
            @Value("${picur.ia.habilitado:false}") boolean habilitado,
            @Value("${picur.ia.modelo:gemini-2.0-flash}") String modelo,
            @Value("${picur.ia.api-key:}") String apiKey,
            @Value("${picur.ia.timeout-ms:8000}") long timeoutMs,
            ObjectMapper objectMapper) {

        this.modelo = modelo;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;

        if (!habilitado || apiKey == null || apiKey.isBlank()) {
            this.restClient = null;
            log.info("Asistente IA (Gemini) deshabilitado: "
                    + "se usaran las recomendaciones base.");
            return;
        }

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(factory)
                .build();
    }

    @Override
    public Optional<String> enriquecer(ContextoVibracion contexto) {
        if (restClient == null || contexto == null) {
            return Optional.empty();
        }

        try {
            Map<String, Object> cuerpo = Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of("text", prompt(contexto))))),
                    "generationConfig", Map.of(
                            "temperature", 0.4,
                            "maxOutputTokens", MAX_TOKENS));

            String json = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{modelo}:generateContent")
                            .queryParam("key", apiKey)
                            .build(modelo))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .body(String.class);

            return extraerTexto(json);
        } catch (Exception e) {
            log.warn("Gemini no respondio, se usa la recomendacion base: {}",
                    e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> extraerTexto(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode raiz = objectMapper.readTree(json);
            JsonNode texto = raiz.path("candidates").path(0)
                    .path("content").path("parts").path(0).path("text");
            String valor = texto.asString();
            if (valor == null || valor.isBlank()) {
                return Optional.empty();
            }
            String limpio = valor.replaceAll("\\s+", " ").trim();
            if (limpio.isEmpty()) {
                return Optional.empty();
            }
            if (limpio.length() > MAX_LONGITUD) {
                limpio = limpio.substring(0, MAX_LONGITUD - 3) + "...";
            }
            return Optional.of(limpio);
        } catch (Exception e) {
            log.warn("No se pudo interpretar la respuesta de Gemini: {}",
                    e.getMessage());
            return Optional.empty();
        }
    }

    private String prompt(ContextoVibracion c) {
        return "Eres el asistente de monitoreo de vibraciones \"Picur\". "
                + "Reescribe la recomendacion para el operario en 1 o 2 frases "
                + "claras en espanol, tono profesional y accionable, sin markdown, "
                + "sin listas y sin inventar datos.\n\n"
                + "Sensor: " + valor(c.dispositivo()) + "\n"
                + "Severidad: " + valor(c.severidad()) + "\n"
                + "Metrica desviada: " + valor(c.metrica()) + "\n"
                + "Indice de anomalia: "
                + (c.indice() == null ? "-" : c.indice()) + "\n"
                + "Z-score maximo: " + String.format("%.2f", c.zMaxima()) + "\n"
                + "Tendencia creciente: "
                + (c.tendenciaCreciente() ? "si" : "no") + "\n"
                + "Lecturas actuales: " + valor(c.resumenSensores()) + "\n"
                + "Titulo base: " + valor(c.tituloBase()) + "\n"
                + "Recomendacion base: " + valor(c.recomendacionBase());
    }

    private String valor(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }
}
