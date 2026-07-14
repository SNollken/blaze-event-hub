package com.blaze.eventhub.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.ResponseCookie;

@ConfigurationProperties(prefix = "server.servlet.session.cookie")
public class SessionCookiePolicy {

	private String name = "JSESSIONID";
	private String path = "/";
	private boolean httpOnly = true;
	private boolean secure = true;
	private String sameSite = "Lax";

	public ResponseCookie expiredCookie() {
		return ResponseCookie.from(name, "")
				.path(path)
				.maxAge(Duration.ZERO)
				.httpOnly(httpOnly)
				.secure(secure)
				.sameSite(sameSite)
				.build();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public boolean isHttpOnly() {
		return httpOnly;
	}

	public void setHttpOnly(boolean httpOnly) {
		this.httpOnly = httpOnly;
	}

	public boolean isSecure() {
		return secure;
	}

	public void setSecure(boolean secure) {
		this.secure = secure;
	}

	public String getSameSite() {
		return sameSite;
	}

	public void setSameSite(String sameSite) {
		this.sameSite = sameSite;
	}
}
