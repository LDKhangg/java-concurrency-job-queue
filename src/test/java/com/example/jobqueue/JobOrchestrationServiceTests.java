package com.example.jobqueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.jobqueue.application.JobOrchestrationService;
import com.example.jobqueue.application.JobQueueProperties;
import com.example.jobqueue.domain.Job;
import com.example.jobqueue.domain.JobId;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class JobOrchestrationServiceTests {

	@Test
	void shouldRetryFlakyEnrichAndEventuallySucceed() {
		try (JobOrchestrationService orchestrationService = new JobOrchestrationService(new JobQueueProperties(2, 50, 80, 300, 1))) {
			Job job = Job.pending(JobId.newId(), "flaky-enrich", 20);

			String finalPayload = orchestrationService.run(job).join();

			assertEquals("validated:processed:enriched:finalized", finalPayload);
		}
	}

	@Test
	void shouldFailWhenEnrichRetriesAreExhausted() {
		try (JobOrchestrationService orchestrationService = new JobOrchestrationService(new JobQueueProperties(2, 50, 80, 300, 1))) {
			Job job = Job.pending(JobId.newId(), "fail-enrich", 20);

			CompletionException exception = assertThrows(CompletionException.class, () -> orchestrationService.run(job).join());

			assertEquals("Step failed: enrich - Enrich step failed", failureMessage(exception));
		}
	}

	@Test
	void shouldFailWhenSingleStepTimesOut() {
		try (JobOrchestrationService orchestrationService = new JobOrchestrationService(new JobQueueProperties(2, 50, 60, 300, 1))) {
			Job job = Job.pending(JobId.newId(), "timeout-process", 20);

			CompletionException exception = assertThrows(CompletionException.class, () -> orchestrationService.run(job).join());

			assertEquals("Step timed out: process", failureMessage(exception));
		}
	}

	@Test
	void shouldFailWhenWholePipelineTimesOut() {
		try (JobOrchestrationService orchestrationService = new JobOrchestrationService(new JobQueueProperties(2, 50, 200, 90, 1))) {
			Job job = Job.pending(JobId.newId(), "slow-pipeline", 40);

			CompletionException exception = assertThrows(CompletionException.class, () -> orchestrationService.run(job).join());

			assertEquals("Pipeline timed out", failureMessage(exception));
		}
	}

	private String failureMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current instanceof CompletionException && current.getCause() != null) {
			current = current.getCause();
		}

		while (current != null) {
			if (current.getMessage() != null && !current.getMessage().isBlank()) {
				return current.getMessage();
			}

			current = current.getCause();
		}

		return null;
	}
}
