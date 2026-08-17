package com.example.jobqueue.application;

import com.example.jobqueue.domain.Job;
import com.example.jobqueue.domain.JobStatus;
import com.example.jobqueue.domain.WorkerMetrics;
import com.example.jobqueue.infrastructure.InMemoryJobRepository;
import org.springframework.stereotype.Service;

@Service
public class GetWorkerMetricsUseCase {

	private final InMemoryJobRepository jobRepository;

	public GetWorkerMetricsUseCase(InMemoryJobRepository jobRepository) {
		this.jobRepository = jobRepository;
	}

	public WorkerMetrics handle() {
		int pendingJobs = 0;
		int runningJobs = 0;
		int successfulJobs = 0;
		int failedJobs = 0;

		for (Job job : jobRepository.findAll()) {
			if (job.status() == JobStatus.PENDING) {
				pendingJobs++;
			} else if (job.status() == JobStatus.RUNNING) {
				runningJobs++;
			} else if (job.status() == JobStatus.SUCCESS) {
				successfulJobs++;
			} else if (job.status() == JobStatus.FAILED) {
				failedJobs++;
			}
		}

		return new WorkerMetrics(pendingJobs, runningJobs, successfulJobs, failedJobs, 0);
	}
}
