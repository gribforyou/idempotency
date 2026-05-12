package org.gribforyou.keygen;

import org.gribforyou.IdempotencyKey;
import org.gribforyou.keygen.compressor.SHA256Compressor;
import org.gribforyou.keygen.serializer.JsonObjectSerializer;
import org.gribforyou.keygen.serializer.ToStringObjectSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConcatenatingKeyGenerator}.
 */
class ConcatenatingKeyGeneratorTest {

    @Test
    void generate_whenSimpleParams_shouldReturnConcatenatedKey() {
        // Given
        ConcatenatingKeyGenerator generator = ConcatenatingKeyGenerator.builder()
                .defaultSerializer(new ToStringObjectSerializer())
                .byObjectCompressor(s -> s) // Без сжатия
                .byStringCompressor(s -> s) // Без сжатия
                .delimiter("::")
                .build();

        String operationType = "createUser";
        Object[] params = {"Alice", 25};

        // When
        IdempotencyKey key = generator.generate(operationType, params);

        // Then
        assertNotNull(key);
        // Ожидаемый формат: operationType::serializedParam1::serializedParam2
        assertEquals("createUser::Alice::25", key.key());
    }

    @Test
    void generate_whenEmptyParams_shouldReturnOperationTypeOnly() {
        // Given
        ConcatenatingKeyGenerator generator = ConcatenatingKeyGenerator.builder()
                .defaultSerializer(new ToStringObjectSerializer())
                .build();

        String operationType = "getAllUsers";
        Object[] params = {};

        // When
        IdempotencyKey key = generator.generate(operationType, params);

        // Then
        assertEquals("getAllUsers", key.key(), "При пустых параметрах ключ должен состоять только из типа операции");
    }

    @Test
    void generate_whenCustomSerializerRegistered_shouldUseIt() {
        // Given
        SerializerRegistry registry = new SerializerRegistry();
        // Регистрируем сериализатор, который всегда возвращает "CUSTOM"
        registry.registerSerializer(String.class, obj -> "CUSTOM");

        ConcatenatingKeyGenerator generator = ConcatenatingKeyGenerator.builder()
                .registry(registry)
                .defaultSerializer(new ToStringObjectSerializer())
                .build();

        String operationType = "test";
        Object[] params = {"Hello", 123}; // "Hello" должно сериализоваться как "CUSTOM"

        // When
        IdempotencyKey key = generator.generate(operationType, params);

        // Then
        // Ожидаем: test::CUSTOM::123
        assertTrue(key.key().contains("CUSTOM"), "Должен использоваться кастомный сериализатор");
        assertTrue(key.key().contains("123"), "Для незарегистрированного типа должен использоваться defaultSerializer");
    }

    @Test
    void generate_whenJsonObjectSerializerUsed_shouldSerializeToJson() {
        // Given
        ConcatenatingKeyGenerator generator = ConcatenatingKeyGenerator.builder()
                .defaultSerializer(new JsonObjectSerializer())
                .byObjectCompressor(s -> s)
                .byStringCompressor(s -> s)
                .build();

        TestDto dto = new TestDto("John", 30);
        String operationType = "createDto";
        Object[] params = {dto};

        // When
        IdempotencyKey key = generator.generate(operationType, params);

        // Then
        assertNotNull(key);
        assertTrue(key.key().contains("\"name\":\"John\""), "Ключ должен содержать JSON представление объекта");
        assertTrue(key.key().contains("\"age\":30"), "Ключ должен содержать JSON представление объекта");
    }

    @Test
    void generate_whenCompressorsUsed_shouldCompressResult() {
        // Given
        // Используем SHA256 для финального сжатия, чтобы получить фиксированную длину
        ConcatenatingKeyGenerator generator = ConcatenatingKeyGenerator.builder()
                .defaultSerializer(new ToStringObjectSerializer())
                .byObjectCompressor(s -> s) // Параметры не сжимаем
                .byStringCompressor(new SHA256Compressor()) // Итоговый ключ хэшируем
                .build();

        String operationType = "test";
        Object[] params = {"param1", "param2"};

        // When
        IdempotencyKey key = generator.generate(operationType, params);

        // Then
        assertNotNull(key);
        // SHA-256 всегда возвращает 64 hex-символа
        assertEquals(64, key.key().length(), "Длина ключа должна соответствовать длине хэша SHA-256");
        assertTrue(key.key().matches("[a-f0-9]+"), "Ключ должен содержать только hex-символы");
    }

    @Test
    void generate_whenSameInputs_shouldReturnIdenticalKeys() {
        // Given
        ConcatenatingKeyGenerator generator = ConcatenatingKeyGenerator.builder()
                .defaultSerializer(new JsonObjectSerializer())
                .byStringCompressor(new SHA256Compressor())
                .build();

        TestDto dto = new TestDto("Alice", 25);
        String operationType = "createUser";
        Object[] params = {dto};

        // When
        IdempotencyKey key1 = generator.generate(operationType, params);
        IdempotencyKey key2 = generator.generate(operationType, params);

        // Then
        assertEquals(key1, key2, "Генератор должен быть детерминированным");
    }

    @Test
    void generate_whenDifferentInputs_shouldReturnDifferentKeys() {
        // Given
        ConcatenatingKeyGenerator generator = ConcatenatingKeyGenerator.builder()
                .defaultSerializer(new ToStringObjectSerializer())
                .byStringCompressor(new SHA256Compressor())
                .build();

        String operationType = "test";
        Object[] params1 = {"A"};
        Object[] params2 = {"B"};

        // When
        IdempotencyKey key1 = generator.generate(operationType, params1);
        IdempotencyKey key2 = generator.generate(operationType, params2);

        // Then
        assertNotEquals(key1, key2, "Разные параметры должны давать разные ключи");
    }

    @Test
    void builder_whenNullRegistry_shouldThrowException() {
        // When & Then
        assertThrows(
                NullPointerException.class,
                () -> ConcatenatingKeyGenerator.builder().registry(null),
                "Builder должен выбрасывать NullPointerException при установке null registry"
        );
    }

    @Test
    void builder_whenNullDefaultSerializer_shouldThrowException() {
        // When & Then
        assertThrows(
                NullPointerException.class,
                () -> ConcatenatingKeyGenerator.builder().defaultSerializer(null),
                "Builder должен выбрасывать NullPointerException при установке null defaultSerializer"
        );
    }

    @Test
    void builder_whenNullDelimiter_shouldThrowException() {
        // When & Then
        assertThrows(
                NullPointerException.class,
                () -> ConcatenatingKeyGenerator.builder().delimiter(null),
                "Builder должен выбрасывать NullPointerException при установке null delimiter"
        );
    }

    @Test
    void builder_whenNullByObjectCompressor_shouldThrowException() {
        // When & Then
        assertThrows(
                NullPointerException.class,
                () -> ConcatenatingKeyGenerator.builder().byObjectCompressor(null),
                "Builder должен выбрасывать NullPointerException при установке null byObjectCompressor"
        );
    }

    @Test
    void builder_whenNullByStringCompressor_shouldThrowException() {
        // When & Then
        assertThrows(
                NullPointerException.class,
                () -> ConcatenatingKeyGenerator.builder().byStringCompressor(null),
                "Builder должен выбрасывать NullPointerException при установке null byStringCompressor"
        );
    }

    @Test
    void generate_whenNullParamsArray_shouldThrowException() {
        // Given
        ConcatenatingKeyGenerator generator = ConcatenatingKeyGenerator.builder().build();

        // When & Then
        // Arrays.stream(null) выбросит NullPointerException
        assertThrows(
                NullPointerException.class,
                () -> generator.generate("test", null),
                "Генерация с null массивом параметров должна выбрасывать исключение"
        );
    }

    // Вспомогательный класс для тестов
    static class TestDto {
        private final String name;
        private final int age;

        TestDto(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public int getAge() { return age; }
    }
}