package com.example.jobqueue.infrastructure;

import com.example.jobqueue.domain.Job;
import com.example.jobqueue.domain.JobId;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryJobRepository {

	private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();

	public Job save(Job job) {
		jobs.put(job.id().value(), job);
		return job;
	}

	public Optional<Job> findById(JobId jobId) {
		return Optional.ofNullable(jobs.get(jobId.value()));
	}

	public Optional<Job> update(JobId jobId, UnaryOperator<Job> updater) {
		AtomicReference<Job> updatedJob = new AtomicReference<>();
		jobs.computeIfPresent(jobId.value(), (ignored, existingJob) -> {
			Job nextJob = updater.apply(existingJob);
			updatedJob.set(nextJob);
			return nextJob;
		});
		return Optional.ofNullable(updatedJob.get());
	}

	public Collection<Job> findAll() {
		return jobs.values();
	}
}
