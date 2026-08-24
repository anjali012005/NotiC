package io.github.anjali.notifyflow.management.exception;

/** Raised when a configured notification provider cannot deliver a notification. */
public class ProviderDispatchException extends RuntimeException {
    public ProviderDispatchException(String message) {
        super(message);
    }

    public ProviderDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
