package com.picur.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.picur.demo.model.MedicionVibracion;

public interface MedicionVibracionRepository extends JpaRepository<MedicionVibracion, Long> {
    
}
