package com.blaze.eventhub.events;

public interface BlazeEventsClient {

	void start();

	void stop();

	boolean isRunning();
}
