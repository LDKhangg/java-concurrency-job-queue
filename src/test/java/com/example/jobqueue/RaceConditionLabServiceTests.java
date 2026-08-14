package com.example.jobqueue;

import com.example.jobqueue.application.RaceConditionLabService;
import com.example.jobqueue.application.RaceConditionReport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RaceConditionLabServiceTests {

	private final RaceConditionLabService service = new RaceConditionLabService();

	@Test
	void shouldLoseUpdatesWithUnsafeCounter() throws Exception {
		RaceConditionReport report = service.runUnsafeCounterDemo(6, 25);

		assertThat(report.expectedCount()).isEqualTo(150);
		assertThat(report.actualCount()).isLessThan(report.expectedCount());
		assertThat(report.lostUpdates()).isGreaterThan(0);
	}

	@Test
	void shouldPreserveAllUpdatesWithLockedCounter() throws Exception {
		RaceConditionReport report = service.runLockedCounterDemo(6, 25);

		assertThat(report.expectedCount()).isEqualTo(150);
		assertThat(report.actualCount()).isEqualTo(report.expectedCount());
		assertThat(report.lostUpdates()).isZero();
	}
}
