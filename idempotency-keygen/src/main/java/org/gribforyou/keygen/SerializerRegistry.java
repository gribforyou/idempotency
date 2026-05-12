package org.gribforyou.keygen;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for managing {@link ObjectSerializer} instances by class type.
 * <p>
 * Allows registering custom serializers for specific classes, which are then used
 * during idempotency key generation to serialize parameters. If no serializer is
 * registered for a class, a default serializer is used instead.
 * <p>
 * Example usage:
 * <pre>{@code
 * SerializerRegistry registry = new SerializerRegistry();
 * registry.registerSerializer(MyDto.class, new JsonObjectSerializer());
 * registry.registerSerializer(User.class, User::getId);
 * }</pre>
 *
 * @see ConcatenatingKeyGenerator
 * @see ObjectSerializer
 */
public class SerializerRegistry {
    private final Map<Class<?>, ObjectSerializer> registry;

    /**
     * Creates a new empty serializer registry.
     */
    public SerializerRegistry() {
        registry = new HashMap<>();
    }

    /**
     * Retrieves the serializer registered for the specified class.
     *
     * @param clazz the class to find a serializer for
     * @param <T> type of the class
     * @return the registered serializer, or {@code null} if none is registered
     */
    public <T> ObjectSerializer getSerializer(Class<T> clazz) {
        return registry.get(clazz);
    }

    /**
     * Registers a serializer for the specified class type.
     * <p>
     * If a serializer was already registered for this class, it will be replaced.
     *
     * @param clazz the class to register the serializer for
     * @param serializer the serializer to use for this class type
     * @param <T> type of the class
     */
    public <T> void registerSerializer(Class<T> clazz, ObjectSerializer serializer) {
        registry.put(clazz, serializer);
    }
}
