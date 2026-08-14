package com.example.jobqueue.domain;

public record JobResult(boolean success, String errorMessage) {

	public static JobResult pending() {
		return new JobResult(false, null);
	}
}
