package org.gribforyou;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryIdempotencyStore}.
 */
class InMemoryIdempotencyStoreTest {

    private InMemoryIdempotencyStore<String> store;
    private final IdempotencyKey key = new IdempotencyKey("test-key");
    private final Duration lockTimeout = Duration.ofSeconds(10);
    private final Duration ttl = Duration.ofHours(1);
    private final String result = "testResult";

    @BeforeEach
    void setUp() {
        store = new InMemoryIdempotencyStore<>();
    }

    @Test
    void getActiveRecordOrLock_whenNoRecordExists_shouldCreateLockedRecordAndReturnEmpty() {
        // When
        Optional<IdempotencyRecord<String>> optionalRecord = store.getActiveRecordOrLock(key, lockTimeout);

        // Then
        assertTrue(optionalRecord.isEmpty());

        // Проверка, что запись действительно создана и заблокирована
        Optional<IdempotencyRecord<String>> actualRecord = store.get(key);
        assertTrue(actualRecord.isPresent());
        assertEquals(RecordState.LOCKED, actualRecord.get().state());
        assertEquals(key, actualRecord.get().key());
        assertNull(actualRecord.get().result());
    }

    @Test
    void getActiveRecordOrLock_whenRecordExistsAndLocked_shouldReturnExistingRecord() {
        // Given
        store.getActiveRecordOrLock(key, lockTimeout);

        // When
        Optional<IdempotencyRecord<String>> optionalRecord = store.getActiveRecordOrLock(key, lockTimeout);

        // Then
        assertTrue(optionalRecord.isPresent());
        assertEquals(RecordState.LOCKED, optionalRecord.get().state());
    }

    @Test
    void getActiveRecordOrLock_whenRecordExecuted_shouldReturnExecutedRecord() {
        // Given
        store.getActiveRecordOrLock(key, lockTimeout);
        store.saveResult(key, result, ttl);

        // When
        Optional<IdempotencyRecord<String>> optionalRecord = store.getActiveRecordOrLock(key, lockTimeout);

        // Then
        assertTrue(optionalRecord.isPresent());
        assertEquals(RecordState.EXECUTED, optionalRecord.get().state());
        assertEquals(result, optionalRecord.get().result());
    }

    @Test
    void getActiveRecordOrLock_whenRecordFailed_shouldCreateNewLockedRecordAndReturnEmpty() {
        // Given
        store.getActiveRecordOrLock(key, lockTimeout);
        RuntimeException error = new RuntimeException("Test error");
        store.saveError(key, error, ttl);

        // When
        Optional<IdempotencyRecord<String>> optionalRecord = store.getActiveRecordOrLock(key, lockTimeout);

        // Then
        assertTrue(optionalRecord.isEmpty(), "Должен вернуться empty для перехвата выполнения после ошибки");

        Optional<IdempotencyRecord<String>> newRecord = store.get(key);
        assertTrue(newRecord.isPresent());
        assertEquals(RecordState.LOCKED, newRecord.get().state());
    }

    @Test
    void getActiveRecordOrLock_whenRecordExpired_shouldCreateNewLockedRecordAndReturnEmpty() throws InterruptedException {
        // Given
        Duration shortTtl = Duration.ofMillis(50);
        store.getActiveRecordOrLock(key, shortTtl);

        // Ждем истечения TTL
        Thread.sleep(60);

        // When
        Optional<IdempotencyRecord<String>> optionalRecord = store.getActiveRecordOrLock(key, lockTimeout);

        // Then
        assertTrue(optionalRecord.isEmpty());

        Optional<IdempotencyRecord<String>> newRecord = store.get(key);
        assertTrue(newRecord.isPresent());
        assertEquals(RecordState.LOCKED, newRecord.get().state());
    }

    @Test
    void saveResult_whenRecordLocked_shouldUpdateToExecuted() {
        // Given
        store.getActiveRecordOrLock(key, lockTimeout);

        // When
        IdempotencyRecord<String> savedRecord = store.saveResult(key, result, ttl);

        // Then
        assertEquals(RecordState.EXECUTED, savedRecord.state());
        assertEquals(result, savedRecord.result());
        assertNull(savedRecord.error());

        Optional<IdempotencyRecord<String>> actualRecord = store.get(key);
        assertEquals(RecordState.EXECUTED, actualRecord.get().state());
    }

    @Test
    void saveResult_whenRecordNotLocked_shouldThrowIllegalStateException() {
        // Given (запись не создана, сразу пытаемся сохранить)
        // Или запись уже выполнена
        store.getActiveRecordOrLock(key, lockTimeout);
        store.saveResult(key, result, ttl);

        // When & Then
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> store.saveResult(key, "anotherResult", ttl)
        );
        assertTrue(exception.getMessage().contains("not in LOCKED state"));
    }

    @Test
    void saveError_whenRecordLocked_shouldUpdateToFailed() {
        // Given
        store.getActiveRecordOrLock(key, lockTimeout);
        RuntimeException error = new RuntimeException("Test error");

        // When
        IdempotencyRecord<String> savedRecord = store.saveError(key, error, ttl);

        // Then
        assertEquals(RecordState.FAILED, savedRecord.state());
        assertNull(savedRecord.result());
        assertEquals(error, savedRecord.error());

        Optional<IdempotencyRecord<String>> actualRecord = store.get(key);
        assertEquals(RecordState.FAILED, actualRecord.get().state());
    }

    @Test
    void saveError_whenRecordNotLocked_shouldThrowIllegalStateException() {
        // Given
        // Пытаемся сохранить ошибку без предварительной блокировки
        // When & Then
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> store.saveError(key, new RuntimeException("Test error"), ttl)
        );
        assertTrue(exception.getMessage().contains("not in LOCKED state"));
    }

    @Test
    void get_whenRecordExists_shouldReturnRecord() {
        // Given
        store.getActiveRecordOrLock(key, lockTimeout);
        store.saveResult(key, result, ttl);

        // When
        Optional<IdempotencyRecord<String>> optionalRecord = store.get(key);

        // Then
        assertTrue(optionalRecord.isPresent());
        assertEquals(result, optionalRecord.get().result());
    }

    @Test
    void get_whenRecordDoesNotExist_shouldReturnEmpty() {
        // When
        Optional<IdempotencyRecord<String>> optionalRecord = store.get(key);

        // Then
        assertTrue(optionalRecord.isEmpty());
    }

    @Test
    void clear_shouldRemoveAllRecords() {
        // Given
        store.getActiveRecordOrLock(key, lockTimeout);
        store.saveResult(key, result, ttl);
        IdempotencyKey key2 = new IdempotencyKey("key-2");
        store.getActiveRecordOrLock(key2, lockTimeout);

        assertEquals(2, store.size());

        // When
        store.clear();

        // Then
        assertEquals(0, store.size());
        assertTrue(store.get(key).isEmpty());
        assertTrue(store.get(key2).isEmpty());
    }

    @Test
    void getActiveRecordOrLock_concurrentCalls_sameKey_shouldAllowOnlyOneToProceed() throws InterruptedException {
        // Given
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger emptyCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Optional<IdempotencyRecord<String>> result = store.getActiveRecordOrLock(key, lockTimeout);
                    if (result.isEmpty()) {
                        emptyCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Then
        // Только один поток должен получить empty (создать блокировку)
        assertEquals(1, emptyCount.get(), "Только один поток должен получить пустой Optional для создания блокировки");

        Optional<IdempotencyRecord<String>> record = store.get(key);
        assertTrue(record.isPresent());
        assertEquals(RecordState.LOCKED, record.get().state());
    }
}