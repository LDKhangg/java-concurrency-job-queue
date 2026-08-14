package com.example.jobqueue.application;

import com.example.jobqueue.domain.Job;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class JobOrchestrationService implements AutoCloseable {

	private final JobQueueProperties jobQueueProperties;
	private final ExecutorService pipelineExecutor;
	private final AtomicInteger threadNumber = new AtomicInteger(1);
	private final ConcurrentHashMap<String, AtomicInteger> enrichAttempts = new ConcurrentHashMap<>();

	public JobOrchestrationService(JobQueueProperties jobQueueProperties) {
		this.jobQueueProperties = jobQueueProperties;
		this.pipelineExecutor = Executors.newFixedThreadPool(Math.max(2, jobQueueProperties.workerCount()), runnable -> {
			Thread thread = new Thread(runnable);
			thread.setName("job-pipeline-" + threadNumber.getAndIncrement());
			thread.setDaemon(true);
			return thread;
		});
	}

	public CompletableFuture<String> run(Job job) {
		CompletableFuture<String> pipeline = runStep("validate", () -> validate(job))
			.thenCompose(validatedPayload -> runRetriableStep("process", job, () -> process(validatedPayload, job), 0))
			.thenCompose(processedPayload -> runRetriableStep("enrich", job, () -> enrich(processedPayload, job), 0))
			.thenCompose(enrichedPayload -> runStep("finalize", () -> finalizePayload(enrichedPayload, job)));

		return pipeline.orTimeout(jobQueueProperties.pipelineTimeoutMs(), TimeUnit.MILLISECONDS)
			.handle((result, throwable) -> {
				if (throwable == null) {
					return CompletableFuture.completedFuture(result);
				}

				Throwable cause = unwrap(throwable);
				if (cause instanceof TimeoutException) {
					return CompletableFuture.<String>failedFuture(new IllegalStateException("Pipeline timed out", cause));
				}

				return CompletableFuture.<String>failedFuture(asRuntimeException(cause));
			})
			.thenCompose(Function.identity())
			.whenComplete((ignored, throwable) -> enrichAttempts.remove(job.id().value()));
	}

	@PreDestroy
	@Override
	public void close() {
		pipelineExecutor.shutdownNow();
		try {
			pipelineExecutor.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
	}

	private CompletableFuture<String> runRetriableStep(String stepName, Job job, Supplier<String> supplier, int attempt) {
		return runStep(stepName, supplier)
			.handle((result, throwable) -> {
				if (throwable == null) {
					return CompletableFuture.completedFuture(result);
				}

				Throwable cause = unwrap(throwable);
				if (attempt >= jobQueueProperties.retryCount() || !isRetryable(stepName, cause)) {
					return CompletableFuture.<String>failedFuture(asRuntimeException(cause));
				}

				return runRetriableStep(stepName, job, supplier, attempt + 1);
			})
			.thenCompose(Function.identity());
	}

	private CompletableFuture<String> runStep(String stepName, Supplier<String> supplier) {
		return CompletableFuture.supplyAsync(supplier, pipelineExecutor)
			.orTimeout(jobQueueProperties.stepTimeoutMs(), TimeUnit.MILLISECONDS)
			.handle((result, throwable) -> {
				if (throwable == null) {
					return CompletableFuture.completedFuture(result);
				}

				Throwable cause = unwrap(throwable);
				if (cause instanceof TimeoutException) {
					return CompletableFuture.<String>failedFuture(new IllegalStateException("Step timed out: " + stepName, cause));
				}

				return CompletableFuture.<String>failedFuture(new IllegalStateException(
					"Step failed: " + stepName + " - " + cause.getMessage(),
					cause
				));
			})
			.thenCompose(Function.identity());
	}

	private String validate(Job job) {
		if (job.type() == null || job.type().isBlank()) {
			throw new IllegalStateException("Job type is required");
		}

		if (job.type().startsWith("slow-pipeline")) {
			pause(job.processingDelayMs());
		}

		return "validated";
	}

	private String process(String validatedPayload, Job job) {
		if (job.type().startsWith("timeout-process")) {
			pause(jobQueueProperties.stepTimeoutMs() + 50);
		} else {
			pause(job.processingDelayMs());
		}

		if (job.type().startsWith("fail") && !job.type().startsWith("fail-enrich")) {
			throw new IllegalStateException("Simulated job failure");
		}

		return validatedPayload + ":processed";
	}

	private String enrich(String processedPayload, Job job) {
		if (job.type().startsWith("timeout-enrich")) {
			pause(jobQueueProperties.stepTimeoutMs() + 50);
		} else if (job.type().startsWith("slow-pipeline")) {
			pause(job.processingDelayMs());
		} else {
			pause(10);
		}

		if (job.type().startsWith("fail-enrich")) {
			throw new IllegalStateException("Enrich step failed");
		}

		if (job.type().startsWith("flaky-enrich")) {
			int attempt = enrichAttempts.computeIfAbsent(job.id().value(), ignored -> new AtomicInteger()).incrementAndGet();
			if (attempt == 1) {
				throw new IllegalStateException("Temporary enrich failure");
			}
		}

		return processedPayload + ":enriched";
	}

	private String finalizePayload(String enrichedPayload, Job job) {
		if (job.type().startsWith("slow-pipeline")) {
			pause(job.processingDelayMs());
		} else {
			pause(10);
		}

		return enrichedPayload + ":finalized";
	}

	private boolean isRetryable(String stepName, Throwable throwable) {
		return ("process".equals(stepName) || "enrich".equals(stepName)) && !(throwable instanceof IllegalStateException exception
			&& exception.getMessage() != null
			&& exception.getMessage().startsWith("Step timed out:"));
	}

	private RuntimeException asRuntimeException(Throwable throwable) {
		return throwable instanceof RuntimeException runtimeException
			? runtimeException
			: new IllegalStateException(throwable.getMessage(), throwable);
	}

	private Throwable unwrap(Throwable throwable) {
		Throwable current = throwable;
		while (current instanceof CompletionException && current.getCause() != null) {
			current = current.getCause();
		}
		return current;
	}

	private void pause(int delayMs) {
		if (delayMs <= 0) {
			return;
		}

		try {
			Thread.sleep(delayMs);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Pipeline step interrupted", exception);
		}
	}
}
