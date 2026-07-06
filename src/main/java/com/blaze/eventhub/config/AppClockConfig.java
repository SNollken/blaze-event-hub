package com.blaze.eventhub.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppClockConfig {

	@Bean
	Clock appClock() {
		return Clock.systemUTC();
	}
}
