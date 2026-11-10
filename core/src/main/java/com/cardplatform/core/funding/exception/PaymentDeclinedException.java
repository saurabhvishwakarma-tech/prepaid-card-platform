package com.cardplatform.core.funding.exception;

// The provider refused the payment for good; retrying the same card won't change the answer
public class PaymentDeclinedException extends RuntimeException {

    private final String code;

    public PaymentDeclinedException(String code) {
        super("Payment declined: " + code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}