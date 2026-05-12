package org.gribforyou;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for executing operations with idempotency guarantees.
 * <p>
 * Ensures that operations with the same idempotency key are executed only once,
 * returning cached results for subsequent requests.
 * <p>
 * If a previous execution failed, this service will retry the operation instead of
 * immediately throwing the cached error.
 *
 * @param <T> the type of operation result
 */
public class IdempotencyService<T> {

    private final IdempotencyStore<T> store;
    private final IdempotencyKeyGenerator keyGenerator;
    private final Duration defaultTtl;
    private final Duration lockTimeout;
    private final int maxLockWaitAttempts;
    private final Duration lockWaitInterval;

    private IdempotencyService(Builder<T> builder) {
        this.store = Objects.requireNonNull(builder.store, "store must not be null");
        this.keyGenerator = Objects.requireNonNull(builder.keyGenerator, "keyGenerator must not be null");
        this.defaultTtl = Objects.requireNonNull(builder.defaultTtl, "defaultTtl must not be null");
        this.lockTimeout = Objects.requireNonNull(builder.lockTimeout, "lockTimeout must not be null");
        this.maxLockWaitAttempts = builder.maxLockWaitAttempts;
        this.lockWaitInterval = Objects.requireNonNull(builder.lockWaitInterval, "lockWaitInterval must not be null");
    }

    /**
     * Creates a new builder for IdempotencyService.
     *
     * @param <T> the type of operation result
     * @return a new builder instance
     */
    public static <T> Builder<T> builder(Class<T> clazz) {
        return new Builder<>();
    }

    /**
     * Executes an operation with idempotency guarantees.
     * <p>
     * Behavior based on record state:
     * <ul>
     *   <li><b>EXECUTED</b> - returns cached result</li>
     *   <li><b>FAILED</b> - retries the operation</li>
     *   <li><b>LOCKED</b> - waits for lock release</li>
     * </ul>
     *
     * @param operationType the type of operation
     * @param params operation parameters for key generation
     * @param operation the operation to execute
     * @return the operation result
     * @throws IdempotencyOperationExecutionFailedException if the operation failed
     * @throws IdempotencyOperationExecutionFailedException if lock wait timeout exceeded
     */
    public T execute(String operationType, Object[] params, Operation<T> operation) {
        Objects.requireNonNull(operationType, "operationType must not be null");
        Objects.requireNonNull(params, "params must not be null");
        Objects.requireNonNull(operation, "operation must not be null");

        IdempotencyKey key = keyGenerator.generate(operationType, params);

        try {
            return store.getActiveRecordOrLock(key, lockTimeout)
                    .map(record -> handleExistingRecord(record, operation))
                    .orElseGet(() -> executeNewOperation(key, operation));
        } catch (IdempotencyStoreException e) {
            throw new IdempotencyOperationExecutionFailedException(
                    "Database error occurred while processing idempotency request", e);
        }
    }

    private T handleExistingRecord(IdempotencyRecord<T> record, Operation<T> operation) {
        if (record.isStillLocked()) {
            return waitForUnlockAndRetry(record.key(), operation);
        }

        if (record.state() == RecordState.EXECUTED) {
            return record.getResult();
        }

        throw new IllegalStateException("Existing record from DB is not locked or executed");
    }

    private T executeNewOperation(IdempotencyKey key, Operation<T> operation) {
        try {
            T result = operation.execute();
            store.saveResult(key, result, defaultTtl);
            return result;
        } catch (Exception e) {
            try {
                store.saveError(key, e, defaultTtl);
            } catch (Exception saveErrorEx) {
                throw new IdempotencyOperationExecutionFailedException(
                        "Database error occurred while saving error for idempotency request", saveErrorEx);
            }
            throw new IdempotencyOperationExecutionFailedException(
                    "Operation failed: " + e.getMessage(), e);
        }
    }

    private T waitForUnlockAndRetry(IdempotencyKey key, Operation<T> operation) {
        int attempts = 0;

        while (attempts < maxLockWaitAttempts) {
            try {
                Thread.sleep(lockWaitInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IdempotencyOperationExecutionFailedException(
                        "Interrupted while waiting for lock", e);
            }

            attempts++;

            Optional<IdempotencyRecord<T>> curResponse = store.getActiveRecordOrLock(key, lockTimeout);
            if(curResponse.isEmpty()) {
                return executeNewOperation(key, operation);
            } else if (curResponse.get().state() != RecordState.LOCKED){
                return handleExistingRecord(curResponse.get(), operation);
            }
        }

        throw new IdempotencyOperationExecutionFailedException(
                "Maximum lock wait attempts exceeded: " + maxLockWaitAttempts + " attempts (" + (maxLockWaitAttempts * lockWaitInterval.toMillis()) + "ms)");
    }

    /**
     * Builder for {@link IdempotencyService}.
     *
     * @param <T> the type of operation result
     */
    public static class Builder<T> {
        private IdempotencyStore<T> store;
        private IdempotencyKeyGenerator keyGenerator;
        private Duration defaultTtl = Duration.ofHours(1);
        private Duration lockTimeout = Duration.ofSeconds(30);
        private int maxLockWaitAttempts = 10;
        private Duration lockWaitInterval = Duration.ofMillis(5);

        /**
         * Sets the idempotency store implementation.
         * <p>
         * Implementations MUST guarantee atomicity of {@code getOrLock} method.
         *
         * @param store the store implementation (must be thread-safe)
         * @return this builder
         */
        public Builder<T> store(IdempotencyStore<T> store) {
            this.store = store;
            return this;
        }

        /**
         * Sets the idempotency key generator.
         *
         * @param keyGenerator the key generator
         * @return this builder
         */
        public Builder<T> keyGenerator(IdempotencyKeyGenerator keyGenerator) {
            this.keyGenerator = keyGenerator;
            return this;
        }

        /**
         * Sets the default time-to-live for idempotency records.
         * <p>
         * After TTL expires, the record is considered stale and the operation
         * will be re-executed on next request.
         *
         * @param defaultTtl the TTL duration
         * @return this builder
         */
        public Builder<T> defaultTtl(Duration defaultTtl) {
            this.defaultTtl = defaultTtl;
            return this;
        }

        /**
         * Sets the maximum time a record can stay in LOCKED state.
         * <p>
         * If a thread holds the lock longer than this duration, other threads
         * will consider the lock stale and re-execute the operation.
         *
         * @param lockTimeout the lock timeout duration
         * @return this builder
         */
        public Builder<T> lockTimeout(Duration lockTimeout) {
            this.lockTimeout = lockTimeout;
            return this;
        }

        /**
         * Sets the maximum number of attempts to wait for a locked record.
         * <p>
         * Default value is 10.
         *
         * @param maxLockWaitAttempts the maximum number of attempts
         * @return this builder
         */
        public Builder<T> maxLockWaitAttempts(int maxLockWaitAttempts) {
            this.maxLockWaitAttempts = maxLockWaitAttempts;
            return this;
        }

        /**
         * Sets the interval between attempts when waiting for a locked record.
         * <p>
         * Default value is 5ms.
         *
         * @param lockWaitInterval the wait interval between attempts
         * @return this builder
         */
        public Builder<T> lockWaitInterval(Duration lockWaitInterval) {
            this.lockWaitInterval = lockWaitInterval;
            return this;
        }

        /**
         * Builds the IdempotencyService instance.
         *
         * @return a new IdempotencyService instance
         */
        public IdempotencyService<T> build() {
            return new IdempotencyService<>(this);
        }
    }
} 
