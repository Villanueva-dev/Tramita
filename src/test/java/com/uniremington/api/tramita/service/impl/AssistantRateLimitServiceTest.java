package com.uniremington.api.tramita.service.impl;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.uniremington.api.tramita.shared.config.AiProperties;
import com.uniremington.api.tramita.shared.exception.AssistantRateLimitExceededException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AssistantRateLimitServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-10T15:00:00Z"), ZoneOffset.UTC);
    private final AssistantRateLimitService service = new AssistantRateLimitService(
            clock, new AiProperties(true, "key", "https://example.test", "model", 500, 20, 2, 60, "", 120));

    @Test
    void blocksTheThirdRequestFromTheSameUserWithinTheWindow() {
        service.checkAndRecord("coordinacion.cali@uniremington.edu.co");
        service.checkAndRecord("coordinacion.cali@uniremington.edu.co");

        assertThatExceptionOfType(AssistantRateLimitExceededException.class)
                .isThrownBy(() -> service.checkAndRecord("coordinacion.cali@uniremington.edu.co"))
                .satisfies(exception -> org.assertj.core.api.Assertions
                        .assertThat(exception.getRetryAfterSeconds()).isEqualTo(60));
    }

    @Test
    void keepsCountersIndependentForDifferentUsers() {
        service.checkAndRecord("coordinacion.cali@uniremington.edu.co");
        service.checkAndRecord("coordinacion.cali@uniremington.edu.co");

        service.checkAndRecord("otra.coordinacion@uniremington.edu.co");
    }
}