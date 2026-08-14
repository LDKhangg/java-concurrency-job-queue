package com.example.jobqueue.application;

public record RaceConditionReport(int expectedCount, int actualCount, int lostUpdates) {
}
