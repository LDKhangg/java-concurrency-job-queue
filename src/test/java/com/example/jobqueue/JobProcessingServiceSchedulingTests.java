package com.example.jobqueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.jobqueue.application.JobOrchestrationService;
import com.example.jobqueue.application.JobProcessingService;
import com.example.jobqueue.application.JobQueueProperties;
import com.example.jobqueue.application.QueueFullException;
import com.example.jobqueue.application.RejectPolicy;
import com.example.jobqueue.domain.Job;
import com.example.jobqueue.domain.JobId;
import com.example.jobqueue.domain.JobStatus;
import com.example.jobqueue.infrastructure.InMemoryJobRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobProcessingServiceSchedulingTests {

	@Test
	void shouldNotProcessDelayedJobBeforeItsDelayElapses() {
		JobQueueProperties props = new JobQueueProperties(1, 10, 150, 500, 1, 10, RejectPolicy.BLOCK, 5000);
		InMemoryJobRepository repository = new InMemoryJobRepository();
		try (JobOrchestrationService orchestrationService = new JobOrchestrationService(props)) {
			JobProcessingService service = new JobProcessingService(repository, props, orchestrationService);
			service.startWorkers();
			try {
				Job delayed = Job.pending(JobId.newId(), "simulation", 10, 5, 600);
				repository.save(delayed);
				service.enqueue(delayed);

				pause(250);
				Job afterShortWait = repository.findById(delayed.id()).orElseThrow();
				assertEquals(JobStatus.PENDING, afterShortWait.status(), "delayed job must not start before its delay");

				awaitTerminal(repository, delayed.id(), Duration.ofSeconds(5));
			} finally {
				service.stopWorkers();
			}
		}
	}

	@Test
	void shouldProcessHigherPriorityJobFirst() {
		JobQueueProperties props = new JobQueueProperties(1, 10, 150, 500, 1, 10, RejectPolicy.BLOCK, 5000);
		InMemoryJobRepository repository = new InMemoryJobRepository();
		try (JobOrchestrationService orchestrationService = new JobOrchestrationService(props)) {
			JobProcessingService service = new JobProcessingService(repository, props, orchestrationService);

			Job lowPriority = Job.pending(JobId.newId(), "simulation", 10, 1, 0);
			Job highPriority = Job.pending(JobId.newId(), "simulation", 10, 10, 0);
			repository.save(lowPriority);
			repository.save(highPriority);
			service.enqueue(lowPriority);
			service.enqueue(highPriority);

			service.startWorkers();
			try {
				assertTrue(waitForRunning(repository, lowPriority.id(), highPriority.id()) == highPriority.id(),
					"higher priority job should be picked up first");
			} finally {
				service.stopWorkers();
			}
		}
	}

	@Test
	void shouldPreserveFifoOrderForEqualPriority() {
		JobQueueProperties props = new JobQueueProperties(1, 50, 150, 500, 1, 10, RejectPolicy.BLOCK, 5000);
		InMemoryJobRepository repository = new InMemoryJobRepository();
		try (JobOrchestrationService orchestrationService = new JobOrchestrationService(props)) {
			JobProcessingService service = new JobProcessingService(repository, props, orchestrationService);

			Job first = Job.pending(JobId.newId(), "simulation", 50, 5, 0);
			Job second = Job.pending(JobId.newId(), "simulation", 50, 5, 0);
			repository.save(first);
			repository.save(second);
			service.enqueue(first);
			service.enqueue(second);

			service.startWorkers();
			try {
				assertEquals(first.id(), waitForRunning(repository, first.id(), second.id()),
					"first enqueued job should be picked up first at equal priority");
			} finally {
				service.stopWorkers();
			}
		}
	}

	@Test
	void shouldNotRejectDelayedJobWhenReadyQueueIsFull() {
		JobQueueProperties props = new JobQueueProperties(1, 10, 150, 500, 1, 1, RejectPolicy.FAIL_FAST, 5000);
		InMemoryJobRepository repository = new InMemoryJobRepository();
		try (JobOrchestrationService orchestrationService = new JobOrchestrationService(props)) {
			JobProcessingService service = new JobProcessingService(repository, props, orchestrationService);

			Job immediate = Job.pending(JobId.newId(), "simulation", 10, 5, 0);
			Job delayed = Job.pending(JobId.newId(), "simulation", 10, 5, 300);
			repository.save(immediate);
			repository.save(delayed);
			service.enqueue(immediate);

			assertDoesNotThrow(() -> service.enqueue(delayed), "delayed jobs wait in the scheduled queue, never rejected");

			Job anotherImmediate = Job.pending(JobId.newId(), "simulation", 10, 5, 0);
			repository.save(anotherImmediate);
			assertThrows(QueueFullException.class, () -> service.enqueue(anotherImmediate));

			service.startWorkers();
			try {
				awaitTerminal(repository, immediate.id(), Duration.ofSeconds(5));
				awaitTerminal(repository, delayed.id(), Duration.ofSeconds(5));
			} finally {
				service.stopWorkers();
			}
		}
	}

	@Test
	void shouldKeepPriorityWhenDelayedJobBecomesReady() {
		JobQueueProperties props = new JobQueueProperties(1, 10, 150, 500, 1, 10, RejectPolicy.BLOCK, 5000);
		InMemoryJobRepository repository = new InMemoryJobRepository();
		try (JobOrchestrationService orchestrationService = new JobOrchestrationService(props)) {
			JobProcessingService service = new JobProcessingService(repository, props, orchestrationService);

			Job blocker = Job.pending(JobId.newId(), "simulation", 300, 5, 0);
			Job lowPriority = Job.pending(JobId.newId(), "simulation", 10, 1, 0);
			Job highPriorityDelayed = Job.pending(JobId.newId(), "simulation", 10, 10, 150);
			repository.save(blocker);
			repository.save(lowPriority);
			repository.save(highPriorityDelayed);

			service.startWorkers();
			try {
				service.enqueue(blocker);
				service.enqueue(lowPriority);
				service.enqueue(highPriorityDelayed);

				assertEquals(highPriorityDelayed.id(), waitForRunning(repository, lowPriority.id(), highPriorityDelayed.id()),
					"delayed high-priority job should overtake the ready low-priority job");
			} finally {
				service.stopWorkers();
			}
		}
	}

	private JobId waitForRunning(InMemoryJobRepository repository, JobId first, JobId second) {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
		while (Instant.now().isBefore(deadline)) {
			Job firstJob = repository.findById(first).orElseThrow();
			Job secondJob = repository.findById(second).orElseThrow();
			if (firstJob.status() == JobStatus.RUNNING) {
				return first;
			}
			if (secondJob.status() == JobStatus.RUNNING) {
				return second;
			}
			pause(10);
		}
		throw new IllegalStateException("no job reached RUNNING before timeout");
	}

	private void awaitTerminal(InMemoryJobRepository repository, JobId jobId, Duration timeout) {
		Instant deadline = Instant.now().plus(timeout);
		while (Instant.now().isBefore(deadline)) {
			JobStatus status = repository.findById(jobId).orElseThrow().status();
			if (status == JobStatus.SUCCESS || status == JobStatus.FAILED) {
				return;
			}
			pause(25);
		}
		throw new IllegalStateException("job " + jobId.value() + " did not reach terminal state before timeout");
	}

	private void pause(int delayMs) {
		try {
			Thread.sleep(delayMs);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("test interrupted", exception);
		}
	}
}