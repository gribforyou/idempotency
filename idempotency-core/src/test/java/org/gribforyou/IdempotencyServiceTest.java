package org.gribforyou;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link IdempotencyService}.
 */
class IdempotencyServiceTest {

    private final String operationType = "testOperation";
    private final Object[] params = new Object[]{"param1", "param2"};
    private final Duration ttl = Duration.ofHours(1);
    private final Duration lockTimeout = Duration.ofMillis(20);
    private final int maxLockWaitAttempts = 3;
    private final String result = "testResult";
    private final IdempotencyKey key = new IdempotencyKey("test-key");
    private final IdempotencyStore<String> store = mock(IdempotencyStore.class);
    private final IdempotencyKeyGenerator keyGenerator = mock(IdempotencyKeyGenerator.class);
    private final Operation<String> operation = mock(Operation.class);
    private IdempotencyService<String> service = IdempotencyService.builder(String.class)
            .store(store)
            .keyGenerator(keyGenerator)
            .maxLockWaitAttempts(maxLockWaitAttempts)
            .lockTimeout(lockTimeout)
            .defaultTtl(ttl)
            .build();


    @Test
    void execute_whenNoRecordExists_shouldExecuteAndSaveResult() throws Exception {
        // Given
        when(keyGenerator.generate(operationType, params)).thenReturn(key);
        when(store.getActiveRecordOrLock(key, ttl)).thenReturn(Optional.empty());
        when(operation.execute()).thenReturn(result);

        // When
        String curResult = service.execute(operationType, params, operation);

        // Then
        assertAll(
                () -> assertEquals(result, curResult),
                () -> verify(store).getActiveRecordOrLock(key, lockTimeout),
                () -> verify(store).saveResult(key, result, ttl),
                () -> verifyNoMoreInteractions(store)
        );
    }

    @Test
    void execute_whenNoRecordExists_failedWhileSaveRecord() throws Exception {
        // Given
        RuntimeException exception = new RuntimeException("Failed to save result");
        when(keyGenerator.generate(operationType, params)).thenReturn(key);
        when(store.getActiveRecordOrLock(key, ttl)).thenReturn(Optional.empty());
        when(operation.execute()).thenReturn(result);
        when(store.saveResult(key, result, ttl)).thenThrow(exception);

        // Then
        assertAll(
                () -> assertThrows(IdempotencyOperationExecutionFailedException.class, () -> service.execute(operationType, params, operation)),
                () -> verify(store, times(1)).getActiveRecordOrLock(key, lockTimeout),
                () -> verify(store, times(1)).saveResult(key, result, ttl),
                () -> verify(store, times(1)).saveError(key, exception, ttl)
        );
    }

    @Test
    void execute_whenNoRecordExists_failedWhileSaveRecordAndSaveError() throws Exception {
        // Given
        RuntimeException exception = new RuntimeException("Failed to save result");
        when(keyGenerator.generate(operationType, params)).thenReturn(key);
        when(store.getActiveRecordOrLock(key, ttl)).thenReturn(Optional.empty());
        when(operation.execute()).thenReturn(result);
        when(store.saveResult(key, result, ttl)).thenThrow(exception);
        when(store.saveError(key, exception, ttl)).thenThrow(exception);

        // Then
        assertAll(
                () -> assertThrows(IdempotencyOperationExecutionFailedException.class, () -> service.execute(operationType, params, operation)),
                () -> verify(store, times(1)).getActiveRecordOrLock(key, lockTimeout),
                () -> verify(store, times(1)).saveResult(key, result, ttl),
                () -> verify(store, times(1)).saveError(key, exception, ttl)
        );
    }

    @Test
    void execute_whenRecordExistsWithExecutedStatus_shouldReturnCachedResult() throws Exception {
        // Given
        String cachedResult = "cachedResult";
        IdempotencyRecord<String> executedRecord = new IdempotencyRecord<>(
                key,
                cachedResult,
                null,
                RecordState.EXECUTED,
                System.currentTimeMillis(),
                ttl.toMillis(),
                null
        );

        when(keyGenerator.generate(operationType, params)).thenReturn(key);
        when(store.getActiveRecordOrLock(key, lockTimeout)).thenReturn(Optional.of(executedRecord));

        // When
        String result = service.execute(operationType, params, operation);

        // Then
        assertAll(
                () -> assertEquals(cachedResult, result),
                () -> verify(store).getActiveRecordOrLock(key, lockTimeout),
                () -> verify(operation, never()).execute(),
                () -> verify(store, never()).saveResult(any(), any(), any()),
                () -> verify(store, never()).saveError(any(), any(), any())
        );
    }

    @Test
    void execute_whenRecordExistsWithFailedStatus_shouldThrowIllegalStateException() {
        // Given
        RuntimeException originalError = new RuntimeException("Previous execution failed");
        IdempotencyRecord<String> failedRecord = new IdempotencyRecord<>(
                key,
                null,
                originalError,
                RecordState.FAILED,
                System.currentTimeMillis(),
                ttl.toMillis(),
                null
        );

        when(keyGenerator.generate(operationType, params)).thenReturn(key);
        when(store.getActiveRecordOrLock(key, lockTimeout)).thenReturn(Optional.of(failedRecord));

        // When & Then
        assertAll(
                () -> assertThrows(IllegalStateException.class, () -> service.execute(operationType, params, operation)),
                () -> verify(store).getActiveRecordOrLock(key, lockTimeout),
                () -> verify(operation, never()).execute(),
                () -> verify(store, never()).saveResult(any(), any(), any()),
                () -> verify(store, never()).saveError(any(), any(), any())
        );
    }

    @Test
    void execute_whenRecordLockedFirstThenExecuted_shouldReturnCachedResult() throws Exception {
        // Given
        String cachedResult = "concurrentResult";

        long futureLockTime = System.currentTimeMillis() + 100000;
        IdempotencyRecord<String> lockedRecord = new IdempotencyRecord<>(
                key,
                null,
                null,
                RecordState.LOCKED,
                System.currentTimeMillis(),
                ttl.toMillis(),
                futureLockTime
        );
        IdempotencyRecord<String> executedRecord = new IdempotencyRecord<>(
                key,
                cachedResult,
                null,
                RecordState.EXECUTED,
                System.currentTimeMillis(),
                ttl.toMillis(),
                null
        );

        when(keyGenerator.generate(operationType, params)).thenReturn(key);
        when(store.getActiveRecordOrLock(key, lockTimeout))
                .thenReturn(Optional.of(lockedRecord))
                .thenReturn(Optional.of(executedRecord));

        // When
        String result = service.execute(operationType, params, operation);

        // Then
        assertAll(
                () -> assertEquals(cachedResult, result),
                () -> verify(store, times(2)).getActiveRecordOrLock(key, lockTimeout),
                () -> verify(operation, never()).execute(),
                () -> verify(store, never()).saveResult(any(), any(), any()),
                () -> verify(store, never()).saveError(any(), any(), any())
        );
    }

    @Test
    void execute_whenRecordLockedAndStatusNotChanged_shouldThrowException() {
        // Given
        long futureLockTime = System.currentTimeMillis() + 100000;
        IdempotencyRecord<String> lockedRecord = new IdempotencyRecord<>(
                key,
                null,
                null,
                RecordState.LOCKED,
                System.currentTimeMillis(),
                ttl.toMillis(),
                futureLockTime
        );

        when(keyGenerator.generate(operationType, params)).thenReturn(key);

        when(store.getActiveRecordOrLock(key, lockTimeout))
                .thenReturn(Optional.of(lockedRecord))
                .thenReturn(Optional.of(lockedRecord))
                .thenReturn(Optional.of(lockedRecord))
                .thenReturn(Optional.of(lockedRecord));

        assertAll(
                () -> {
                    IdempotencyOperationExecutionFailedException exception = assertThrows(
                            IdempotencyOperationExecutionFailedException.class,
                            () -> service.execute(operationType, params, operation)
                    );
                    assertTrue(exception.getMessage().contains("Maximum lock wait attempts exceeded"));
                },
                () -> verify(store, times(4)).getActiveRecordOrLock(key, lockTimeout), // 1 первичный + 3 retry
                () -> verify(operation, never()).execute(),
                () -> verify(store, never()).saveResult(any(), any(), any()),
                () -> verify(store, never()).saveError(any(), any(), any())
        );
    }
}
