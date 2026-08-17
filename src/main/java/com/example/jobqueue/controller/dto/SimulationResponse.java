package com.example.jobqueue.controller.dto;

public record SimulationResponse(
	int totalJobs,
	int successfulJobs,
	int failedJobs,
	int producerCount,
	int workerDelayMs
) {
}
