package com.example.jobqueue.application;

import org.springframework.stereotype.Service;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class RaceConditionLabService {

	public RaceConditionReport runUnsafeCounterDemo(int threadCount, int incrementsPerThread) throws InterruptedException {
		PlainCounter counter = new PlainCounter();
		CyclicBarrier afterReadBarrier = new CyclicBarrier(threadCount);
		CyclicBarrier afterWriteBarrier = new CyclicBarrier(threadCount);
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch finished = new CountDownLatch(threadCount);

		for (int index = 0; index < threadCount; index++) {
			executor.submit(() -> {
				try {
					for (int increment = 0; increment < incrementsPerThread; increment++) {
						int snapshot = counter.value;
						awaitBarrier(afterReadBarrier);
						counter.value = snapshot + 1;
						awaitBarrier(afterWriteBarrier);
					}
				} finally {
					finished.countDown();
				}
			});
		}

		finished.await(5, TimeUnit.SECONDS);
		executor.shutdownNow();

		int expectedCount = threadCount * incrementsPerThread;
		return new RaceConditionReport(expectedCount, counter.value, expectedCount - counter.value);
	}

	public RaceConditionReport runLockedCounterDemo(int threadCount, int incrementsPerThread) throws InterruptedException {
		LockedCounter counter = new LockedCounter();
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch finished = new CountDownLatch(threadCount);

		for (int index = 0; index < threadCount; index++) {
			executor.submit(() -> {
				try {
					for (int increment = 0; increment < incrementsPerThread; increment++) {
						counter.increment();
					}
				} finally {
					finished.countDown();
				}
			});
		}

		finished.await(5, TimeUnit.SECONDS);
		executor.shutdownNow();

		int expectedCount = threadCount * incrementsPerThread;
		return new RaceConditionReport(expectedCount, counter.value(), expectedCount - counter.value());
	}

	private void awaitBarrier(CyclicBarrier barrier) {
		try {
			barrier.await();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Race lab was interrupted", exception);
		} catch (BrokenBarrierException exception) {
			throw new IllegalStateException("Race lab barrier was broken", exception);
		}
	}

	private static final class PlainCounter {
		private int value;
	}

	private static final class LockedCounter {
		private final ReentrantLock lock = new ReentrantLock();
		private int value;

		private void increment() {
			lock.lock();
			try {
				value++;
			} finally {
				lock.unlock();
			}
		}

		private int value() {
			return value;
		}
	}
}
