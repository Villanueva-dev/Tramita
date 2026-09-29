package com.uniremington.api.tramita.dto;

import java.util.List;

public record RequestDashboardPageResponse(
        List<RequestDashboardEntryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {
}