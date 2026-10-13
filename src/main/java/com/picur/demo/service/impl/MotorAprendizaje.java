package com.picur.demo.service.impl;

import com.picur.demo.model.MedicionVibracion;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Motor de aprendizaje ligero: cada sensor acumula medias, desviaciones
 * y medias exponenciales de sus métricas. Con pocas muestras basta para
 * clasificar nuevos valores por su distancia al comportamiento histórico
 * (estilo z-score) y para detectar tendencias crecientes.
 */
@Component
public class MotorAprendizaje {

    public enum Severidad { NORMAL, PRECAUCION, ANOMALA }

    public record Resultado(Severidad severidad, double zMaxima,
                            String metrica, boolean tendenciaCreciente,
                            long muestras) {
    }

    private static final int MIN_MUESTRAS = 12;
    private static final double K_PRECAUCION = 2.0;
    private static final double K_ANOMALIA = 3.0;
    private static final double TENDENCIA = 0.04;

    private static final List<String> METRICAS = List.of(
            "vibracion", "rms", "frecuencia", "inclinacion",
            "aceleracionX", "aceleracionY", "aceleracionZ");

    private static final class Serie {
        private long n;
        private double media;
        private double m2;
        private double ema;
        private double emaLenta;

        void actualizar(double v) {
            if (n == 0) {
                n = 1;
                media = v;
                ema = v;
                emaLenta = v;
                return;
            }
            n++;
            double delta = v - media;
            media += delta / n;
            m2 += delta * (v - media);
            ema = 0.3 * v + 0.7 * ema;
            emaLenta = 0.06 * v + 0.94 * emaLenta;
        }

        double desviacion() {
            return n < 2 ? 0.0 : Math.sqrt(m2 / (n - 1.0));
        }

        boolean suficiente() {
            return n >= MIN_MUESTRAS;
        }

        boolean tendenciaCreciente() {
            return emaLenta > 0 && ema > emaLenta * (1.0 + TENDENCIA);
        }

        double z(double v) {
            double d = desviacion();
            return d <= 1e-9 ? 0.0 : Math.abs(v - media) / d;
        }
    }

    private final Map<String, Map<String, Serie>> series = new HashMap<>();

    private Map<String, Serie> seriesDe(String dispositivo) {
        return series.computeIfAbsent(dispositivo, k -> new HashMap<>());
    }

    public synchronized void aprender(MedicionVibracion medicion) {
        Map<String, Serie> mapa = seriesDe(dispositivo(medicion));
        for (String metrica : METRICAS) {
            Double valor = valor(medicion, metrica);
            if (valor != null) {
                mapa.computeIfAbsent(metrica, k -> new Serie())
                        .actualizar(valor);
            }
        }
    }

    public synchronized Resultado evaluar(MedicionVibracion medicion,
                                          double indiceServidor) {
        Map<String, Serie> mapa = seriesDe(dispositivo(medicion));

        double zMaxima = 0.0;
        String metricaMaxima = "";
        boolean tendencia = false;
        long muestras = 0;

        for (String metrica : METRICAS) {
            Double valor = valor(medicion, metrica);
            Serie serie = mapa.get(metrica);
            if (valor == null || serie == null) {
                continue;
            }
            muestras = Math.max(muestras, serie.n);
            if (serie.suficiente()) {
                double z = serie.z(valor);
                if (z > zMaxima) {
                    zMaxima = z;
                    metricaMaxima = metrica;
                }
            }
            if (serie.tendenciaCreciente()) {
                tendencia = true;
            }
        }

        Severidad severidad = severidad(zMaxima, muestras, indiceServidor);

        return new Resultado(severidad, zMaxima, metricaMaxima,
                tendencia, muestras);
    }

    private Severidad severidad(double zMaxima, long muestras,
                                double indiceServidor) {
        Severidad aprendizaje = Severidad.NORMAL;
        if (muestras >= MIN_MUESTRAS) {
            if (zMaxima >= K_ANOMALIA) {
                aprendizaje = Severidad.ANOMALA;
            } else if (zMaxima >= K_PRECAUCION) {
                aprendizaje = Severidad.PRECAUCION;
            }
        }

        Severidad indice = Severidad.NORMAL;
        if (indiceServidor >= 0.6) {
            indice = Severidad.ANOMALA;
        } else if (indiceServidor >= 0.3) {
            indice = Severidad.PRECAUCION;
        }

        return aprendizaje.compareTo(indice) >= 0 ? aprendizaje : indice;
    }

    private Double valor(MedicionVibracion m, String metrica) {
        return switch (metrica) {
            case "vibracion" -> m.getVibracion();
            case "rms" -> m.getRms();
            case "frecuencia" -> m.getFrecuencia();
            case "inclinacion" -> m.getInclinacion();
            case "aceleracionX" -> m.getAceleracionX();
            case "aceleracionY" -> m.getAceleracionY();
            case "aceleracionZ" -> m.getAceleracionZ();
            default -> null;
        };
    }

    private String dispositivo(MedicionVibracion m) {
        return m.getDispositivo() == null || m.getDispositivo().isBlank()
                ? "sensor-picur"
                : m.getDispositivo();
    }
}