package it.matteoroxis.reactive_streaming;

import it.matteoroxis.reactive_streaming.model.document.TelemetryEvent;
import it.matteoroxis.reactive_streaming.model.request.TelemetryIngestRequest;
import it.matteoroxis.reactive_streaming.repository.TelemetryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TelemetryIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private TelemetryRepository repository;

    @AfterEach
    void cleanUp() {
        repository.deleteAll().block();
    }

    // --- Ingest integration ---

    @Test
    void ingest_shouldPersistEventToDatabase() {
        TelemetryIngestRequest request = new TelemetryIngestRequest();
        request.setDeviceId("device-integration");
        request.setTemperature(30.0);
        request.setTimestamp(Instant.parse("2024-03-01T12:00:00Z"));

        webTestClient.post()
                .uri("/api/telemetry/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TelemetryEvent.class)
                .value(event -> {
                    assert event.getId() != null;
                    assert "device-integration".equals(event.getDeviceId());
                    assert event.getTemperature() == 30.0;
                });

        StepVerifier.create(repository.findByDeviceId("device-integration"))
                .expectNextMatches(e -> e.getDeviceId().equals("device-integration"))
                .verifyComplete();
    }

    @Test
    void ingest_shouldAutoGenerateTimestampWhenAbsent() {
        Instant before = Instant.now();

        webTestClient.post()
                .uri("/api/telemetry/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "deviceId": "device-ts",
                          "temperature": 18.0
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TelemetryEvent.class)
                .value(event -> {
                    assert event.getTimestamp() != null;
                    assert !event.getTimestamp().isBefore(before);
                });
    }

    // --- Stream integration ---

    @Test
    void stream_shouldReturnPersistedEventsForDevice() {
        // Pre-populate the DB with two events
        TelemetryEvent e1 = new TelemetryEvent("device-stream", 21.0, Instant.now());
        TelemetryEvent e2 = new TelemetryEvent("device-stream", 22.0, Instant.now());
        repository.saveAll(Flux.just(e1, e2)).blockLast();

        webTestClient.get()
                .uri("/api/telemetry/stream/device-stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBodyList(TelemetryEvent.class)
                .hasSize(2);
    }

    @Test
    void stream_shouldReturnEmptyStreamForUnknownDevice() {
        webTestClient.get()
                .uri("/api/telemetry/stream/no-such-device")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TelemetryEvent.class)
                .hasSize(0);
    }

    // --- End-to-end: ingest then stream ---

    @Test
    void endToEnd_ingestThenStream() {
        // Ingest 3 events via REST
        for (int i = 1; i <= 3; i++) {
            TelemetryIngestRequest req = new TelemetryIngestRequest();
            req.setDeviceId("device-e2e");
            req.setTemperature(20.0 + i);
            req.setTimestamp(Instant.now());

            webTestClient.post()
                    .uri("/api/telemetry/ingest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .exchange()
                    .expectStatus().isOk();
        }

        // Poll until exactly 3 documents are persisted (max 5 seconds)
        StepVerifier.create(
                Flux.interval(Duration.ofMillis(100))
                        .flatMap(tick -> repository.findByDeviceId("device-e2e").collectList())
                        .filter(list -> list.size() == 3)
                        .next()
                        .timeout(Duration.ofSeconds(5))
        ).expectNextCount(1).verifyComplete();

        // Stream should contain exactly 3 events
        webTestClient
                .mutate()
                .responseTimeout(Duration.ofSeconds(10))
                .build()
                .get()
                .uri("/api/telemetry/stream/device-e2e")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TelemetryEvent.class)
                .hasSize(3);
    }
}

