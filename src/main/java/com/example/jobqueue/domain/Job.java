package com.example.jobqueue.domain;

import java.time.Instant;

public record Job(
	JobId id,
	String type,
	JobStatus status,
	JobResult result,
	Instant createdAt
) {

	public static Job pending(JobId id, String type) {
		return new Job(id, type, JobStatus.PENDING, JobResult.pending(), Instant.now());
	}
}
