package com.example.jobqueue.application;

import com.example.jobqueue.domain.Job;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public record ScheduledJobEntry(String jobId, int priority, long readyAtNanos, long sequence) implements Delayed {

	public static ScheduledJobEntry of(Job job, long sequence) {
		long readyAtNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(job.delayMs());
		return new ScheduledJobEntry(job.id().value(), job.priority(), readyAtNanos, sequence);
	}

	@Override
	public long getDelay(TimeUnit unit) {
		return unit.convert(readyAtNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
	}

	@Override
	public int compareTo(Delayed other) {
		ScheduledJobEntry that = (ScheduledJobEntry) other;
		int byReadyAt = Long.compare(readyAtNanos, that.readyAtNanos);
		if (byReadyAt != 0) {
			return byReadyAt;
		}
		return Long.compare(sequence, that.sequence);
	}
}