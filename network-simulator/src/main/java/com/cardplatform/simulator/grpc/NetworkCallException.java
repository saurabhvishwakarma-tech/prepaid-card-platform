package com.cardplatform.simulator.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

public class NetworkCallException extends RuntimeException {

    private final Status.Code statusCode;
    private final int attempts;

    public NetworkCallException(String operation, int attempts, StatusRuntimeException cause) {
        super(operation + " failed after " + attempts + " attempt(s): " + cause.getStatus().getCode(), cause);
        this.statusCode = cause.getStatus().getCode();
        this.attempts = attempts;
    }

    // Core never answered, so it may or may not have processed the call
    public boolean isUnanswered() {
        return statusCode == Status.Code.DEADLINE_EXCEEDED || statusCode == Status.Code.UNAVAILABLE;
    }

    public Status.Code getStatusCode() {
        return statusCode;
    }

    public int getAttempts() {
        return attempts;
    }
}