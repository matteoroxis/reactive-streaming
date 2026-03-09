package it.matteoroxis.reactive_streaming.controller;

import it.matteoroxis.reactive_streaming.model.document.TelemetryEvent;
import it.matteoroxis.reactive_streaming.service.TelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(TelemetryController.class)
class TelemetryControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private TelemetryService service;

    private TelemetryEvent savedEvent;

    @BeforeEach
    void setUp() {
        savedEvent = new TelemetryEvent("device-001", 23.5, Instant.parse("2024-01-15T10:30:00Z"));
        savedEvent.setId("abc123");
    }

    // --- POST /ingest ---

    @Test
    void ingest_shouldReturn200WithSavedEvent() {
        when(service.ingest(any())).thenReturn(Mono.just(savedEvent));

        webTestClient.post()
                .uri("/api/telemetry/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "deviceId": "device-001",
                          "temperature": 23.5,
                          "timestamp": "2024-01-15T10:30:00Z"
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id").isEqualTo("abc123")
                .jsonPath("$.deviceId").isEqualTo("device-001")
                .jsonPath("$.temperature").isEqualTo(23.5);
    }

    @Test
    void ingest_shouldReturn200WithAutoTimestamp() {
        when(service.ingest(any())).thenReturn(Mono.just(savedEvent));

        webTestClient.post()
                .uri("/api/telemetry/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "deviceId": "device-001",
                          "temperature": 25.0
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.deviceId").isEqualTo("device-001");
    }

    @Test
    void ingest_shouldReturn5xxOnServiceError() {
        when(service.ingest(any()))
                .thenReturn(Mono.error(new RuntimeException("DB error")));

        webTestClient.post()
                .uri("/api/telemetry/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "deviceId": "device-001",
                          "temperature": 23.5
                        }
                        """)
                .exchange()
                .expectStatus().is5xxServerError();
    }

    // --- GET /stream/{deviceId} ---

    @Test
    void stream_shouldReturnSseEvents() {
        TelemetryEvent event2 = new TelemetryEvent("device-001", 24.0, Instant.now());
        event2.setId("def456");

        when(service.streamByDevice("device-001"))
                .thenReturn(Flux.just(savedEvent, event2));

        webTestClient.get()
                .uri("/api/telemetry/stream/device-001")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBodyList(TelemetryEvent.class)
                .hasSize(2);
    }

    @Test
    void stream_shouldReturnEmptyStreamWhenNoEvents() {
        when(service.streamByDevice("unknown-device"))
                .thenReturn(Flux.empty());

        webTestClient.get()
                .uri("/api/telemetry/stream/unknown-device")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TelemetryEvent.class)
                .hasSize(0);
    }
}

