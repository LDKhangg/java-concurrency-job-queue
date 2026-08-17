package com.example.jobqueue.application;

import com.example.jobqueue.domain.Job;
import com.example.jobqueue.domain.JobId;
import com.example.jobqueue.infrastructure.InMemoryJobRepository;
import org.springframework.stereotype.Service;

@Service
public class SubmitJobUseCase {

	private final InMemoryJobRepository jobRepository;

	public SubmitJobUseCase(InMemoryJobRepository jobRepository) {
		this.jobRepository = jobRepository;
	}

	public Job handle(String jobType) {
		Job job = Job.pending(JobId.newId(), jobType);
		return jobRepository.save(job);
	}
}
