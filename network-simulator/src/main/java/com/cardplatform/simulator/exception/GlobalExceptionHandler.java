package com.cardplatform.simulator.exception;

import com.cardplatform.simulator.grpc.NetworkCallException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GlobalExceptionHandler {

    // Core failed or rejected the call itself: from the simulator's side that's a bad gateway
    @ExceptionHandler(NetworkCallException.class)
    ProblemDetail coreCallFailed(NetworkCallException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
        problem.setProperty("code", "CORE_CALL_FAILED");
        problem.setProperty("grpcStatus", e.getStatusCode().name());
        problem.setProperty("attempts", e.getAttempts());
        return problem;
    }

    @ExceptionHandler(UnknownMerchantException.class)
    ProblemDetail unknownMerchant(UnknownMerchantException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setProperty("code", "UNKNOWN_MERCHANT");
        return problem;
    }
}