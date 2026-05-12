package org.gribforyou.keygen.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gribforyou.keygen.ObjectSerializer;

/**
 * {@link ObjectSerializer} that serializes objects to their JSON representation.
 * <p>
 * This serializer uses Jackson's {@link ObjectMapper} to convert objects to JSON strings.
 * It provides deterministic serialization for most Java objects, including:
 * <ul>
 *   <li>POJOs with proper getters/setters</li>
 *   <li>Collections and arrays</li>
 *   <li>Nested object structures</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * ConcatenatingKeyGenerator generator = ConcatenatingKeyGenerator.builder()
 *     .defaultSerializer(new JsonObjectSerializer())
 *     .build();
 * }</pre>
 * <p>
 * <strong>Note:</strong> Ensure that the objects being serialized have stable JSON representations.
 * Fields that change between serializations (timestamps, random values) will cause different
 * idempotency keys for logically identical operations.
 *
 * @see ToStringObjectSerializer
 * @see ToHashCodeObjectSerializer
 */
public class JsonObjectSerializer implements ObjectSerializer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Serializes the object to its JSON string representation.
     *
     * @param object the object to serialize to JSON
     * @return JSON string representation of the object
     * @throws RuntimeException if JSON processing fails
     */
    @Override
    public String serialize(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
