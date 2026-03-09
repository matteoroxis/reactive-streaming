package it.matteoroxis.reactive_streaming.service;

import it.matteoroxis.reactive_streaming.model.document.TelemetryEvent;
import it.matteoroxis.reactive_streaming.model.request.TelemetryIngestRequest;
import it.matteoroxis.reactive_streaming.repository.TelemetryRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class TelemetryService {

    private final TelemetryRepository repository;

    public TelemetryService(TelemetryRepository repository) {
        this.repository = repository;
    }

    public Flux<TelemetryEvent> streamByDevice(String deviceId) {
        return repository.findByDeviceId(deviceId)
                .onBackpressureLatest();
    }

    public Mono<TelemetryEvent> ingest(TelemetryIngestRequest request) {
        Instant timestamp = request.getTimestamp() != null
                ? request.getTimestamp()
                : Instant.now();
        TelemetryEvent event = new TelemetryEvent(
                request.getDeviceId(),
                request.getTemperature(),
                timestamp);
        return repository.save(event);
    }

}
