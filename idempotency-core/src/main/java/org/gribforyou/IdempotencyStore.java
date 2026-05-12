package org.gribforyou;

import java.time.Duration;
import java.util.Optional;

/**
 * Thread-safe storage for idempotency records.
 * <p>
 * Implementations MUST guarantee atomicity of {@link #getActiveRecordOrLock} method
 * to prevent duplicate execution in concurrent environments.
 * <p>
 * JDBC implementations may throw IdempotencyStoreException for database connectivity or integrity issues.
 *
 * @param <T> the type of operation result
 */
public interface IdempotencyStore<T> {

    /**
     * Atomically gets an existing record or creates a new LOCKED record.
     * <p>
     * This method MUST be atomic and thread-safe. When multiple threads
     * call this method concurrently with the same key, only one should
     * receive {@code Optional.empty()}, while others should receive the
     * LOCKED record created by the winning thread.
     *
     * @param key         the idempotency key
     * @param lockTimeout maximum time the record can stay in LOCKED state
     * @return existing record of EXECUTED not expired or LOCKED state, or lock key for a new LOCKED record
     */
    Optional<IdempotencyRecord<T>> getActiveRecordOrLock(IdempotencyKey key, Duration lockTimeout);

    /**
     * Atomically updates a LOCKED record to EXECUTED state with the result.
     *
     * @param key    the idempotency key
     * @param result the operation result
     * @return updated record in EXECUTED state
     * @throws IllegalStateException if the record is not in LOCKED state
     */
    IdempotencyRecord<T> saveResult(IdempotencyKey key, T result, Duration defaultTtl);

    /**
     * Atomically updates a LOCKED record to FAILED state with the error.
     *
     * @param key   the idempotency key
     * @param error the operation error
     * @return updated record in FAILED state
     * @throws IllegalStateException if the record is not in LOCKED state
     */
    IdempotencyRecord<T> saveError(IdempotencyKey key, Throwable error, Duration defaultTtl);

    /**
     * Gets an existing record by key without locking.
     *
     * @param key the idempotency key
     * @return existing record or empty if not found
     */
    Optional<IdempotencyRecord<T>> get(IdempotencyKey key);
}
