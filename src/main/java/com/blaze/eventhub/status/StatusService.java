package com.blaze.eventhub.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.blaze.eventhub.config.BlazeProperties;

@Service
public class StatusService {

    private static final String APP_NAME = "Blaze Event Hub";

    private final BlazeProperties blazeProperties;
    private final Clock clock;
    private final Instant startedAt;

    public StatusService(BlazeProperties blazeProperties, Clock clock) {
        this.blazeProperties = blazeProperties;
        this.clock = clock;
        this.startedAt = Instant.now(clock);
    }

    public StatusResponse currentStatus() {
        return new StatusResponse(
                APP_NAME,
                version(),
                System.getProperty("java.version"),
                blazeProperties.isOAuthConfigured(),
                blazeProperties.isApiConfigured(),
                Duration.between(startedAt, Instant.now(clock)).toSeconds());
    }

    private String version() {
        String implementationVersion = StatusService.class.getPackage().getImplementationVersion();
        return implementationVersion == null ? "0.0.1-SNAPSHOT" : implementationVersion;
    }
}
