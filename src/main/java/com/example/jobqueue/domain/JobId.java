package com.example.jobqueue.domain;

import java.util.UUID;

public record JobId(String value) {

	public static JobId newId() {
		return new JobId(UUID.randomUUID().toString());
	}
}
