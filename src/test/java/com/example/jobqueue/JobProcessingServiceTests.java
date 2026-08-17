package com.example.jobqueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JobProcessingServiceTests {

	@Test
	void shouldRejectWhenQueueIsFullWithFailFast() {
		JobQueueProperties props = new JobQueueProperties(2, 50, 150, 500, 1, 2, RejectPolicy.FAIL_FAST, 5000);
		try (JobOrchestrationService orchestrationService = new JobOrchestrationService(props)) {
			JobProcessingService service = new JobProcessingService(new InMemoryJobRepository(), props, orchestrationService);

			service.enqueue(job("one"));
			service.enqueue(job("two"));

			QueueFullException exception = assertThrows(QueueFullException.class, () -> service.enqueue(job("three")));

			assertTrue(exception.getMessage().contains("Queue is full"));
		}
	}

	@Test
	void shouldBlockProducerUntilQueueHasSpace() throws InterruptedException {
		JobQueueProperties props = new JobQueueProperties(1, 10, 150, 500, 1, 1, RejectPolicy.BLOCK, 5000);
		try (JobOrchestrationService orchestrationService = new JobOrchestrationService(props)) {
			JobProcessingService service = new JobProcessingService(new InMemoryJobRepository(), props, orchestrationService);
			service.enqueue(job("one"));

			ExecutorService producer = Executors.newSingleThreadExecutor();
			CountDownLatch enqueued = new CountDownLatch(1);
			AtomicReference<Throwable> failure = new AtomicReference<>();
			producer.submit(() -> {
				try {
					service.enqueue(job("two"));
				} catch (Throwable throwable) {
					failure.set(throwable);
				} finally {
					enqueued.countDown();
				}
			});

			assertEquals(false, enqueued.await(200, TimeUnit.MILLISECONDS), "producer should stay blocked while queue is full");

			service.startWorkers();
			assertTrue(enqueued.await(10, TimeUnit.SECONDS), "producer should complete once a worker frees space");
			assertEquals(null, failure.get());
			producer.shutdownNow();
			service.stopWorkers();
		}
	}

	@Test
	void shouldTimeoutWhenQueueStaysFullWithBlock() {
		JobQueueProperties props = new JobQueueProperties(1, 10, 150, 500, 1, 1, RejectPolicy.BLOCK, 100);
		try (JobOrchestrationService orchestrationService = new JobOrchestrationService(props)) {
			JobProcessingService service = new JobProcessingService(new InMemoryJobRepository(), props, orchestrationService);
			service.enqueue(job("one"));

			QueueFullException exception = assertThrows(QueueFullException.class, () -> service.enqueue(job("two")));

			assertTrue(exception.getMessage().contains("Queue is full"));
		}
	}

	@Test
	void shouldDrainPendingJobsOnGracefulShutdown() {
		JobQueueProperties props = new JobQueueProperties(1, 10, 150, 500, 1, 10, RejectPolicy.BLOCK, 5000);
		InMemoryJobRepository repository = new InMemoryJobRepository();
		try (JobOrchestrationService orchestrationService = new JobOrchestrationService(props)) {
			JobProcessingService service = new JobProcessingService(repository, props, orchestrationService);
			service.startWorkers();

			List<String> jobIds = new ArrayList<>();
			for (int index = 0; index < 5; index++) {
				Job job = Job.pending(JobId.newId(), "simulation", 10);
				repository.save(job);
				service.enqueue(job);
				jobIds.add(job.id().value());
			}

			service.stopWorkers();

			for (String jobId : jobIds) {
				Job job = repository.findById(new JobId(jobId)).orElseThrow();
				assertTrue(
					job.status() == JobStatus.SUCCESS || job.status() == JobStatus.FAILED,
					"job " + jobId + " should reach a terminal state: " + job.status()
				);
			}
		}
	}

	private Job job(String type) {
		return Job.pending(JobId.newId(), type, 10);
	}
}
