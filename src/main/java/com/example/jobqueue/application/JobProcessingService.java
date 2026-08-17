package com.example.jobqueue.application;

import com.example.jobqueue.domain.Job;
import com.example.jobqueue.domain.JobId;
import com.example.jobqueue.infrastructure.InMemoryJobRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class JobProcessingService {

	private final InMemoryJobRepository jobRepository;
	private final JobQueueProperties jobQueueProperties;
	private final JobOrchestrationService jobOrchestrationService;
	private final BlockingQueue<String> queue;
	private final AtomicInteger workerNumber = new AtomicInteger(1);

	private volatile boolean running = true;
	private ExecutorService workerPool;

	public JobProcessingService(
		InMemoryJobRepository jobRepository,
		JobQueueProperties jobQueueProperties,
		JobOrchestrationService jobOrchestrationService
	) {
		this.jobRepository = jobRepository;
		this.jobQueueProperties = jobQueueProperties;
		this.jobOrchestrationService = jobOrchestrationService;
		this.queue = new ArrayBlockingQueue<>(jobQueueProperties.queueCapacity());
	}

	@PostConstruct
	public void startWorkers() {
		workerPool = Executors.newFixedThreadPool(jobQueueProperties.workerCount(), runnable -> {
			Thread thread = new Thread(runnable);
			thread.setName("job-worker-" + workerNumber.getAndIncrement());
			thread.setDaemon(true);
			return thread;
		});

		for (int index = 0; index < jobQueueProperties.workerCount(); index++) {
			workerPool.submit(this::runWorkerLoop);
		}
	}

	public void enqueue(Job job) {
		String jobId = job.id().value();
		boolean accepted = switch (jobQueueProperties.rejectPolicy()) {
			case FAIL_FAST -> queue.offer(jobId);
			case BLOCK -> offerWithTimeout(jobId);
		};

		if (!accepted) {
			throw new QueueFullException(
				"Queue is full (capacity " + jobQueueProperties.queueCapacity() + "), job " + jobId + " stays PENDING"
			);
		}
	}

	private boolean offerWithTimeout(String jobId) {
		try {
			return queue.offer(jobId, jobQueueProperties.enqueueTimeoutMs(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new QueueFullException("Interrupted while waiting for queue space", exception);
		}
	}

	public int workerCount() {
		return jobQueueProperties.workerCount();
	}

	public int backlog() {
		return queue.size();
	}

	@PreDestroy
	public void stopWorkers() {
		running = false;
		if (workerPool == null) {
			return;
		}

		workerPool.shutdown();
		try {
			if (!workerPool.awaitTermination(10, TimeUnit.SECONDS)) {
				workerPool.shutdownNow();
				workerPool.awaitTermination(5, TimeUnit.SECONDS);
			}
		} catch (InterruptedException exception) {
			workerPool.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	private void runWorkerLoop() {
		while (running || !queue.isEmpty()) {
			try {
				String jobId = queue.poll(200, TimeUnit.MILLISECONDS);
				if (jobId == null) {
					continue;
				}

				processJob(new JobId(jobId));
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private void processJob(JobId jobId) {
		jobRepository.update(jobId, Job::markRunning);
		Job job = jobRepository.findById(jobId).orElse(null);
		if (job == null) {
			return;
		}

		try {
			jobOrchestrationService.run(job).join();
			jobRepository.update(jobId, Job::markSuccess);
		} catch (RuntimeException exception) {
			String errorMessage = extractMessage(exception);
			jobRepository.update(jobId, existingJob -> existingJob.markFailed(errorMessage));
		}
	}

	private String extractMessage(Throwable throwable) {
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
		return "Unknown processing failure";
	}
}
