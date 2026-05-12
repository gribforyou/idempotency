package org.gribforyou;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JdbcIdempotencyStoreTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    private static DataSource dataSource;
    private JdbcIdempotencyStore<TestResult> store;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String tableName = "idempotency_records";
    private final Duration lockTimeout = Duration.ofSeconds(10);
    private final Duration ttl = Duration.ofHours(1);

    @BeforeAll
    static void startContainer() throws SQLException {
        postgres.start();

        org.postgresql.ds.PGSimpleDataSource ds = new org.postgresql.ds.PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        dataSource = ds;
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP TABLE IF EXISTS " + tableName);
            stmt.execute("""
                CREATE TABLE %s (
                    record_key VARCHAR(255) PRIMARY KEY,
                    state VARCHAR(50) NOT NULL,
                    result_data TEXT,
                    error_data TEXT,
                    created_at BIGINT NOT NULL,
                    ttl BIGINT NOT NULL,
                    locked_until BIGINT
                )
            """.formatted(tableName));
        }

        store = JdbcIdempotencyStore.<TestResult>builder()
                .dataSource(dataSource)
                .objectMapper(objectMapper)
                .tableName(tableName)
                .resultType(TestResult.class)
                .build();
    }

    public static class TestResult {
        public String message;
        public int code;

        public TestResult() {}
        public TestResult(String message, int code) {
            this.message = message;
            this.code = code;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestResult that = (TestResult) o;
            return code == that.code && message.equals(that.message);
        }
    }

    @Test
    void getActiveRecordOrLock_whenNewKey_shouldCreateLockedRecordAndReturnEmpty() {
        IdempotencyKey key = new IdempotencyKey("new-key");
        Optional<IdempotencyRecord<TestResult>> result = store.getActiveRecordOrLock(key, lockTimeout);

        assertTrue(result.isEmpty());
        Optional<IdempotencyRecord<TestResult>> recordFromDb = store.get(key);
        assertTrue(recordFromDb.isPresent());
        assertEquals(RecordState.LOCKED, recordFromDb.get().state());
        assertNull(recordFromDb.get().result());
    }

    @Test
    void getActiveRecordOrLock_whenExistingLockedRecord_shouldReturnRecord() {
        IdempotencyKey key = new IdempotencyKey("locked-key");
        store.getActiveRecordOrLock(key, lockTimeout);

        Optional<IdempotencyRecord<TestResult>> result = store.getActiveRecordOrLock(key, lockTimeout);
        assertTrue(result.isPresent());
        assertEquals(RecordState.LOCKED, result.get().state());
    }

    @Test
    void getActiveRecordOrLock_whenExecutedRecord_shouldReturnExecutedRecord() {
        IdempotencyKey key = new IdempotencyKey("executed-key");
        store.getActiveRecordOrLock(key, lockTimeout);
        store.saveResult(key, new TestResult("Success", 200), ttl);

        Optional<IdempotencyRecord<TestResult>> result = store.getActiveRecordOrLock(key, lockTimeout);
        assertTrue(result.isPresent());
        assertEquals(RecordState.EXECUTED, result.get().state());
        assertEquals("Success", result.get().result().message);
    }

    @Test
    void getActiveRecordOrLock_whenFailedRecord_shouldResetAndReturnEmpty() {
        IdempotencyKey key = new IdempotencyKey("failed-key");
        store.getActiveRecordOrLock(key, lockTimeout);
        store.saveError(key, new RuntimeException("Test error"), ttl);

        Optional<IdempotencyRecord<TestResult>> result = store.getActiveRecordOrLock(key, lockTimeout);
        assertTrue(result.isEmpty());

        Optional<IdempotencyRecord<TestResult>> recordFromDb = store.get(key);
        assertEquals(RecordState.LOCKED, recordFromDb.get().state());
        assertNull(recordFromDb.get().error());
    }

    @Test
    void getActiveRecordOrLock_whenExpiredExecutedRecord_shouldResetAndReturnEmpty() throws InterruptedException {
        IdempotencyKey key = new IdempotencyKey("expired-key");
        Duration shortTtl = Duration.ofMillis(100);

        store.getActiveRecordOrLock(key, lockTimeout);
        store.saveResult(key, new TestResult("Old", 1), shortTtl);
        Thread.sleep(150);

        Optional<IdempotencyRecord<TestResult>> result = store.getActiveRecordOrLock(key, lockTimeout);
        assertTrue(result.isEmpty());
        assertEquals(RecordState.LOCKED, store.get(key).get().state());
    }

    @Test
    void saveResult_whenLockedRecord_shouldUpdateToExecuted() {
        IdempotencyKey key = new IdempotencyKey("save-result-key");
        store.getActiveRecordOrLock(key, lockTimeout);
        TestResult resultData = new TestResult("Operation Success", 200);

        IdempotencyRecord<TestResult> savedRecord = store.saveResult(key, resultData, ttl);
        assertEquals(RecordState.EXECUTED, savedRecord.state());
        assertEquals("Operation Success", savedRecord.result().message);
        assertEquals(RecordState.EXECUTED, store.get(key).get().state());
    }

    @Test
    void saveResult_whenNoRecord_shouldThrowIllegalStateException() {
        IdempotencyKey key = new IdempotencyKey("non-existent-key");
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> store.saveResult(key, new TestResult("Test", 1), ttl));
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    void saveResult_whenAlreadyExecuted_shouldThrowIllegalStateException() {
        IdempotencyKey key = new IdempotencyKey("double-save-key");
        store.getActiveRecordOrLock(key, lockTimeout);
        store.saveResult(key, new TestResult("First", 1), ttl);

        assertThrows(IllegalStateException.class,
                () -> store.saveResult(key, new TestResult("Second", 2), ttl));
    }

    @Test
    void saveError_whenLockedRecord_shouldUpdateToFailed() {
        IdempotencyKey key = new IdempotencyKey("save-error-key");
        store.getActiveRecordOrLock(key, lockTimeout);
        store.saveError(key, new RuntimeException("Business Exception"), ttl);

        assertEquals(RecordState.FAILED, store.get(key).get().state());
    }

    @Test
    void saveError_whenAlreadyExecuted_shouldThrowIllegalStateException() {
        IdempotencyKey key = new IdempotencyKey("error-after-success-key");
        store.getActiveRecordOrLock(key, lockTimeout);
        store.saveResult(key, new TestResult("Success", 1), ttl);

        assertThrows(IllegalStateException.class,
                () -> store.saveError(key, new RuntimeException("Error"), ttl));
    }

    @Test
    void get_whenRecordExists_shouldReturnRecord() {
        IdempotencyKey key = new IdempotencyKey("get-key");
        store.getActiveRecordOrLock(key, lockTimeout);
        store.saveResult(key, new TestResult("Get Test", 123), ttl);

        Optional<IdempotencyRecord<TestResult>> result = store.get(key);
        assertTrue(result.isPresent());
        assertEquals("Get Test", result.get().result().message);
        assertEquals(123, result.get().result().code);
    }

    @Test
    void get_whenRecordDoesNotExist_shouldReturnEmpty() {
        assertTrue(store.get(new IdempotencyKey("missing-key")).isEmpty());
    }

    @Test
    void getActiveRecordOrLock_concurrentCalls_sameKey_shouldAllowOnlyOneToProceed() throws InterruptedException {
        IdempotencyKey key = new IdempotencyKey("concurrent-key");
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger emptyCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    if (store.getActiveRecordOrLock(key, lockTimeout).isEmpty()) {
                        emptyCount.incrementAndGet();
                        Thread.sleep(10);
                        store.saveResult(key, new TestResult("Concurrent", 1), ttl);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(1, emptyCount.get());
        assertEquals(RecordState.EXECUTED, store.get(key).get().state());
    }
}