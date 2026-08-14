package com.example.jobqueue.controller;

import com.example.jobqueue.application.RunSimulationUseCase;
import com.example.jobqueue.controller.dto.SimulationRequest;
import com.example.jobqueue.controller.dto.SimulationResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/simulation")
public class SimulationController {

	private final RunSimulationUseCase runSimulationUseCase;

	public SimulationController(RunSimulationUseCase runSimulationUseCase) {
		this.runSimulationUseCase = runSimulationUseCase;
	}

	@PostMapping("/run")
	public SimulationResponse run(@RequestBody SimulationRequest request) {
		return runSimulationUseCase.handle(request);
	}
}
