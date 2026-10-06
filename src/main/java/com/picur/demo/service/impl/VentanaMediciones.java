package com.picur.demo.service.impl;

import java.util.ArrayDeque;
import java.util.Deque;

import com.picur.demo.config.ConfiguracionVibracion;
import com.picur.demo.enums.EstadoVibracion;

/**
 * Ventana deslizante con las últimas N mediciones consecutivas.
 *
 * Evita que una lectura aislada dispare una alarma persistente: el
 * estado final combina el índice promedio de la ventana con la
 * proporción de lecturas que presentan desviaciones.
 */
public class VentanaMediciones {

    private final int tamano;
    private final double indiceNormalMaximo;
    private final double indicePrecaucionMaximo;
    private final Deque<ResultadoLectura> lecturas = new ArrayDeque<>();

    public VentanaMediciones(ConfiguracionVibracion config) {
        this.tamano = config.getTamanoVentana();
        this.indiceNormalMaximo = config.getIndiceNormalMaximo();
        this.indicePrecaucionMaximo = config.getIndicePrecaucionMaximo();
    }

    public synchronized Resultado registrar(ResultadoLectura lectura) {
        lecturas.addLast(lectura);
        while (lecturas.size() > tamano) {
            lecturas.removeFirst();
        }

        double indicePromedio = lecturas.stream()
                .mapToDouble(ResultadoLectura::indiceLectura)
                .average()
                .orElse(0.0);

        long lecturasAfectadas = lecturas.stream()
                .filter(r -> r.variablesFueraDeRango() > 0)
                .count();

        double proporcionAfectadas = (double) lecturasAfectadas / lecturas.size();

        indicePromedio = Math.round(indicePromedio * 100.0) / 100.0;

        return new Resultado(indicePromedio,
                estado(indicePromedio, proporcionAfectadas));
    }

    private EstadoVibracion estado(double indice, double proporcionAfectadas) {
        // Al menos la mitad de las lecturas de la ventana debe presentar
        // desviaciones; así un valor suelto no se convierte en alarma.
        if (proporcionAfectadas < 0.5) {
            return EstadoVibracion.NORMAL;
        }
        if (indice >= indicePrecaucionMaximo) {
            return EstadoVibracion.ANOMALA;
        }
        if (indice >= indiceNormalMaximo) {
            return EstadoVibracion.PRECAUCION;
        }
        return EstadoVibracion.NORMAL;
    }

    public record ResultadoLectura(double indiceLectura, int variablesFueraDeRango) {
    }

    public record Resultado(double indiceAnomalia, EstadoVibracion estado) {
    }
}