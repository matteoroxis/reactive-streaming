package it.matteoroxis.reactive_streaming.service;

import it.matteoroxis.reactive_streaming.model.document.TelemetryEvent;
import it.matteoroxis.reactive_streaming.model.request.TelemetryIngestRequest;
import it.matteoroxis.reactive_streaming.repository.TelemetryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {

    @Mock
    private TelemetryRepository repository;

    @InjectMocks
    private TelemetryService service;

    private TelemetryEvent event;

    @BeforeEach
    void setUp() {
        event = new TelemetryEvent("device-001", 23.5, Instant.parse("2024-01-15T10:30:00Z"));
        event.setId("abc123");
    }

    // --- ingest ---

    @Test
    void ingest_shouldSaveEventWithProvidedTimestamp() {
        TelemetryIngestRequest request = new TelemetryIngestRequest();
        request.setDeviceId("device-001");
        request.setTemperature(23.5);
        request.setTimestamp(Instant.parse("2024-01-15T10:30:00Z"));

        when(repository.save(any(TelemetryEvent.class))).thenReturn(Mono.just(event));

        StepVerifier.create(service.ingest(request))
                .expectNextMatches(e ->
                        e.getId().equals("abc123") &&
                        e.getDeviceId().equals("device-001") &&
                        e.getTemperature() == 23.5)
                .verifyComplete();
    }

    @Test
    void ingest_shouldUseCurrentTimestampWhenNotProvided() {
        TelemetryIngestRequest request = new TelemetryIngestRequest();
        request.setDeviceId("device-001");
        request.setTemperature(25.0);
        // timestamp not set → should default to Instant.now()

        when(repository.save(any(TelemetryEvent.class))).thenReturn(Mono.just(event));

        StepVerifier.create(service.ingest(request))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void ingest_shouldPropagateRepositoryError() {
        TelemetryIngestRequest request = new TelemetryIngestRequest();
        request.setDeviceId("device-001");
        request.setTemperature(23.5);

        when(repository.save(any(TelemetryEvent.class)))
                .thenReturn(Mono.error(new RuntimeException("DB error")));

        StepVerifier.create(service.ingest(request))
                .expectErrorMessage("DB error")
                .verify();
    }

    // --- streamByDevice ---

    @Test
    void streamByDevice_shouldReturnEventsForDevice() {
        TelemetryEvent event2 = new TelemetryEvent("device-001", 24.0, Instant.now());
        event2.setId("def456");

        when(repository.findByDeviceId("device-001"))
                .thenReturn(Flux.just(event, event2));

        StepVerifier.create(service.streamByDevice("device-001"))
                .expectNextMatches(e -> e.getId().equals("abc123"))
                .expectNextMatches(e -> e.getId().equals("def456"))
                .verifyComplete();
    }

    @Test
    void streamByDevice_shouldReturnEmptyFluxWhenNoEvents() {
        when(repository.findByDeviceId("unknown-device"))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.streamByDevice("unknown-device"))
                .verifyComplete();
    }

    @Test
    void streamByDevice_shouldPropagateRepositoryError() {
        when(repository.findByDeviceId("device-001"))
                .thenReturn(Flux.error(new RuntimeException("Stream error")));

        StepVerifier.create(service.streamByDevice("device-001"))
                .expectErrorMessage("Stream error")
                .verify();
    }
}

