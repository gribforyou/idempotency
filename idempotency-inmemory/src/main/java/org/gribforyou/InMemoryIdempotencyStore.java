package org.gribforyou;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory implementation of {@link IdempotencyStore}.
 * <p>
 * This implementation uses a {@link ConcurrentHashMap} for thread-safe storage
 * and {@link ReentrantLock} for atomic get-or-lock operations.
 * <p>
 * <strong>Note:</strong> This implementation is suitable for single-node applications.
 * For distributed systems, consider using a shared storage implementation (e.g., Redis, database).
 *
 * @param <T> the type of operation result
 */
public class InMemoryIdempotencyStore<T> implements IdempotencyStore<T> {

    private final ConcurrentMap<IdempotencyKey, IdempotencyRecord<T>> storage = new ConcurrentHashMap<>();
    private final ConcurrentMap<IdempotencyKey, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<IdempotencyRecord<T>> getActiveRecordOrLock(IdempotencyKey key, Duration lockTimeout) {
        ReentrantLock keyLock = locks.computeIfAbsent(key, k -> new ReentrantLock());

        keyLock.lock();
        try {
            IdempotencyRecord<T> existingRecord = storage.get(key);

            if (existingRecord == null || existingRecord.isExpired() || existingRecord.state() == RecordState.FAILED) {
                long now = System.currentTimeMillis();
                long lockedUntil = now + lockTimeout.toMillis();

                IdempotencyRecord<T> newLockedRecord = new IdempotencyRecord<>(
                        key,
                        null,
                        null,
                        RecordState.LOCKED,
                        now,
                        lockTimeout.toMillis(),
                        lockedUntil
                );

                storage.put(key, newLockedRecord);
                return Optional.empty();
            }

            return Optional.of(existingRecord);

        } finally {
            keyLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IdempotencyRecord<T> saveResult(IdempotencyKey key, T result, Duration defaultTtl) {
        ReentrantLock keyLock = locks.computeIfAbsent(key, k -> new ReentrantLock());

        keyLock.lock();
        try {
            IdempotencyRecord<T> existingRecord = storage.get(key);

            if (existingRecord == null || existingRecord.state() != RecordState.LOCKED) {
                throw new IllegalStateException("Cannot save result: record is not in LOCKED state (key: " + key + ")");
            }

            IdempotencyRecord<T> executedRecord = new IdempotencyRecord<>(
                    key,
                    result,
                    null,
                    RecordState.EXECUTED,
                    existingRecord.createdAt(),
                    defaultTtl.toMillis(),
                    null
            );

            storage.put(key, executedRecord);
            return executedRecord;

        } finally {
            keyLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IdempotencyRecord<T> saveError(IdempotencyKey key, Throwable error, Duration defaultTtl) {
        ReentrantLock keyLock = locks.computeIfAbsent(key, k -> new ReentrantLock());

        keyLock.lock();
        try {
            IdempotencyRecord<T> existingRecord = storage.get(key);

            if (existingRecord == null || existingRecord.state() != RecordState.LOCKED) {
                throw new IllegalStateException("Cannot save error: record is not in LOCKED state (key: " + key + ")");
            }

            IdempotencyRecord<T> failedRecord = new IdempotencyRecord<>(
                    key,
                    null,
                    error,
                    RecordState.FAILED,
                    existingRecord.createdAt(),
                    defaultTtl.toMillis(),
                    null
            );

            storage.put(key, failedRecord);
            return failedRecord;

        } finally {
            keyLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<IdempotencyRecord<T>> get(IdempotencyKey key) {
        return Optional.ofNullable(storage.get(key));
    }

    /**
     * Clears all stored records and locks.
     * <p>
     * This method is primarily useful for testing purposes.
     */
    public void clear() {
        storage.clear();
        locks.clear();
    }

    /**
     * Returns the current number of records in the store.
     *
     * @return the number of records
     */
    public int size() {
        return storage.size();
    }
}