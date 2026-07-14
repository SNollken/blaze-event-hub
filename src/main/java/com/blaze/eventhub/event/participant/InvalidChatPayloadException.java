package com.blaze.eventhub.event.participant;

public class InvalidChatPayloadException extends RuntimeException {

    public InvalidChatPayloadException(String message) {
        super(message);
    }
}
