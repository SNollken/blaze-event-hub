package com.nollen.blaze.config;

import java.net.http.HttpClient;
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
		// Connect timeout is set on the JDK HttpClient: JdkClientHttpRequestFactory
		// only exposes readTimeout, and the default HttpClient has NO connect
		// timeout — a blackholed Blaze endpoint would pin Tomcat threads until the
		// OS TCP timeout (~2+ min).
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.build();
		var requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(10));
		return RestClient.builder().requestFactory(requestFactory);
	}

	@Bean
	RestClientCustomizer blazeRestClientDefaults() {
		return builder -> builder.defaultHeader("Accept", "application/json");
	}
}
