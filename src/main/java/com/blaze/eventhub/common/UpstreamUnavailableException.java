package com.blaze.eventhub.common;

public class UpstreamUnavailableException extends RuntimeException {

    public UpstreamUnavailableException(String message) {
        super(message);
    }
}
