package org.gribforyou.keygen;

/**
 * Strategy interface for serializing Java objects to strings during idempotency key generation.
 * <p>
 * Implementations define how objects are converted to their string representation,
 * which is then used as part of the idempotency key. Different serialization strategies
 * can be used depending on the required level of detail and determinism.
 * <p>
 * Example implementations:
 * <ul>
 *   <li>{@link org.gribforyou.keygen.serializer.ToStringObjectSerializer} - uses {@code toString()}</li>
 *   <li>{@link org.gribforyou.keygen.serializer.ToHashCodeObjectSerializer} - uses {@code hashCode()}</li>
 *   <li>{@link org.gribforyou.keygen.serializer.JsonObjectSerializer} - uses JSON serialization</li>
 * </ul>
 *
 * @see ConcatenatingKeyGenerator
 * @see SerializerRegistry
 */
public interface ObjectSerializer {

    /**
     * Serializes the given object to a string representation.
     * <p>
     * The implementation must be deterministic - the same object state must always
     * produce the same string output to ensure consistent idempotency key generation.
     *
     * @param object the object to serialize (may be null depending on implementation)
     * @return string representation of the object
     * @throws RuntimeException if serialization fails
     */
    String serialize(Object object);
}
