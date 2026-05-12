package org.gribforyou;

import org.junit.jupiter.api.Test;

import static org.gribforyou.RecordState.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link IdempotencyRecord}.
 */
class IdempotencyRecordTest {

    private final IdempotencyKey key = new IdempotencyKey("test-key");
    private final long now = System.currentTimeMillis();

    @Test
    void testRecordCreation_Executed() {
        String result = "test-result";
        IdempotencyRecord<String> record = new IdempotencyRecord<>(key, result, null, EXECUTED, now, 3600000L, null);

        assertEquals(key, record.key());
        assertEquals(result, record.result());
        assertNull(record.error());
        assertEquals(EXECUTED, record.state());
        assertEquals(now, record.createdAt());
    }

    @Test
    void testRecordCreation_Failed() {
        RuntimeException error = new RuntimeException("test error");
        IdempotencyRecord<String> record = new IdempotencyRecord<>(key, null, error, FAILED, now, 3600000, null);

        assertEquals(key, record.key());
        assertNull(record.result());
        assertEquals(error, record.error());
        assertEquals(FAILED, record.state());
    }

    @Test
    void testRecordCreation_Locked() {
        long lockedUntil = now + 30000;
        IdempotencyRecord<String> record = new IdempotencyRecord<>(key, null, null, LOCKED, now, 3600000, lockedUntil);

        assertEquals(key, record.key());
        assertEquals(LOCKED, record.state());
        assertEquals(lockedUntil, record.lockedUntil());
    }

    @Test
    void testIsExpired_NotExpired() {
        long pastTime = now - 1000;
        long ttl = 3600000;
        IdempotencyRecord<String> record = new IdempotencyRecord<>(key, "result", null, EXECUTED, pastTime, ttl, null);

        assertFalse(record.isExpired());
    }

    @Test
    void testIsExpired_Expired() {
        long pastTime = now - 7200000;
        long ttl = 3600000;
        IdempotencyRecord<String> record = new IdempotencyRecord<>(key, "result", null, EXECUTED, pastTime, ttl, null);

        assertTrue(record.isExpired());
    }

    @Test
    void testIsExpired_JustAtTtlBoundary() throws InterruptedException {
        long ttl = 100;
        long createdAt = System.currentTimeMillis() - ttl;
        IdempotencyRecord<String> record = new IdempotencyRecord<>(key, "result", null, EXECUTED, createdAt, ttl, null);
        
        Thread.sleep(10);
        
        assertTrue(record.isExpired());
    }

    @Test
    void testIsStillLocked_NotLockedState() {
        IdempotencyRecord<String> record = new IdempotencyRecord<>(key, "result", null, EXECUTED, now, 3600000, null);
        assertFalse(record.isStillLocked());
    }

    @Test
    void testIsStillLocked_LockedButExpired() {
        long lockedUntil = now - 1000; // expired 1 second ago
        IdempotencyRecord<String> record = new IdempotencyRecord<>(key, null, null, LOCKED, now, 3600000, lockedUntil);

        assertFalse(record.isStillLocked());
    }

    @Test
    void testIsStillLocked_LockedAndValid() {
        long lockedUntil = now + 30000;
        IdempotencyRecord<String> record = new IdempotencyRecord<>(key, null, null, LOCKED, now, 3600000, lockedUntil);

        assertTrue(record.isStillLocked());
    }

    @Test
    void testGetResult_ExecutedState() {
        String expectedResult = "test-result";
        IdempotencyRecord<String> record = new IdempotencyRecord<>(key, expectedResult, null, EXECUTED, now, 3600000, null);

        assertEquals(expectedResult, record.getResult());
    }

    @Test
    void testGetResult_FailedState_ThrowsException() {
        RuntimeException error = new RuntimeException("test error");
        IdempotencyRecord<String> record = new IdempotencyRecord<>(key, null, error, FAILED, now, 3600000, null);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, record::getResult);
        assertEquals("Record is in FAILED state", thrown.getMessage());
        assertSame(error, thrown.getCause());
    }

    @Test
    void testGetResult_LockedState_ReturnsNull() {
        IdempotencyRecord<String> record = new IdempotencyRecord<>(key, null, null, LOCKED, now, 3600000, System.currentTimeMillis() + 30000);

        assertNull(record.getResult());
    }

    @Test
    void testEquals_SameReference() {
        IdempotencyRecord<String> record = new IdempotencyRecord<>(key, "result", null, EXECUTED, now, 3600000, null);
        assertSame(record, record);
    }

    @Test
    void testEquals_SameValues() {
        IdempotencyRecord<String> record1 = new IdempotencyRecord<>(key, "result", null, EXECUTED, now, 3600000, null);
        IdempotencyRecord<String> record2 = new IdempotencyRecord<>(key, "result", null, EXECUTED, now, 3600000, null);

        assertEquals(record1, record2);
    }

    @Test
    void testEquals_DifferentResult() {
        IdempotencyRecord<String> record1 = new IdempotencyRecord<>(key, "result1", null, EXECUTED, now, 3600000, null);
        IdempotencyRecord<String> record2 = new IdempotencyRecord<>(key, "result2", null, EXECUTED, now, 3600000, null);

        assertNotEquals(record1, record2);
    }

    @Test
    void testEquals_DifferentState() {
        IdempotencyRecord<String> record1 = new IdempotencyRecord<>(key, "result", null, EXECUTED, now, 3600000, null);
        IdempotencyRecord<String> record2 = new IdempotencyRecord<>(key, "result", null, FAILED, now, 3600000, null);

        assertNotEquals(record1, record2);
    }

    @Test
    void testHashCode_Consistent() {
        IdempotencyRecord<String> record = new IdempotencyRecord<>(key, "result", null, EXECUTED, now, 3600000, null);
        int hashCode1 = record.hashCode();
        int hashCode2 = record.hashCode();

        assertEquals(hashCode1, hashCode2);
    }
}
