package com.example.jobqueue.infrastructure;

import com.example.jobqueue.domain.Job;
import com.example.jobqueue.domain.JobId;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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

	public Collection<Job> findAll() {
		return jobs.values();
	}
}
