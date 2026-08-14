package com.example.jobqueue.application;

import com.example.jobqueue.domain.Job;
import com.example.jobqueue.domain.JobId;
import com.example.jobqueue.infrastructure.InMemoryJobRepository;
import org.springframework.stereotype.Service;

@Service
public class SubmitJobUseCase {

	private final InMemoryJobRepository jobRepository;
	private final JobProcessingService jobProcessingService;
	private final JobQueueProperties jobQueueProperties;

	public SubmitJobUseCase(
		InMemoryJobRepository jobRepository,
		JobProcessingService jobProcessingService,
		JobQueueProperties jobQueueProperties
	) {
		this.jobRepository = jobRepository;
		this.jobProcessingService = jobProcessingService;
		this.jobQueueProperties = jobQueueProperties;
	}

	public Job handle(String jobType) {
		return handle(jobType, jobQueueProperties.defaultWorkerDelayMs());
	}

	public Job handle(String jobType, int processingDelayMs) {
		Job job = Job.pending(JobId.newId(), jobType, processingDelayMs);
		Job savedJob = jobRepository.save(job);
		jobProcessingService.enqueue(savedJob);
		return savedJob;
	}
}
