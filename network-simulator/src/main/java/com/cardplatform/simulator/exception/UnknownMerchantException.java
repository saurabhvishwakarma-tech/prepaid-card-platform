package com.cardplatform.simulator.exception;

public class UnknownMerchantException extends RuntimeException {

    public UnknownMerchantException(String merchantId) {
        super("Unknown merchant: " + merchantId);
    }
}