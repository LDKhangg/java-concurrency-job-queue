package com.example.jobqueue.domain;

import java.time.Instant;

public record Job(
	JobId id,
	String type,
	JobStatus status,
	JobResult result,
	Instant createdAt,
	int processingDelayMs,
	int priority,
	int delayMs
) {

	public static final int DEFAULT_PRIORITY = 5;

	public static Job pending(JobId id, String type, int processingDelayMs) {
		return pending(id, type, processingDelayMs, DEFAULT_PRIORITY, 0);
	}

	public static Job pending(JobId id, String type, int processingDelayMs, int priority, int delayMs) {
		return new Job(id, type, JobStatus.PENDING, JobResult.pending(), Instant.now(), processingDelayMs, priority, delayMs);
	}

	public Job markRunning() {
		return new Job(id, type, JobStatus.RUNNING, result, createdAt, processingDelayMs, priority, delayMs);
	}

	public Job markSuccess() {
		return new Job(id, type, JobStatus.SUCCESS, JobResult.succeeded(), createdAt, processingDelayMs, priority, delayMs);
	}

	public Job markFailed(String errorMessage) {
		return new Job(id, type, JobStatus.FAILED, JobResult.failed(errorMessage), createdAt, processingDelayMs, priority, delayMs);
	}
}