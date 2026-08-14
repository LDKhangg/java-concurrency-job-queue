package com.example.jobqueue.application;

import com.example.jobqueue.domain.Job;
import com.example.jobqueue.domain.JobId;
import com.example.jobqueue.infrastructure.InMemoryJobRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GetJobStatusUseCase {

	private final InMemoryJobRepository jobRepository;

	public GetJobStatusUseCase(InMemoryJobRepository jobRepository) {
		this.jobRepository = jobRepository;
	}

	public Optional<Job> handle(String jobId) {
		return jobRepository.findById(new JobId(jobId));
	}
}
