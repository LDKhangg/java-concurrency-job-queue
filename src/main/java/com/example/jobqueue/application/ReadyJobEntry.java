package com.example.jobqueue.application;

public record ReadyJobEntry(String jobId, int priority, long sequence) implements Comparable<ReadyJobEntry> {

	@Override
	public int compareTo(ReadyJobEntry other) {
		int byPriority = Integer.compare(other.priority, priority);
		if (byPriority != 0) {
			return byPriority;
		}
		return Long.compare(sequence, other.sequence);
	}
}