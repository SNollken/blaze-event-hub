package com.nollen.blaze.common;

/**
 * Exceção pública para erros controlados do OAuth (callback, start, etc).
 * Carrega httpStatus, code e message para o GlobalExceptionHandler renderizar
 * como resposta amigável em vez de 500 genérico.
 */
public class OAuthException extends RuntimeException {

    private final int httpStatus;
    private final String errorCode;

    public OAuthException(int httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
