package com.picur.demo.config;

import org.springframework.stereotype.Component;

/**
 * RANGOS INICIALES DE CALIBRACIÓN DEL PROTOTIPO.
 *
 * NO representan valores oficiales ni medidos de la carretera
 * Medellín–Bogotá. Son solo puntos de partida que se ajustarán con
 * los datos reales obtenidos durante las pruebas del proyecto.
 *
 * Todo está centralizado aquí para poder cambiar umbrales y rangos
 * sin reescribir la lógica de detección.
 */
@Component
public class ConfiguracionVibracion {

    // ---------- Rangos normales por variable ----------
    private final RangoVibracion frecuencia = new RangoVibracion(1.0, 25.0);
    private final RangoVibracion aceleracionX = new RangoVibracion(-2.0, 2.0);
    private final RangoVibracion aceleracionY = new RangoVibracion(-2.0, 2.0);
    private final RangoVibracion aceleracionZ = new RangoVibracion(-4.0, 4.0);
    private final RangoVibracion rms = new RangoVibracion(0.0, 3.0);
    private final RangoVibracion inclinacion = new RangoVibracion(-15.0, 15.0);

    // ---------- Parámetros de la detección ----------
    private final int tamanoVentana = 5;
    private final double indiceNormalMaximo = 0.30;
    private final double indicePrecaucionMaximo = 0.60;

    public RangoVibracion getFrecuencia() {
        return frecuencia;
    }

    public RangoVibracion getAceleracionX() {
        return aceleracionX;
    }

    public RangoVibracion getAceleracionY() {
        return aceleracionY;
    }

    public RangoVibracion getAceleracionZ() {
        return aceleracionZ;
    }

    public RangoVibracion getRms() {
        return rms;
    }

    public RangoVibracion getInclinacion() {
        return inclinacion;
    }

    public int getTamanoVentana() {
        return tamanoVentana;
    }

    public double getIndiceNormalMaximo() {
        return indiceNormalMaximo;
    }

    public double getIndicePrecaucionMaximo() {
        return indicePrecaucionMaximo;
    }
}