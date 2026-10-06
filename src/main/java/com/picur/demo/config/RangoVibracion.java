package com.picur.demo.config;

/**
 * Rango permitido para una variable de vibración (mínimo–máximo).
 *
 * La desviación normalizada mide cuán lejos está un valor fuera de su
 * rango respecto al ancho del rango. Ejemplo: RMS con rango 0–3 y valor 4.5
 * tiene desviación 0.5 (una vez y media el ancho de la banda).
 */
public record RangoVibracion(double minimo, double maximo) {

    public RangoVibracion {
        if (maximo < minimo) {
            throw new IllegalArgumentException(
                    "El máximo debe ser mayor o igual al mínimo");
        }
    }

    public boolean dentro(double valor) {
        return valor >= minimo && valor <= maximo;
    }

    public double ancho() {
        return maximo - minimo;
    }

    public double desviacionNormalizada(double valor) {
        if (valor > maximo) {
            return (valor - maximo) / ancho();
        }
        if (valor < minimo) {
            return (minimo - valor) / ancho();
        }
        return 0.0;
    }
}