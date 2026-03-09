package it.matteoroxis.reactive_streaming.repository;

import it.matteoroxis.reactive_streaming.model.document.TelemetryEvent;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface TelemetryRepository extends ReactiveMongoRepository<TelemetryEvent, String> {

    Flux<TelemetryEvent> findByDeviceId(String deviceId);
}
