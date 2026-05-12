package org.gribforyou.keygen.serializer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ToHashCodeObjectSerializer}.
 */
class ToHashCodeObjectSerializerTest {

    private final ToHashCodeObjectSerializer serializer = new ToHashCodeObjectSerializer();

    @Test
    void serialize_whenObjectProvided_shouldReturnHashCodeAsString() {
        // Given
        Object testObject = new Object();
        String expectedHash = String.valueOf(testObject.hashCode());

        // When
        String actualResult = serializer.serialize(testObject);

        // Then
        assertEquals(expectedHash, actualResult, "Результат должен совпадать со строковым представлением hashCode");
    }

    @Test
    void serialize_whenStringProvided_shouldReturnStringHashCode() {
        // Given
        String testString = "testValue";
        String expectedHash = String.valueOf(testString.hashCode());

        // When
        String actualResult = serializer.serialize(testString);

        // Then
        assertEquals(expectedHash, actualResult);
    }

    @Test
    void serialize_whenIntegerProvided_shouldReturnIntegerHashCode() {
        // Given
        Integer testInteger = 42;
        // Для Integer hashCode() возвращает само значение
        String expectedHash = String.valueOf(testInteger.hashCode());

        // When
        String actualResult = serializer.serialize(testInteger);

        // Then
        assertEquals(expectedHash, actualResult);
        assertEquals("42", actualResult);
    }

    @Test
    void serialize_whenNullProvided_shouldThrowNullPointerException() {
        // When & Then
        // Метод hashCode() на null выбросит NullPointerException, так как в реализации нет проверки
        assertThrows(
                NullPointerException.class,
                () -> serializer.serialize(null),
                "Сериализация null должна выбрасывать NullPointerException"
        );
    }

    @Test
    void serialize_whenEqualObjectsProvided_shouldReturnSameHash() {
        // Given
        String obj1 = new String("sameValue");
        String obj2 = new String("sameValue");

        // Когда объекты равны (equals), их hashCode должен совпадать по контракту Java
        assertEquals(obj1.hashCode(), obj2.hashCode());

        // When
        String hash1 = serializer.serialize(obj1);
        String hash2 = serializer.serialize(obj2);

        // Then
        assertEquals(hash1, hash2, "Равные объекты должны иметь одинаковую сериализацию хэш-кода");
    }

    @Test
    void serialize_whenDifferentObjectsProvided_shouldLikelyReturnDifferentHash() {
        // Given
        String obj1 = "valueOne";
        String obj2 = "valueTwo";

        // When
        String hash1 = serializer.serialize(obj1);
        String hash2 = serializer.serialize(obj2);

        // Then
        // Хотя коллизии теоретически возможны, для разных строк они маловероятны
        assertNotEquals(hash1, hash2, "Разные объекты должны иметь разный хэш-код (за исключением коллизий)");
    }
}