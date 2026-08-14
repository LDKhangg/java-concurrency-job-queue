package com.example.jobqueue.domain;

import java.time.Instant;

public record Job(
	JobId id,
	String type,
	JobStatus status,
	JobResult result,
	Instant createdAt,
	int processingDelayMs
) {

	public static Job pending(JobId id, String type, int processingDelayMs) {
		return new Job(id, type, JobStatus.PENDING, JobResult.pending(), Instant.now(), processingDelayMs);
	}

	public Job markRunning() {
		return new Job(id, type, JobStatus.RUNNING, result, createdAt, processingDelayMs);
	}

	public Job markSuccess() {
		return new Job(id, type, JobStatus.SUCCESS, JobResult.succeeded(), createdAt, processingDelayMs);
	}

	public Job markFailed(String errorMessage) {
		return new Job(id, type, JobStatus.FAILED, JobResult.failed(errorMessage), createdAt, processingDelayMs);
	}
}
