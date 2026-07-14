package com.blaze.eventhub.events;

public class ChatHistoryGapException extends RuntimeException {

    public ChatHistoryGapException(String message) {
        super(message);
    }
}
