package org.gribforyou;

/**
 * Represents a unique idempotency key for an operation.
 * <p>
 * The key is used to identify and deduplicate identical operations.
 * Equality and hash code are based solely on the key string.
 *
 * @param key the unique key string
 */
public record IdempotencyKey(String key) {
    public IdempotencyKey {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdempotencyKey that = (IdempotencyKey) o;
        return key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }
}
