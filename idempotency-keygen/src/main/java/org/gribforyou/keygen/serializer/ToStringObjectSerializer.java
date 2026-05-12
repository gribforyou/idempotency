package org.gribforyou.keygen.serializer;

import org.gribforyou.keygen.ObjectSerializer;

/**
 * Simple {@link ObjectSerializer} that uses the object's {@link Object#toString()} method.
 * <p>
 * This serializer is the default for {@link org.gribforyou.keygen.ConcatenatingKeyGenerator}.
 * It works well for:
 * <ul>
 *   <li>String, Number, and other JDK classes with meaningful {@code toString()}</li>
 *   <li>Custom classes that override {@code toString()} with a deterministic representation</li>
 * </ul>
 * <p>
 * <strong>Warning:</strong> For classes that don't override {@code toString()}, this will use
 * the default implementation (class name + hash code), which is NOT deterministic across JVM runs.
 *
 * @see ToHashCodeObjectSerializer
 * @see JsonObjectSerializer
 */
public class ToStringObjectSerializer implements ObjectSerializer {

    /**
     * Serializes the object by calling its {@code toString()} method.
     *
     * @param object the object to serialize
     * @return result of {@code object.toString()}
     */
    @Override
    public String serialize(Object object) {
        return object.toString();
    }
}
