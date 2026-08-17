package com.example.jobqueue.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.job-queue")
public record JobQueueProperties(
	int workerCount,
	int defaultWorkerDelayMs,
	int stepTimeoutMs,
	int pipelineTimeoutMs,
	int retryCount,
	int queueCapacity,
	RejectPolicy rejectPolicy,
	int enqueueTimeoutMs
) {
}
