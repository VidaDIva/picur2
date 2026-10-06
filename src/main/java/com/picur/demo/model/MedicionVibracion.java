package com.picur.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

import com.picur.demo.enums.EstadoVibracion;

@Entity
@Table(name = "mediciones_vibracion")
public class MedicionVibracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String dispositivo;

    private Double aceleracionX;

    private Double aceleracionY;

    private Double aceleracionZ;

    private Double frecuencia;

    private Double rms;

    private Double inclinacion;

    /** Índice de vibración calculado por el dispositivo (MPU + piezo). */
    private Double vibracion;

    /** Umbral dinámico aprendido por el dispositivo (límite de tráfico normal). */
    private Double umbral;

    @Enumerated(EnumType.STRING)
    private EstadoVibracion estado;

    private Double indiceAnomalia;

    private LocalDateTime fechaRegistro;

    public MedicionVibracion(Long id, String dispositivo, Double aceleracionX, Double aceleracionY, Double aceleracionZ, Double frecuencia,
            Double rms, Double inclinacion, EstadoVibracion estado, Double indiceAnomalia,
            LocalDateTime fechaRegistro) {
        this.id = id;
        this.dispositivo = dispositivo;
        this.aceleracionX = aceleracionX;
        this.aceleracionY = aceleracionY;
        this.aceleracionZ = aceleracionZ;
        this.frecuencia = frecuencia;
        this.rms = rms;
        this.inclinacion = inclinacion;
        this.estado = estado;
        this.indiceAnomalia = indiceAnomalia;
        this.fechaRegistro = fechaRegistro;
    }

    public MedicionVibracion() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDispositivo() {
        return dispositivo;
    }

    public void setDispositivo(String dispositivo) {
        this.dispositivo = dispositivo;
    }

    public Double getAceleracionX() {
        return aceleracionX;
    }

    public void setAceleracionX(Double aceleracionX) {
        this.aceleracionX = aceleracionX;
    }

    public Double getAceleracionY() {
        return aceleracionY;
    }

    public void setAceleracionY(Double aceleracionY) {
        this.aceleracionY = aceleracionY;
    }

    public Double getAceleracionZ() {
        return aceleracionZ;
    }

    public void setAceleracionZ(Double aceleracionZ) {
        this.aceleracionZ = aceleracionZ;
    }

    public Double getFrecuencia() {
        return frecuencia;
    }

    public void setFrecuencia(Double frecuencia) {
        this.frecuencia = frecuencia;
    }

    public Double getRms() {
        return rms;
    }

    public void setRms(Double rms) {
        this.rms = rms;
    }

    public Double getInclinacion() {
        return inclinacion;
    }

    public void setInclinacion(Double inclinacion) {
        this.inclinacion = inclinacion;
    }

    public Double getVibracion() {
        return vibracion;
    }

    public void setVibracion(Double vibracion) {
        this.vibracion = vibracion;
    }

    public Double getUmbral() {
        return umbral;
    }

    public void setUmbral(Double umbral) {
        this.umbral = umbral;
    }

    public EstadoVibracion getEstado() {
        return estado;
    }

    public void setEstado(EstadoVibracion estado) {
        this.estado = estado;
    }

    public Double getIndiceAnomalia() {
        return indiceAnomalia;
    }

    public void setIndiceAnomalia(Double indiceAnomalia) {
        this.indiceAnomalia = indiceAnomalia;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }
}