package com.example.jobqueue.application;

import com.example.jobqueue.controller.dto.SimulationMode;
import com.example.jobqueue.controller.dto.SimulationRequest;
import com.example.jobqueue.controller.dto.SimulationResponse;
import com.example.jobqueue.domain.Job;
import com.example.jobqueue.domain.JobId;
import com.example.jobqueue.domain.JobStatus;
import com.example.jobqueue.infrastructure.InMemoryJobRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RunSimulationUseCase {

	private final SubmitJobUseCase submitJobUseCase;
	private final JobProcessingService jobProcessingService;
	private final InMemoryJobRepository jobRepository;

	public RunSimulationUseCase(
		SubmitJobUseCase submitJobUseCase,
		JobProcessingService jobProcessingService,
		InMemoryJobRepository jobRepository
	) {
		this.submitJobUseCase = submitJobUseCase;
		this.jobProcessingService = jobProcessingService;
		this.jobRepository = jobRepository;
	}

	public SimulationResponse handle(SimulationRequest request) {
		int totalJobs = request.producerCount() * request.jobsPerProducer();
		int producerDelayMs = request.mode() == SimulationMode.BURST ? 0 : request.producerDelayMs();
		ConcurrentLinkedQueue<String> submittedJobIds = new ConcurrentLinkedQueue<>();
		AtomicInteger rejectedCount = new AtomicInteger();

		ExecutorService producers = Executors.newFixedThreadPool(Math.max(1, request.producerCount()));
		CountDownLatch finishedProducers = new CountDownLatch(request.producerCount());
		long startedAt = System.nanoTime();

		BacklogSampler backlogSampler = new BacklogSampler();
		backlogSampler.start();

		for (int producerIndex = 0; producerIndex < request.producerCount(); producerIndex++) {
			producers.submit(() -> {
				try {
					for (int jobIndex = 0; jobIndex < request.jobsPerProducer(); jobIndex++) {
						try {
							Job job = submitJobUseCase.handle("simulation", request.workerDelayMs());
							submittedJobIds.add(job.id().value());
						} catch (QueueFullException exception) {
							rejectedCount.incrementAndGet();
						}
						pause(producerDelayMs);
					}
				} finally {
					finishedProducers.countDown();
				}
			});
		}

		await(finishedProducers, Duration.ofSeconds(5));
		producers.shutdownNow();
		awaitTerminalJobs(new ArrayList<>(submittedJobIds), Duration.ofSeconds(10));
		backlogSampler.stop();
		double elapsedSeconds = Duration.ofNanos(System.nanoTime() - startedAt).toMillis() / 1000.0;

		int successfulJobs = 0;
		int failedJobs = 0;
		for (String jobId : submittedJobIds) {
			Job job = jobRepository.findById(new JobId(jobId)).orElseThrow();
			if (job.status() == JobStatus.SUCCESS) {
				successfulJobs++;
			} else if (job.status() == JobStatus.FAILED) {
				failedJobs++;
			}
		}

		return new SimulationResponse(
			totalJobs,
			successfulJobs,
			failedJobs,
			rejectedCount.get(),
			request.producerCount(),
			request.workerDelayMs(),
			backlogSampler.maxBacklog(),
			backlogSampler.averageBacklog(),
			elapsedSeconds > 0 ? totalJobs / elapsedSeconds : 0
		);
	}

	private void awaitTerminalJobs(List<String> submittedJobIds, Duration timeout) {
		Instant deadline = Instant.now().plus(timeout);
		while (Instant.now().isBefore(deadline)) {
			boolean allTerminal = submittedJobIds.stream()
				.map(jobId -> jobRepository.findById(new JobId(jobId)).orElseThrow())
				.allMatch(job -> job.status() == JobStatus.SUCCESS || job.status() == JobStatus.FAILED);

			if (allTerminal) {
				return;
			}

			pause(25);
		}

		throw new IllegalStateException("Simulation did not finish before timeout");
	}

	private void await(CountDownLatch latch, Duration timeout) {
		try {
			if (!latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				throw new IllegalStateException("Simulation producers did not finish before timeout");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Simulation was interrupted", exception);
		}
	}

	private void pause(int delayMs) {
		if (delayMs <= 0) {
			return;
		}

		try {
			Thread.sleep(delayMs);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Simulation was interrupted", exception);
		}
	}

	private class BacklogSampler {

		private final AtomicLong backlogSum = new AtomicLong();
		private final AtomicInteger backlogSamples = new AtomicInteger();
		private final AtomicInteger backlogMax = new AtomicInteger();
		private Thread samplerThread;

		void start() {
			samplerThread = new Thread(this::sampleLoop);
			samplerThread.setName("backlog-sampler");
			samplerThread.setDaemon(true);
			samplerThread.start();
		}

		void stop() {
			samplerThread.interrupt();
		}

		int maxBacklog() {
			return backlogMax.get();
		}

		double averageBacklog() {
			int samples = backlogSamples.get();
			return samples == 0 ? 0 : (double) backlogSum.get() / samples;
		}

		private void sampleLoop() {
			try {
				while (true) {
					int depth = jobProcessingService.backlog();
					backlogSum.addAndGet(depth);
					backlogSamples.incrementAndGet();
					backlogMax.updateAndGet(current -> Math.max(current, depth));
					pause(10);
				}
			} catch (IllegalStateException ignored) {
				Thread.currentThread().interrupt();
			}
		}
	}
}
