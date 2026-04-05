package com.example.javamcp.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

@Component
public class OperationObservationService {

    private final ObservationRegistry observationRegistry;

    public OperationObservationService(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    public <T> T observe(String name,
                         String contextualName,
                         Map<String, String> lowCardinalityTags,
                         Supplier<T> supplier) {
        Observation observation = Observation.createNotStarted(name, observationRegistry)
                .contextualName(contextualName == null || contextualName.isBlank() ? name : contextualName);

        if (lowCardinalityTags != null) {
            lowCardinalityTags.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    observation.lowCardinalityKeyValue(key, value);
                }
            });
        }

        observation.start();
        try (Observation.Scope ignored = observation.openScope()) {
            T result = supplier.get();
            observation.lowCardinalityKeyValue("status", "ok");
            return result;
        } catch (RuntimeException ex) {
            observation.lowCardinalityKeyValue("status", "error");
            observation.error(ex);
            throw ex;
        } finally {
            observation.stop();
        }
    }
}
