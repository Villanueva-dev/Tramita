package com.uniremington.api.tramita.service;

import com.uniremington.api.tramita.dto.AssistantRequest;
import com.uniremington.api.tramita.dto.AssistantResponse;

public interface IAssistantService {

    AssistantResponse answer(AssistantRequest request);
}
