package com.uniremington.api.tramita.controller;

import com.uniremington.api.tramita.dto.AssistantRequest;
import com.uniremington.api.tramita.dto.AssistantResponse;
import com.uniremington.api.tramita.service.IAssistantService;
import com.uniremington.api.tramita.service.impl.AssistantRateLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Asistente académico: consulta documental sin capacidad de modificar trámites. */
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final IAssistantService assistantService;
    private final AssistantRateLimitService rateLimitService;

    @PostMapping
    public AssistantResponse answer(
            @Valid @RequestBody AssistantRequest request, org.springframework.security.core.Authentication authentication) {
        rateLimitService.checkAndRecord(authentication.getName());
        return assistantService.answer(request);
    }
}
