package org.gribforyou.keygen.serializer;

import org.gribforyou.keygen.ObjectSerializer;

/**
 * {@link ObjectSerializer} that serializes objects to their {@link Object#hashCode()} value.
 * <p>
 * This serializer converts objects to their hash code string representation.
 * <p>
 * <strong>Warning:</strong> This serializer has significant collision risk and should only be used
 * for non-critical operations where:
 * <ul>
 *   <li>Key length must be minimal</li>
 *   <li>Collision probability is acceptable</li>
 *   <li>Objects have well-distributed hash codes</li>
 * </ul>
 * <p>
 * For better collision resistance, consider using {@link JsonObjectSerializer} combined
 * with a hash-based {@link org.gribforyou.keygen.StringCompressor}.
 *
 * @see ToStringObjectSerializer
 * @see JsonObjectSerializer
 */
public class ToHashCodeObjectSerializer implements ObjectSerializer {

    /**
     * Serializes the object to its hash code string representation.
     *
     * @param object the object to serialize
     * @return string representation of {@code object.hashCode()}
     */
    @Override
    public String serialize(Object object) {
        return String.valueOf(object.hashCode());
    }
}
