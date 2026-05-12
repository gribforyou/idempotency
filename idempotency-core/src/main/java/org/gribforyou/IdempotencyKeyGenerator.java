package org.gribforyou;

/**
 * Generates idempotency keys for operations.
 * <p>
 * Implementations MUST ensure that the same operation with the same parameters
 * always produces the same key, and different operations/parameters produce
 * unique keys to avoid collisions.
 */
public interface IdempotencyKeyGenerator {

    /**
     * Generates an idempotency key for the given operation type and parameters.
     *
     * @param operationType the type of operation (e.g., "PAYMENT", "ORDER_CREATE")
     * @param params operation parameters used for key generation
     * @return a unique idempotency key
     */
    IdempotencyKey generate(String operationType, Object[] params);
}
