package com.example.jobqueue.application;

import com.example.jobqueue.domain.Job;
import com.example.jobqueue.domain.JobId;
import com.example.jobqueue.infrastructure.InMemoryJobRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.concurrent.CompletionException;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class JobProcessingService {

	private final InMemoryJobRepository jobRepository;
	private final JobQueueProperties jobQueueProperties;
	private final JobOrchestrationService jobOrchestrationService;
	private final DelayQueue<ScheduledJobEntry> scheduledQueue = new DelayQueue<>();
	private final PriorityBlockingQueue<ReadyJobEntry> readyQueue;
	private final Semaphore readySlots;
	private final AtomicLong sequence = new AtomicLong();
	private final AtomicInteger workerNumber = new AtomicInteger(1);

	private volatile boolean running = true;
	private Thread dispatcherThread;
	private ExecutorService workerPool;

	public JobProcessingService(
		InMemoryJobRepository jobRepository,
		JobQueueProperties jobQueueProperties,
		JobOrchestrationService jobOrchestrationService
	) {
		this.jobRepository = jobRepository;
		this.jobQueueProperties = jobQueueProperties;
		this.jobOrchestrationService = jobOrchestrationService;
		this.readyQueue = new PriorityBlockingQueue<>(jobQueueProperties.queueCapacity(), Comparator.naturalOrder());
		this.readySlots = new Semaphore(jobQueueProperties.queueCapacity());
	}

	@PostConstruct
	public void startWorkers() {
		dispatcherThread = new Thread(this::runDispatcherLoop, "job-scheduler");
		dispatcherThread.setDaemon(true);
		dispatcherThread.start();

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
		long jobSequence = sequence.incrementAndGet();

		if (job.delayMs() > 0) {
			scheduledQueue.offer(ScheduledJobEntry.of(job, jobSequence));
			return;
		}

		if (!acquireReadySlot(jobId, job.priority(), jobSequence)) {
			throw new QueueFullException(
				"Queue is full (capacity " + jobQueueProperties.queueCapacity() + "), job " + jobId + " stays PENDING"
			);
		}
	}

	private boolean acquireReadySlot(String jobId, int priority, long jobSequence) {
		boolean slotAcquired = switch (jobQueueProperties.rejectPolicy()) {
			case FAIL_FAST -> readySlots.tryAcquire();
			case BLOCK -> waitForSlot();
		};

		if (slotAcquired) {
			readyQueue.offer(new ReadyJobEntry(jobId, priority, jobSequence));
		}
		return slotAcquired;
	}

	private boolean waitForSlot() {
		try {
			return readySlots.tryAcquire(jobQueueProperties.enqueueTimeoutMs(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new QueueFullException("Interrupted while waiting for queue space", exception);
		}
	}

	public int workerCount() {
		return jobQueueProperties.workerCount();
	}

	public int backlog() {
		return readyQueue.size();
	}

	@PreDestroy
	public void stopWorkers() {
		running = false;
		if (dispatcherThread != null) {
			dispatcherThread.interrupt();
		}
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

	private void runDispatcherLoop() {
		while (running || !scheduledQueue.isEmpty()) {
			try {
				ScheduledJobEntry entry = scheduledQueue.poll(200, TimeUnit.MILLISECONDS);
				if (entry == null) {
					continue;
				}

				readySlots.acquire();
				readyQueue.offer(new ReadyJobEntry(entry.jobId(), entry.priority(), entry.sequence()));
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private void runWorkerLoop() {
		while (running || !readyQueue.isEmpty()) {
			try {
				ReadyJobEntry entry = readyQueue.poll(200, TimeUnit.MILLISECONDS);
				if (entry == null) {
					continue;
				}

				readySlots.release();
				processJob(new JobId(entry.jobId()));
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