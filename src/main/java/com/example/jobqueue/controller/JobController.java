package com.example.jobqueue.controller;

import com.example.jobqueue.application.GetJobStatusUseCase;
import com.example.jobqueue.application.SubmitJobUseCase;
import com.example.jobqueue.controller.dto.CreateJobRequest;
import com.example.jobqueue.controller.dto.CreateJobResponse;
import com.example.jobqueue.controller.dto.JobResponse;
import com.example.jobqueue.domain.Job;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/jobs")
public class JobController {

	private final SubmitJobUseCase submitJobUseCase;
	private final GetJobStatusUseCase getJobStatusUseCase;

	public JobController(SubmitJobUseCase submitJobUseCase, GetJobStatusUseCase getJobStatusUseCase) {
		this.submitJobUseCase = submitJobUseCase;
		this.getJobStatusUseCase = getJobStatusUseCase;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CreateJobResponse createJob(@RequestBody(required = false) CreateJobRequest request) {
		String jobType = request == null || request.type() == null || request.type().isBlank() ? "demo-job" : request.type();
		Job job = submitJobUseCase.handle(jobType);
		return new CreateJobResponse(job.id().value(), job.status().name());
	}

	@GetMapping("/{jobId}")
	public JobResponse getJob(@PathVariable String jobId) {
		Job job = getJobStatusUseCase.handle(jobId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
		return new JobResponse(job.id().value(), job.type(), job.status().name(), job.result().errorMessage(), job.createdAt());
	}
}
