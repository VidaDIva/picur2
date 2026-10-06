package com.picur.demo.service.impl;

import com.picur.demo.config.ConfiguracionVibracion;
import com.picur.demo.config.RangoVibracion;
import com.picur.demo.model.MedicionVibracion;
import com.picur.demo.service.AnalizadorVibracionService;
import org.springframework.stereotype.Service;

@Service
public class AnalizadorVibracionServiceImpl implements AnalizadorVibracionService {

    private static final int TOTAL_VARIABLES = 6;

    private final ConfiguracionVibracion config;
    private final VentanaMediciones ventana;

    public AnalizadorVibracionServiceImpl(ConfiguracionVibracion config) {
        this.config = config;
        this.ventana = new VentanaMediciones(config);
    }

    @Override
    public Analisis analizar(MedicionVibracion medicion) {
        VentanaMediciones.ResultadoLectura lectura = evaluar(medicion);
        VentanaMediciones.Resultado resultado = ventana.registrar(lectura);

        return new Analisis(resultado.estado(), resultado.indiceAnomalia());
    }

    /**
     * Evalúa las 6 variables de una medición.
     *
     * Cada variable fuera de su rango suma 1 punto. El índice de anomalía
     * combina esa proporción con la magnitud de las desviaciones: cuanto
     * más lejos estén los valores de su rango, mayor será el índice.
     */
    private VentanaMediciones.ResultadoLectura evaluar(MedicionVibracion m) {
        double[] desviaciones = {
                desviacion(m.getFrecuencia(), config.getFrecuencia()),
                desviacion(m.getAceleracionX(), config.getAceleracionX()),
                desviacion(m.getAceleracionY(), config.getAceleracionY()),
                desviacion(m.getAceleracionZ(), config.getAceleracionZ()),
                desviacion(m.getRms(), config.getRms()),
                desviacion(m.getInclinacion(), config.getInclinacion())
        };

        int fueraDeRango = 0;
        double sumaDesviaciones = 0.0;
        for (double d : desviaciones) {
            if (d > 0.0) {
                fueraDeRango++;
                sumaDesviaciones += d;
            }
        }

        double proporcion = fueraDeRango / (double) TOTAL_VARIABLES;
        double magnitud = fueraDeRango == 0.0
                ? 0.0
                : sumaDesviaciones / fueraDeRango;

        double indice = Math.min(1.0, proporcion + (1.0 - proporcion) * magnitud);

        return new VentanaMediciones.ResultadoLectura(indice, fueraDeRango);
    }

    private double desviacion(Double valor, RangoVibracion rango) {
        return valor == null ? 0.0 : rango.desviacionNormalizada(valor);
    }
}