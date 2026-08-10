package com.nollen.blaze.common;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.nollen.blaze.config.ApiSecurityProperties;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyFilterTest {

	private ApiKeyFilter filter(String apiKey) {
		ApiSecurityProperties props = new ApiSecurityProperties();
		props.setApiKey(apiKey);
		return new ApiKeyFilter(props);
	}

	@Test
	void publicPathsAreNotFiltered() {
		ApiKeyFilter filter = filter("test-key");
		assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/health"))).isTrue();
		assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/status"))).isTrue();
		assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/public/test"))).isTrue();
		assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/"))).isTrue();
		assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/overlay/abc"))).isTrue();
		assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/assets/app.js"))).isTrue();
	}

	@Test
	void adminApiPathsAreFiltered() {
		ApiKeyFilter filter = filter("test-key");
		assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/alerts"))).isFalse();
		assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/giveaways"))).isFalse();
		assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/blaze/setup"))).isFalse();
		assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/overlay-profiles"))).isFalse();
	}

	@Test
	void blankConfiguredKeyAllowsAllRequests() throws ServletException, IOException {
		ApiKeyFilter filter = filter("");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/alerts");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilterInternal(request, response, (req, resp) -> { });
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void rejectsRequestWithoutKey() throws ServletException, IOException {
		ApiKeyFilter filter = filter("test-key");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/alerts");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilterInternal(request, response, (req, resp) -> { });
		assertThat(response.getStatus()).isEqualTo(401);
	}

	@Test
	void acceptsRequestWithValidHeaderKey() throws ServletException, IOException {
		ApiKeyFilter filter = filter("test-key");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/alerts");
		request.addHeader(ApiKeyFilter.HEADER_NAME, "test-key");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilterInternal(request, response, (req, resp) -> { });
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void rejectsRequestWithWrongKey() throws ServletException, IOException {
		ApiKeyFilter filter = filter("test-key");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/alerts");
		request.addHeader(ApiKeyFilter.HEADER_NAME, "wrong-key");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilterInternal(request, response, (req, resp) -> { });
		assertThat(response.getStatus()).isEqualTo(401);
	}

	@Test
	void acceptsRequestWithBearerToken() throws ServletException, IOException {
		ApiKeyFilter filter = filter("test-key");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/alerts");
		request.addHeader("Authorization", "Bearer test-key");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilterInternal(request, response, (req, resp) -> { });
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void rejectsRequestWithWrongBearerToken() throws ServletException, IOException {
		ApiKeyFilter filter = filter("test-key");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/alerts");
		request.addHeader("Authorization", "Bearer wrong-key");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilterInternal(request, response, (req, resp) -> { });
		assertThat(response.getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsBearerWithWrongScheme() throws ServletException, IOException {
		ApiKeyFilter filter = filter("test-key");
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/alerts");
		request.addHeader("Authorization", "Basic test-key");
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilterInternal(request, response, (req, resp) -> { });
		assertThat(response.getStatus()).isEqualTo(401);
	}

	@Test
	void blankConfiguredKeyLogsStartupWarning() {
		ch.qos.logback.classic.Logger logger =
				(ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ApiKeyFilter.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			filter("");
			assertThat(appender.list).anySatisfy(event -> {
				assertThat(event.getLevel()).isEqualTo(Level.WARN);
				assertThat(event.getFormattedMessage()).contains("/api/** is OPEN");
			});
		} finally {
			logger.detachAppender(appender);
		}
	}
}
