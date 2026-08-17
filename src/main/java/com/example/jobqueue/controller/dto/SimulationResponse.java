package com.example.jobqueue.controller.dto;

public record SimulationResponse(
	int totalJobs,
	int successfulJobs,
	int failedJobs,
	int rejectedCount,
	int producerCount,
	int workerDelayMs,
	int backlogMax,
	double backlogAvg,
	double throughputJobsPerSec
) {
}
