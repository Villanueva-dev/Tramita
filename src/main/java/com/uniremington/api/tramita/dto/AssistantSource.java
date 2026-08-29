package com.uniremington.api.tramita.dto;

public record AssistantSource(
        String sourceId,
        String title,
        String version,
        String locator,
        String section,
        Integer page) {
}
