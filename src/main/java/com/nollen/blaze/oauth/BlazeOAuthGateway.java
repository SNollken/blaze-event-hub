package com.nollen.blaze.oauth;

public interface BlazeOAuthGateway {

	GeneratedAuthUrl generateAuthUrl(OAuthGenerateAuthUrlRequest request);

	OAuthTokenResponse exchangeCode(OAuthTokenExchangeRequest request);

	OAuthTokenResponse refresh(OAuthRefreshRequest request);
}
