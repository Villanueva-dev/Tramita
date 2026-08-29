package com.uniremington.api.tramita.dto;

import java.util.List;

public record AssistantResponse(
        String answer,
        boolean grounded,
        List<AssistantSource> sources,
        String disclaimer) {
}
