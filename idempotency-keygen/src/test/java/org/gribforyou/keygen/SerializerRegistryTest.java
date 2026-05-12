package org.gribforyou.keygen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SerializerRegistry}.
 */
class SerializerRegistryTest {

    private SerializerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SerializerRegistry();
    }

    @Test
    void getSerializer_whenNoSerializerRegistered_shouldReturnNull() {
        // When
        ObjectSerializer serializer = registry.getSerializer(String.class);

        // Then
        assertNull(serializer, "Должен вернуться null, если сериализатор не зарегистрирован");
    }

    @Test
    void registerSerializer_whenRegistered_shouldReturnSameSerializer() {
        // Given
        ObjectSerializer expectedSerializer = Object::toString;

        // When
        registry.registerSerializer(String.class, expectedSerializer);
        ObjectSerializer actualSerializer = registry.getSerializer(String.class);

        // Then
        assertNotNull(actualSerializer);
        assertSame(expectedSerializer, actualSerializer, "Должен вернуться тот же экземпляр сериализатора");
    }

    @Test
    void registerSerializer_whenOverwritten_shouldReturnNewSerializer() {
        // Given
        ObjectSerializer firstSerializer = Object::toString;
        ObjectSerializer secondSerializer = obj -> "custom";

        registry.registerSerializer(String.class, firstSerializer);

        // When
        registry.registerSerializer(String.class, secondSerializer);
        ObjectSerializer actualSerializer = registry.getSerializer(String.class);

        // Then
        assertNotNull(actualSerializer);
        assertSame(secondSerializer, actualSerializer, "Должен вернуться новый сериализатор после перезаписи");
    }

    @Test
    void getSerializer_whenRegisteredForSubclass_shouldNotReturnSuperclassSerializer() {
        // Given
        ObjectSerializer objectSerializer = obj -> "object";
        registry.registerSerializer(Object.class, objectSerializer);

        // When
        ObjectSerializer stringSerializer = registry.getSerializer(String.class);

        // Then
        // Реестр работает по точному совпадению ключа (Class), наследование не учитывается автоматически
        assertNull(stringSerializer, "Для подкласса не должен возвращаться сериализатор родительского класса");
    }

    @Test
    void registerSerializer_whenDifferentClasses_shouldStoreIndependently() {
        // Given
        ObjectSerializer stringSerializer = obj -> "string";
        ObjectSerializer integerSerializer = obj -> "integer";

        // When
        registry.registerSerializer(String.class, stringSerializer);
        registry.registerSerializer(Integer.class, integerSerializer);

        // Then
        assertSame(stringSerializer, registry.getSerializer(String.class));
        assertSame(integerSerializer, registry.getSerializer(Integer.class));
    }

    @Test
    void registerSerializer_whenNullSerializer_shouldStoreNull() {
        // Given
        ObjectSerializer nullSerializer = null;

        // When
        registry.registerSerializer(String.class, nullSerializer);
        ObjectSerializer actualSerializer = registry.getSerializer(String.class);

        // Then
        assertNull(actualSerializer, "Должна быть возможность зарегистрировать null (если логика позволяет)");
    }
}