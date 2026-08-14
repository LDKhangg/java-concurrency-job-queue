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

@SpringBootTest
@AutoConfigureMockMvc
class JobQueueApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void shouldCreateAndFetchJob() throws Exception {
		MvcResult createResult = mockMvc.perform(post("/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"email\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn();

		String responseBody = createResult.getResponse().getContentAsString();
		String jobId = responseBody.replaceAll(".*\"jobId\":\"([^\"]+)\".*", "$1");

		mockMvc.perform(get("/jobs/{jobId}", jobId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.jobId").value(jobId))
			.andExpect(jsonPath("$.type").value("email"))
			.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void shouldReturnWorkerMetrics() throws Exception {
		mockMvc.perform(get("/workers/metrics"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.workerCount").value(0));
	}

	@Test
	void shouldRunSimulation() throws Exception {
		mockMvc.perform(post("/simulation/run")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"producerCount\":2,\"jobsPerProducer\":3,\"producerDelayMs\":10,\"workerDelayMs\":20}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalJobs").value(6))
			.andExpect(jsonPath("$.successfulJobs").value(6));
	}
}
