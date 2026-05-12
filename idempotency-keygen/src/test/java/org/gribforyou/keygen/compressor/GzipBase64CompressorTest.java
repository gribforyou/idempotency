package org.gribforyou.keygen.compressor;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GzipBase64Compressor}.
 */
class GzipBase64CompressorTest {

    private final GzipBase64Compressor compressor = new GzipBase64Compressor();

    @Test
    void compress_whenNullInput_shouldReturnNull() {
        // When
        String result = compressor.compress(null);

        // Then
        assertNull(result, "Для null должен вернуться null");
    }

    @Test
    void compress_whenEmptyInput_shouldReturnEmptyString() {
        // When
        String result = compressor.compress("");

        // Then
        assertEquals("", result, "Для пустой строки должна вернуться пустая строка");
    }

    @Test
    void compress_whenShortString_shouldReturnOriginal() {
        // Given
        String shortInput = "short";

        // When
        String result = compressor.compress(shortInput);

        // Then
        assertEquals(shortInput, result, "Для коротких строк должен возвращаться оригинал (сжатие невыгодно)");
    }

    @Test
    void compress_whenLongRepetitiveString_shouldReturnCompressed() {
        // Given
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("This is a repetitive text block for compression test. ");
        }
        String longInput = sb.toString();

        // When
        String result = compressor.compress(longInput);

        // Then
        assertNotNull(result);
        assertNotEquals(longInput, result, "Длинная строка должна быть сжата");
        assertTrue(result.length() < longInput.length(), "Длина сжатой строки должна быть меньше оригинала");

        assertTrue(result.matches("[A-Za-z0-9_-]+"), "Результат должен быть URL-safe Base64 строкой без паддинга");

        assertDoesNotThrow(() -> Base64.getUrlDecoder().decode(result), "Результат должен быть валидным Base64");
    }

    @Test
    void compress_whenJsonString_shouldReturnCompressed() {
        // Given
        String jsonInput = "{\"name\":\"John\",\"age\":30,\"city\":\"New York\",\"description\":\"Some long text to make compression effective\"}";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append(jsonInput);
        }
        String largeJson = sb.toString();

        // When
        String result = compressor.compress(largeJson);

        // Then
        assertNotNull(result);
        assertTrue(result.length() < largeJson.length(), "JSON должен быть сжат");
        assertTrue(result.matches("[A-Za-z0-9_-]+"), "Результат должен быть валидным Base64");
    }

    @Test
    void compress_whenUtf8String_shouldReturnValidBase64() {
        // Given
        String utf8Input = "Привет мир! Это тест на сжатие UTF-8 строки. 🚀".repeat(50);

        // When
        String result = compressor.compress(utf8Input);

        // Then
        assertNotNull(result);
        if (result.length() < utf8Input.length()) {
            assertTrue(result.matches("[A-Za-z0-9_-]+"), "Сжатый результат должен быть Base64");
            assertDoesNotThrow(() -> Base64.getUrlDecoder().decode(result));
        } else {
            assertEquals(utf8Input, result);
        }
    }

    @Test
    void compress_whenRandomString_mayReturnOriginalOrCompressed() {
        // Given
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append((char) ('A' + (i % 26)));
        }
        String randomInput = sb.toString();

        // When
        String result = compressor.compress(randomInput);

        // Then
        assertNotNull(result);
    }

    @Test
    void compress_verifyBase64Format_shouldNotContainPadding() {
        // Given
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("Data block for testing padding absence. ");
        }
        String input = sb.toString();

        // When
        String result = compressor.compress(input);

        // Then
        if (result.length() < input.length()) {
            assertFalse(result.contains("="), "Base64 не должен содержать символы паддинга '='");
            assertFalse(result.contains("+"), "Base64 не должен содержать '+', должен использовать '-'");
            assertFalse(result.contains("/"), "Base64 не должен содержать '/', должен использовать '_'");
        }
    }
}