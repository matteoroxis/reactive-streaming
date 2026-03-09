package it.matteoroxis.reactive_streaming.model.request;

import java.time.Instant;

public class TelemetryIngestRequest {

    private String deviceId;
    private double temperature;
    private Instant timestamp;

    public TelemetryIngestRequest() {
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}

