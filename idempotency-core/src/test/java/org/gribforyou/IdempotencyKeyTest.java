package org.gribforyou;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link IdempotencyKey}.
 */
class IdempotencyKeyTest {

    @Test
    void testKeyCreation() {
        IdempotencyKey key = new IdempotencyKey("test-key");
        assertEquals("test-key", key.key());
    }


    @Test
    void testEquals_SameValue() {
        IdempotencyKey key1 = new IdempotencyKey("test-key");
        IdempotencyKey key2 = new IdempotencyKey("test-key");
        assertEquals(key1, key2);
    }

    @Test
    void testEquals_DifferentValue() {
        IdempotencyKey key1 = new IdempotencyKey("test-key-1");
        IdempotencyKey key2 = new IdempotencyKey("test-key-2");
        assertNotEquals(key1, key2);
    }

    @Test
    void testHashCode_SameValue() {
        IdempotencyKey key1 = new IdempotencyKey("test-key");
        IdempotencyKey key2 = new IdempotencyKey("test-key");
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    void testHashCode_DifferentValue() {
        IdempotencyKey key1 = new IdempotencyKey("test-key-1");
        IdempotencyKey key2 = new IdempotencyKey("test-key-2");
        assertNotEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    void testConstructor_NullKey() {
        assertThrows(IllegalArgumentException.class, () -> new IdempotencyKey(null));
    }

    @Test
    void testToString() {
        IdempotencyKey key = new IdempotencyKey("test-key");
        String toString = key.toString();
        assertTrue(toString.contains("test-key"));
    }
}
