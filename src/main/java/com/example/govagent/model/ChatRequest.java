package com.example.govagent.model;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank String userId,
        String sessionId,
        @NotBlank String message
) {
}
