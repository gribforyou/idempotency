package org.gribforyou;

/**
 * Represents an operation that can be executed with idempotency guarantees.
 *
 * @param <T> the type of the operation result
 */
@FunctionalInterface
public interface Operation<T> {

    /**
     * Executes the operation.
     *
     * @return the operation result
     * @throws Exception if the operation fails
     */
    T execute() throws Exception;
}
