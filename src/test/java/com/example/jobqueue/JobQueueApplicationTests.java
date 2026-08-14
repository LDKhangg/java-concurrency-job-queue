package com.example.jobqueue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;

@SpringBootTest
@AutoConfigureMockMvc
class JobQueueApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void shouldCreateAndProcessJob() throws Exception {
		MvcResult createResult = mockMvc.perform(post("/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"email\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn();

		String responseBody = createResult.getResponse().getContentAsString();
		String jobId = responseBody.replaceAll(".*\"jobId\":\"([^\"]+)\".*", "$1");

		awaitJobStatus(jobId, "SUCCESS");

		mockMvc.perform(get("/jobs/{jobId}", jobId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.jobId").value(jobId))
			.andExpect(jsonPath("$.type").value("email"))
			.andExpect(jsonPath("$.status").value("SUCCESS"));
	}

	@Test
	void shouldMarkJobAsFailedWhenProcessorThrows() throws Exception {
		MvcResult createResult = mockMvc.perform(post("/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"fail-email\"}"))
				.andExpect(status().isCreated())
				.andReturn();

		String responseBody = createResult.getResponse().getContentAsString();
		String jobId = responseBody.replaceAll(".*\"jobId\":\"([^\"]+)\".*", "$1");

		awaitJobStatus(jobId, "FAILED");

		mockMvc.perform(get("/jobs/{jobId}", jobId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.errorMessage").value("Step failed: process - Simulated job failure"));
	}

	@Test
	void shouldMarkJobAsFailedWhenPipelineStepTimesOut() throws Exception {
		MvcResult createResult = mockMvc.perform(post("/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"timeout-process\"}"))
				.andExpect(status().isCreated())
				.andReturn();

		String responseBody = createResult.getResponse().getContentAsString();
		String jobId = responseBody.replaceAll(".*\"jobId\":\"([^\"]+)\".*", "$1");

		awaitJobStatus(jobId, "FAILED");

		mockMvc.perform(get("/jobs/{jobId}", jobId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.errorMessage").value("Step timed out: process"));
	}

	@Test
	void shouldReturnWorkerMetrics() throws Exception {
		Thread.sleep(150);

		mockMvc.perform(get("/workers/metrics"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.workerCount").value(2));
	}

	@Test
	void shouldRunSimulation() throws Exception {
		mockMvc.perform(post("/simulation/run")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"producerCount\":2,\"jobsPerProducer\":3,\"producerDelayMs\":10,\"workerDelayMs\":20}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalJobs").value(6))
			.andExpect(jsonPath("$.successfulJobs").value(6))
			.andExpect(jsonPath("$.failedJobs").value(0));
	}

	private void awaitJobStatus(String jobId, String expectedStatus) throws Exception {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
		while (Instant.now().isBefore(deadline)) {
			MvcResult result = mockMvc.perform(get("/jobs/{jobId}", jobId))
				.andExpect(status().isOk())
				.andReturn();

			String body = result.getResponse().getContentAsString();
			if (body.contains("\"status\":\"" + expectedStatus + "\"")) {
				return;
			}

			Thread.sleep(50);
		}

		mockMvc.perform(get("/jobs/{jobId}", jobId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(expectedStatus));
	}
}
