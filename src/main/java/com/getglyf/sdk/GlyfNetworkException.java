package com.getglyf.sdk;

/**
 * The request never produced an API response: connection failure, DNS error,
 * timeout, or interruption — after the configured retries were exhausted.
 */
public class GlyfNetworkException extends RuntimeException {

    public GlyfNetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
