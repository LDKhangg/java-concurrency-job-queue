package com.example.jobqueue.controller.dto;

public record WorkerMetricsResponse(
	int pendingJobs,
	int runningJobs,
	int successfulJobs,
	int failedJobs,
	int workerCount
) {
}
