package com.picur.demo.service;

import java.util.Optional;

public interface AsistenteIaService {

    /**
     * Reescribe la recomendacion base en un texto mas natural.
     *
     * @return el texto enriquecido, o {@link Optional#empty()} cuando la IA
     *         esta deshabilitada o falla (el llamador debe usar el texto base).
     */
    Optional<String> enriquecer(ContextoVibracion contexto);
}
