package com.nollen.blaze.config;

import java.time.Duration;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

	@Bean
	RestClient.Builder restClientBuilder() {
		var requestFactory = new JdkClientHttpRequestFactory();
		requestFactory.setReadTimeout(Duration.ofSeconds(10));
		return RestClient.builder().requestFactory(requestFactory);
	}

	@Bean
	RestClientCustomizer blazeRestClientDefaults() {
		return builder -> builder.defaultHeader("Accept", "application/json");
	}
}
