package com.example.jobqueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.jobqueue.application.JobOrchestrationService;
import com.example.jobqueue.application.JobProcessingService;
import com.example.jobqueue.application.JobQueueProperties;
import com.example.jobqueue.application.RejectPolicy;
import com.example.jobqueue.application.RunSimulationUseCase;
import com.example.jobqueue.application.SubmitJobUseCase;
import com.example.jobqueue.controller.dto.SimulationMode;
import com.example.jobqueue.controller.dto.SimulationRequest;
import com.example.jobqueue.controller.dto.SimulationResponse;
import com.example.jobqueue.infrastructure.InMemoryJobRepository;
import org.junit.jupiter.api.Test;

class RunSimulationUseCaseTests {

	@Test
	void shouldRunBurstSimulationAndReportBacklogAndThroughput() {
		JobQueueProperties props = new JobQueueProperties(1, 10, 500, 2000, 0, 5, RejectPolicy.BLOCK, 2000);
		InMemoryJobRepository repository = new InMemoryJobRepository();
		try (JobOrchestrationService orchestrationService = new JobOrchestrationService(props)) {
			JobProcessingService processingService = new JobProcessingService(repository, props, orchestrationService);
			processingService.startWorkers();
			try {
				RunSimulationUseCase useCase = new RunSimulationUseCase(
					new SubmitJobUseCase(repository, processingService, props),
					processingService,
					repository
				);

				SimulationResponse response = useCase.handle(new SimulationRequest(2, 10, 1000, 10, SimulationMode.BURST));

				assertEquals(20, response.totalJobs());
				assertEquals(20, response.successfulJobs());
				assertEquals(0, response.failedJobs());
				assertEquals(0, response.rejectedCount());
				assertTrue(response.backlogMax() >= 1, "burst should fill the queue");
				assertTrue(response.throughputJobsPerSec() > 0);
			} finally {
				processingService.stopWorkers();
			}
		}
	}

	@Test
	void shouldDefaultToSteadyModeWhenModeIsNull() {
		JobQueueProperties props = new JobQueueProperties(1, 10, 500, 2000, 0, 10, RejectPolicy.BLOCK, 2000);
		InMemoryJobRepository repository = new InMemoryJobRepository();
		try (JobOrchestrationService orchestrationService = new JobOrchestrationService(props)) {
			JobProcessingService processingService = new JobProcessingService(repository, props, orchestrationService);
			processingService.startWorkers();
			try {
				RunSimulationUseCase useCase = new RunSimulationUseCase(
					new SubmitJobUseCase(repository, processingService, props),
					processingService,
					repository
				);

				SimulationResponse response = useCase.handle(new SimulationRequest(2, 10, 15, 10, null));

				assertEquals(20, response.totalJobs());
				assertEquals(20, response.successfulJobs());
				assertEquals(0, response.rejectedCount());
			} finally {
				processingService.stopWorkers();
			}
		}
	}

	@Test
	void shouldCountRejectedJobsWhenQueueIsFull() {
		JobQueueProperties props = new JobQueueProperties(1, 10, 500, 2000, 0, 1, RejectPolicy.BLOCK, 10);
		InMemoryJobRepository repository = new InMemoryJobRepository();
		try (JobOrchestrationService orchestrationService = new JobOrchestrationService(props)) {
			JobProcessingService processingService = new JobProcessingService(repository, props, orchestrationService);
			processingService.startWorkers();
			try {
				RunSimulationUseCase useCase = new RunSimulationUseCase(
					new SubmitJobUseCase(repository, processingService, props),
					processingService,
					repository
				);

				SimulationResponse response = useCase.handle(new SimulationRequest(1, 12, 0, 150, SimulationMode.BURST));

				assertEquals(12, response.totalJobs());
				assertTrue(response.rejectedCount() > 0, "worker is slower than producer, rejects expected");
				assertTrue(response.successfulJobs() > 0);
				assertEquals(12, response.successfulJobs() + response.failedJobs() + response.rejectedCount());
			} finally {
				processingService.stopWorkers();
			}
		}
	}
}