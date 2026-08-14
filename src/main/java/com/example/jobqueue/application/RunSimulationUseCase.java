package com.example.jobqueue.application;

import com.example.jobqueue.controller.dto.SimulationRequest;
import com.example.jobqueue.controller.dto.SimulationResponse;
import org.springframework.stereotype.Service;

@Service
public class RunSimulationUseCase {

	public SimulationResponse handle(SimulationRequest request) {
		int totalJobs = request.producerCount() * request.jobsPerProducer();
		return new SimulationResponse(totalJobs, totalJobs, 0, request.producerCount(), request.workerDelayMs());
	}
}
