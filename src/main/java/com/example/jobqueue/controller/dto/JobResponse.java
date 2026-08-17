package com.example.jobqueue.controller.dto;

import java.time.Instant;

public record JobResponse(
	String jobId,
	String type,
	String status,
	String errorMessage,
	int priority,
	int delayMs,
	Instant createdAt
) {
}