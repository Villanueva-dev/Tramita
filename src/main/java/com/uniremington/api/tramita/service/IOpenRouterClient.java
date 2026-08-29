package com.uniremington.api.tramita.service;

import java.util.List;

public interface IOpenRouterClient {

    String answer(String question, List<String> context);
}
