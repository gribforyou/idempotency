package org.gribforyou;

import static org.gribforyou.RecordState.LOCKED;

/**
 * Represents an idempotency record with operation result or error.
 *
 * @param <T> the type of operation result
 */
public record IdempotencyRecord<T>(
        IdempotencyKey key,
        T result,
        Throwable error,
        RecordState state,
        long createdAt,
        long ttl,
        Long lockedUntil
) {
    /**
     * Checks if the record has expired based on TTL.
     *
     * @return true if the record is expired
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - createdAt > ttl;
    }

    /**
     * Checks if the record is still locked (lock timeout has not expired).
     *
     * @return true if the record is still locked
     */
    public boolean isStillLocked() {
        return state == LOCKED && System.currentTimeMillis() < lockedUntil;
    }

    /**
     * Gets the result of the operation.
     *
     * @return the operation result
     * @throws IllegalStateException if the record is in FAILED state
     */
    public T getResult() {
        if (state == RecordState.FAILED) {
            throw new IllegalStateException("Record is in FAILED state", error);
        }
        return result;
    }
}
