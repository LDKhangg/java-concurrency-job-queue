package com.example.jobqueue.domain;

public record WorkerMetrics(
	int pendingJobs,
	int runningJobs,
	int successfulJobs,
	int failedJobs,
	int workerCount
) {
}
