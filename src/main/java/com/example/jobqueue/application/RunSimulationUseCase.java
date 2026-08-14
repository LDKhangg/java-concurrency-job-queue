package com.example.jobqueue.application;

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

@Service
public class RunSimulationUseCase {

	private final SubmitJobUseCase submitJobUseCase;
	private final InMemoryJobRepository jobRepository;

	public RunSimulationUseCase(SubmitJobUseCase submitJobUseCase, InMemoryJobRepository jobRepository) {
		this.submitJobUseCase = submitJobUseCase;
		this.jobRepository = jobRepository;
	}

	public SimulationResponse handle(SimulationRequest request) {
		int totalJobs = request.producerCount() * request.jobsPerProducer();
		ConcurrentLinkedQueue<String> submittedJobIds = new ConcurrentLinkedQueue<>();
		ExecutorService producers = Executors.newFixedThreadPool(Math.max(1, request.producerCount()));
		CountDownLatch finishedProducers = new CountDownLatch(request.producerCount());

		for (int producerIndex = 0; producerIndex < request.producerCount(); producerIndex++) {
			producers.submit(() -> {
				try {
					for (int jobIndex = 0; jobIndex < request.jobsPerProducer(); jobIndex++) {
						Job job = submitJobUseCase.handle("simulation", request.workerDelayMs());
						submittedJobIds.add(job.id().value());
						pause(request.producerDelayMs());
					}
				} finally {
					finishedProducers.countDown();
				}
			});
		}

		await(finishedProducers, Duration.ofSeconds(5));
		producers.shutdownNow();
		awaitTerminalJobs(new ArrayList<>(submittedJobIds), Duration.ofSeconds(10));

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

		return new SimulationResponse(totalJobs, successfulJobs, failedJobs, request.producerCount(), request.workerDelayMs());
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
}
