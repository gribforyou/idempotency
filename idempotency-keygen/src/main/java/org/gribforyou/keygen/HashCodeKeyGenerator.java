package org.gribforyou.keygen;

import org.gribforyou.IdempotencyKey;
import org.gribforyou.IdempotencyKeyGenerator;

import java.util.Arrays;
import java.util.Objects;

/**
 * Implementation of {@link IdempotencyKeyGenerator} that generates keys based on
 * the hash code of the operation type and parameters.
 * <p>
 * The generated key has the following format:
 * <pre>
 * {operationType}:{hashCode}
 * </pre>
 * where {@code hashCode} is calculated from the operation type and all parameters.
 * <p>
 * <strong>Note:</strong> This implementation has a small probability of hash collisions.
 * For critical applications, consider using {@link ConcatenatingKeyGenerator} or
 * a custom implementation with stronger hash functions (e.g., SHA-256).
 *
 * @see ConcatenatingKeyGenerator
 */
public class HashCodeKeyGenerator implements IdempotencyKeyGenerator {

    /**
     * Generates an idempotency key based on the hash code of operation type and parameters.
     * <p>
     * <strong>Warning:</strong> This implementation is not recommended for production use due to high collision risk.
     * Only use for non-critical, low-volume operations where performance is paramount.
     *
     * @param operationType the type of operation
     * @param params operation parameters
     * @return an idempotency key in format "{operationType}:{hashCode}"
     * @throws IllegalArgumentException if operationType is null
     */
    @Override
    public IdempotencyKey generate(String operationType, Object[] params) {
        Objects.requireNonNull(operationType, "operationType must not be null");
        Objects.requireNonNull(params, "params must not be null");

        int hashCode = generateHashCode(params);
        String key = operationType + ":" + hashCode;

        return new IdempotencyKey(key);
    }

    /**
     * Generates a hash code for the parameters.
     *
     * @param params operation parameters
     * @return hashcode
     */
    protected int generateHashCode(Object[] params) {
        return Arrays.hashCode(params);
    }
}
