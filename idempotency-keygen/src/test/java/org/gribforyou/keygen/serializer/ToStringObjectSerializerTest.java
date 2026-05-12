package org.gribforyou.keygen.serializer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ToStringObjectSerializer}.
 */
class ToStringObjectSerializerTest {

    private final ToStringObjectSerializer serializer = new ToStringObjectSerializer();

    @Test
    void serialize_whenStringProvided_shouldReturnSameString() {
        // Given
        String testString = "testValue";

        // When
        String actualResult = serializer.serialize(testString);

        // Then
        assertEquals(testString, actualResult, "Для String результат должен совпадать с исходной строкой");
    }

    @Test
    void serialize_whenIntegerProvided_shouldReturnStringRepresentation() {
        // Given
        Integer testInteger = 42;
        String expected = "42";

        // When
        String actualResult = serializer.serialize(testInteger);

        // Then
        assertEquals(expected, actualResult);
    }

    @Test
    void serialize_whenCustomObjectWithOverrideProvided_shouldReturnCustomToString() {
        // Given
        class CustomDto {
            private final String name;
            CustomDto(String name) { this.name = name; }

            @Override
            public String toString() {
                return "CustomDto{name='" + name + "'}";
            }
        }
        CustomDto obj = new CustomDto("Alice");
        String expected = "CustomDto{name='Alice'}";

        // When
        String actualResult = serializer.serialize(obj);

        // Then
        assertEquals(expected, actualResult, "Должен использоваться переопределенный метод toString()");
    }

    @Test
    void serialize_whenObjectWithoutOverrideProvided_shouldReturnDefaultToString() {
        // Given
        Object obj = new Object();
        // Формат default toString(): className@hexHash
        // Мы не можем предсказать точный хэш, но можем проверить формат
        String result = serializer.serialize(obj);

        // When & Then
        assertNotNull(result);
        assertTrue(result.contains("@"), "Результат должен содержать символ '@' как в реализации Object.toString()");
        assertTrue(result.startsWith("java.lang.Object@"), "Должен использоваться префикс класса");
    }

    @Test
    void serialize_whenNullProvided_shouldThrowNullPointerException() {
        // When & Then
        // Вызов toString() на null выбросит NullPointerException
        assertThrows(
                NullPointerException.class,
                () -> serializer.serialize(null),
                "Сериализация null должна выбрасывать NullPointerException"
        );
    }

    @Test
    void serialize_whenEqualObjectsProvided_shouldReturnSameString() {
        // Given
        String obj1 = new String("sameValue");
        String obj2 = new String("sameValue");

        // When
        String str1 = serializer.serialize(obj1);
        String str2 = serializer.serialize(obj2);

        // Then
        assertEquals(str1, str2, "Равные объекты должны давать одинаковую строку");
    }

    @Test
    void serialize_whenDifferentObjectsProvided_shouldReturnDifferentString() {
        // Given
        Integer obj1 = 100;
        Integer obj2 = 200;

        // When
        String str1 = serializer.serialize(obj1);
        String str2 = serializer.serialize(obj2);

        // Then
        assertNotEquals(str1, str2, "Разные объекты должны давать разные строки");
    }
}