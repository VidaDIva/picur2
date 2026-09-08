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

    private Double aceleracionX;

    private Double aceleracionY;

    private Double aceleracionZ;

    private Double frecuencia;

    private Double rms;

    private Double inclinacion;

    @Enumerated(EnumType.STRING)
    private EstadoVibracion estado;

    private Double indiceAnomalia;

    private LocalDateTime fechaRegistro;

    public MedicionVibracion(Long id, Double aceleracionX, Double aceleracionY, Double aceleracionZ, Double frecuencia,
            Double rms, Double inclinacion, EstadoVibracion estado, Double indiceAnomalia,
            LocalDateTime fechaRegistro) {
        this.id = id;
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