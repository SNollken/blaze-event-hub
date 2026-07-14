package com.blaze.eventhub.config;

import java.time.Duration;
import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

	@Bean
	RestClient.Builder restClientBuilder() {
		var httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		var requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(10));
		return RestClient.builder()
				.requestFactory(requestFactory)
				.defaultHeader("Accept", "application/json");
	}
}
