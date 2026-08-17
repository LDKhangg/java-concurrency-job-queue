package com.example.jobqueue.application;

public class QueueFullException extends RuntimeException {

	public QueueFullException(String message) {
		super(message);
	}

	public QueueFullException(String message, Throwable cause) {
		super(message, cause);
	}
}