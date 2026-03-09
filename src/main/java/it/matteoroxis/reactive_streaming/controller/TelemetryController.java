package it.matteoroxis.reactive_streaming.controller;

import it.matteoroxis.reactive_streaming.model.document.TelemetryEvent;
import it.matteoroxis.reactive_streaming.model.request.TelemetryIngestRequest;
import it.matteoroxis.reactive_streaming.service.TelemetryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryService service;

    public TelemetryController(TelemetryService service) {
        this.service = service;
    }

    @GetMapping(
            value = "/stream/{deviceId}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<TelemetryEvent> stream(
            @PathVariable String deviceId) {

        return service.streamByDevice(deviceId);
    }

    @PostMapping(
            value = "/ingest",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<TelemetryEvent> ingest(
            @RequestBody Mono<TelemetryIngestRequest> request) {

        return request.flatMap(service::ingest);
    }

}
