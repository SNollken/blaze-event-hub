package com.blaze.eventhub;

import com.blaze.eventhub.oauth.InMemoryOAuthProfileStore;
import com.blaze.eventhub.oauth.InMemoryTokenStore;
import com.blaze.eventhub.oauth.OAuthProfileStore;
import com.blaze.eventhub.oauth.TokenStore;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Fornece stores InMemory para testes de controller, sobrescrevendo os JDBC.
 * Evita BadSqlGrammarException quando o schema.sql não é aplicado no contexto
 * de teste compartilhado. Os stores JDBC são testados em integração separada.
 */
@TestConfiguration
public class InMemoryStoreTestConfig {

	@Bean
	@Primary
	public TokenStore inMemoryTokenStore() {
		return new InMemoryTokenStore();
	}

	@Bean
	@Primary
	public OAuthProfileStore inMemoryOAuthProfileStore() {
		return new InMemoryOAuthProfileStore();
	}
}
