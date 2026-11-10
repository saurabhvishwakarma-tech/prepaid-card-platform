package com.cardplatform.simulator.dto;

public record LoadReport(int requests, int approved, int declined, int failed,
                         long p50Ms, long p95Ms, long maxMs, long totalMs) {
}