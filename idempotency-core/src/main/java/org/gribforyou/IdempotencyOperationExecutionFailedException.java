package org.gribforyou;

/**
 * Thrown when an idempotent operation fails.
 * <p>
 * This exception wraps the original cause and indicates that the operation
 * has been recorded as FAILED. Subsequent requests with the same idempotency
 * key will re-throw this exception.
 */
public class IdempotencyOperationExecutionFailedException extends RuntimeException {

    public IdempotencyOperationExecutionFailedException(String message) {
        super(message);
    }

    public IdempotencyOperationExecutionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
