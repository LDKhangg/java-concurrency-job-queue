package com.example.jobqueue.domain;

public record JobResult(boolean success, String errorMessage) {

	public static JobResult pending() {
		return new JobResult(false, null);
	}

	public static JobResult succeeded() {
		return new JobResult(true, null);
	}

	public static JobResult failed(String errorMessage) {
		return new JobResult(false, errorMessage);
	}
}
