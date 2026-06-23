package com.nollen.blaze.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.nollen.blaze.config.BlazeProperties;
import com.nollen.blaze.oauth.TokenStore;
import com.nollen.blaze.overlays.OverlayRepository;

import org.springframework.stereotype.Service;

@Service
public class StatusService {

	private static final String APP_NAME = "NollenBlaze";

	private final BlazeProperties blazeProperties;
	private final TokenStore tokenStore;
	private final OverlayRepository overlayRepository;
	private final Clock clock;
	private final Instant startedAt;

	public StatusService(BlazeProperties blazeProperties, TokenStore tokenStore, OverlayRepository overlayRepository, Clock clock) {
		this.blazeProperties = blazeProperties;
		this.tokenStore = tokenStore;
		this.overlayRepository = overlayRepository;
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
				blazeProperties.isSocketConfigured(),
				tokenStore.current().map(token -> !token.accessTokenBlank()).orElse(false),
				tokenStore.current().map(token -> !token.refreshTokenBlank()).orElse(false),
				blazeProperties.isMonitoredChannelConfigured(),
				overlayRepository.countProfiles(),
				overlayRepository.countOverlays(),
				Duration.between(startedAt, Instant.now(clock)).toSeconds());
	}

	private String version() {
		String implementationVersion = StatusService.class.getPackage().getImplementationVersion();
		return implementationVersion == null ? "0.0.1-SNAPSHOT" : implementationVersion;
	}
}
