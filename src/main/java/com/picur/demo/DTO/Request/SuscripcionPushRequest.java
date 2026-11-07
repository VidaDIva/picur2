package com.picur.demo.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SuscripcionPushRequest(

        @NotNull
        Long usuarioId,

        @NotBlank
        @Size(max = 512)
        String endpoint,

        @NotBlank
        @Size(max = 200)
        String p256dh,

        @NotBlank
        @Size(max = 100)
        String auth,

        @Size(max = 300)
        String userAgent
) {
}
