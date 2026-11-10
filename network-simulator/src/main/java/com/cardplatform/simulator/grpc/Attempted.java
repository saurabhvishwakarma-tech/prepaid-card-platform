package com.cardplatform.simulator.grpc;

// A response from core and how many attempts it took to get it
public record Attempted<T>(T response, int attempts) {
}