package org.gribforyou.keygen.serializer;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link JsonObjectSerializer}.
 */
class JsonObjectSerializerTest {

    private final JsonObjectSerializer serializer = new JsonObjectSerializer();

    @Test
    void serialize_whenPojoProvided_shouldReturnValidJson() {
        // Given
        TestDto dto = new TestDto("Alice", 30);
        String expectedJson = "{\"name\":\"Alice\",\"age\":30}";

        // When
        String actualJson = serializer.serialize(dto);

        // Then
        assertEquals(expectedJson, actualJson, "JSON должен соответствовать полям объекта");
    }

    @Test
    void serialize_whenMapProvided_shouldReturnValidJson() {
        // Given
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key1", "value1");
        map.put("key2", 123);
        String expectedJson = "{\"key1\":\"value1\",\"key2\":123}";

        // When
        String actualJson = serializer.serialize(map);

        // Then
        assertEquals(expectedJson, actualJson);
    }

    @Test
    void serialize_whenListProvided_shouldReturnValidJsonArray() {
        // Given
        List<String> list = Arrays.asList("a", "b", "c");
        String expectedJson = "[\"a\",\"b\",\"c\"]";

        // When
        String actualJson = serializer.serialize(list);

        // Then
        assertEquals(expectedJson, actualJson);
    }

    @Test
    void serialize_whenNullProvided_shouldReturnJsonNull() {
        // When
        String actualJson = serializer.serialize(null);

        // Then
        assertEquals("null", actualJson, "Сериализация null должна возвращать строку \"null\"");
    }

    @Test
    void serialize_whenNestedObjectProvided_shouldReturnValidNestedJson() {
        // Given
        TestDto innerDto = new TestDto("Bob", 25);
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("user", innerDto);
        wrapper.put("active", true);

        String expectedJson = "{\"user\":{\"name\":\"Bob\",\"age\":25},\"active\":true}";

        // When
        String actualJson = serializer.serialize(wrapper);

        // Then
        assertEquals(expectedJson, actualJson, "Вложенные объекты должны корректно сериализоваться");
    }

    @Test
    void serialize_whenObjectWithSpecialCharactersProvided_shouldEscapeCharacters() {
        // Given
        String input = "Line 1\nLine 2\tTab\"Quote";
        String expectedJson = "\"Line 1\\nLine 2\\tTab\\\"Quote\"";

        // When
        String actualJson = serializer.serialize(input);

        // Then
        assertEquals(expectedJson, actualJson, "Спецсимволы должны быть экранированы");
    }

    @Test
    void serialize_whenSerializationFails_shouldThrowRuntimeException() {
        // Given
        CyclicObject obj = new CyclicObject();
        obj.self = obj;

        // When & Then
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> serializer.serialize(obj),
                "Сериализация циклической ссылки должна выбрасывать RuntimeException"
        );
        assertTrue(exception.getCause() instanceof com.fasterxml.jackson.core.JsonProcessingException);
    }

    static class TestDto {
        private String name;
        private int age;

        TestDto(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }
    }

    static class ClassWithTransient {
        private String visibleField;
        private transient String hiddenField;

        ClassWithTransient(String visible, String hidden) {
            this.visibleField = visible;
            this.hiddenField = hidden;
        }

        public String getVisibleField() {
            return visibleField;
        }

        public String getHiddenField() {
            return hiddenField;
        }
    }

    static class CyclicObject {
        CyclicObject self;
    }
}