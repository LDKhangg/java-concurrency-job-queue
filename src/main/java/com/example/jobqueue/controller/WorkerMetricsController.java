package com.example.jobqueue.controller;

import com.example.jobqueue.application.GetWorkerMetricsUseCase;
import com.example.jobqueue.controller.dto.WorkerMetricsResponse;
import com.example.jobqueue.domain.WorkerMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workers")
public class WorkerMetricsController {

	private final GetWorkerMetricsUseCase getWorkerMetricsUseCase;

	public WorkerMetricsController(GetWorkerMetricsUseCase getWorkerMetricsUseCase) {
		this.getWorkerMetricsUseCase = getWorkerMetricsUseCase;
	}

	@GetMapping("/metrics")
	public WorkerMetricsResponse metrics() {
		WorkerMetrics metrics = getWorkerMetricsUseCase.handle();
		return new WorkerMetricsResponse(
			metrics.pendingJobs(),
			metrics.runningJobs(),
			metrics.successfulJobs(),
			metrics.failedJobs(),
			metrics.workerCount(),
			metrics.queueDepth()
		);
	}
}
