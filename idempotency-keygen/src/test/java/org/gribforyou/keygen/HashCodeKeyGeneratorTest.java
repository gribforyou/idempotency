package org.gribforyou.keygen;

import org.gribforyou.IdempotencyKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HashCodeKeyGenerator}.
 */
class HashCodeKeyGeneratorTest {

    private final HashCodeKeyGenerator keyGenerator = new HashCodeKeyGenerator();

    @Test
    void testGenerate_SameParameters_SameKey() {
        IdempotencyKey key1 = keyGenerator.generate("TEST_OP", new Object[]{"param1", "param2"});
        IdempotencyKey key2 = keyGenerator.generate("TEST_OP", new Object[]{"param1", "param2"});

        assertEquals(key1, key2);
    }

    @Test
    void testGenerate_DifferentOperationType_DifferentKey() {
        IdempotencyKey key1 = keyGenerator.generate("OP1", new Object[]{"param"});
        IdempotencyKey key2 = keyGenerator.generate("OP2", new Object[]{"param"});

        assertNotEquals(key1, key2);
    }

    @Test
    void testGenerate_DifferentParams_DifferentKey() {
        IdempotencyKey key1 = keyGenerator.generate("TEST_OP", new Object[]{"param1"});
        IdempotencyKey key2 = keyGenerator.generate("TEST_OP", new Object[]{"param2"});

        assertNotEquals(key1, key2);
    }

    @Test
    void testGenerate_DifferentParamCount_DifferentKey() {
        IdempotencyKey key1 = keyGenerator.generate("TEST_OP", new Object[]{"param1"});
        IdempotencyKey key2 = keyGenerator.generate("TEST_OP", new Object[]{"param1", "param2"});

        assertNotEquals(key1, key2);
    }

    @Test
    void testGenerate_EmptyParams() {
        IdempotencyKey key = keyGenerator.generate("TEST_OP", new Object[]{});

        assertNotNull(key);
        assertTrue(key.key().startsWith("TEST_OP:"));
    }

    @Test
    void testGenerate_NullParam() {
        IdempotencyKey key1 = keyGenerator.generate("TEST_OP", new Object[]{null});
        IdempotencyKey key2 = keyGenerator.generate("TEST_OP", new Object[]{null});

        assertEquals(key1, key2);
    }

    @Test
    void testGenerate_NullOperationType_ThrowsException() {
        assertThrows(NullPointerException.class, () ->
                keyGenerator.generate(null, new Object[]{"param"})
        );
    }

    @Test
    void testGenerate_NullParamsArray_ThrowsException() {
        assertThrows(NullPointerException.class, () ->
                keyGenerator.generate("TEST_OP", null)
        );
    }

    @Test
    void testGenerate_KeyFormat() {
        IdempotencyKey key = keyGenerator.generate("MY_OPERATION", new Object[]{"test"});

        assertTrue(key.key().matches("MY_OPERATION:-?\\d+"));
    }

    @Test
    void testGenerate_IntegerParams() {
        IdempotencyKey key1 = keyGenerator.generate("TEST_OP", new Object[]{1, 2, 3});
        IdempotencyKey key2 = keyGenerator.generate("TEST_OP", new Object[]{1, 2, 3});

        assertEquals(key1, key2);
    }

    @Test
    void testGenerate_MixedTypes() {
        IdempotencyKey key1 = keyGenerator.generate("TEST_OP", new Object[]{"string", 123, true});
        IdempotencyKey key2 = keyGenerator.generate("TEST_OP", new Object[]{"string", 123, true});

        assertEquals(key1, key2);
    }

    @Test
    void testGenerate_ParamOrderMatters() {
        IdempotencyKey key1 = keyGenerator.generate("TEST_OP", new Object[]{"a", "b"});
        IdempotencyKey key2 = keyGenerator.generate("TEST_OP", new Object[]{"b", "a"});

        assertNotEquals(key1, key2);
    }

    @Test
    void testGenerate_ConsistentHashCode() {
        // Run multiple times to ensure consistency
        IdempotencyKey key1 = keyGenerator.generate("TEST_OP", new Object[]{"param1", "param2"});
        IdempotencyKey key2 = keyGenerator.generate("TEST_OP", new Object[]{"param1", "param2"});
        IdempotencyKey key3 = keyGenerator.generate("TEST_OP", new Object[]{"param1", "param2"});

        assertEquals(key1, key2);
        assertEquals(key2, key3);
    }
}
