package com.example.jobqueue.controller.dto;

public record SimulationRequest(
	int producerCount,
	int jobsPerProducer,
	int producerDelayMs,
	int workerDelayMs,
	SimulationMode mode
) {
}
