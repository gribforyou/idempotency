package org.gribforyou;

/**
 * Runtime exception thrown by IdempotencyStore implementations when database operations fail.
 * This wraps checked SQLExceptions to avoid propagating them through the public API.
 */
public class IdempotencyStoreException extends RuntimeException {
    
    public IdempotencyStoreException(String message) {
        super(message);
    }
    
    public IdempotencyStoreException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public IdempotencyStoreException(Throwable cause) {
        super(cause);
    }
}