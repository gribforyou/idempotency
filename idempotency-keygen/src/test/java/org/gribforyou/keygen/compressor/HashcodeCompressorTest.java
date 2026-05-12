package org.gribforyou.keygen.compressor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HashcodeCompressor}.
 */
class HashcodeCompressorTest {

    private final HashcodeCompressor compressor = new HashcodeCompressor();

    @Test
    void compress_whenNullInput_shouldThrowNullPointerException() {
        // When & Then
        assertThrows(
                NullPointerException.class,
                () -> compressor.compress(null),
                "Сжатие null должно выбрасывать NullPointerException"
        );
    }

    @Test
    void compress_whenEmptyString_shouldReturnZeroHashCode() {
        // Given
        String input = "";
        String expected = "0";

        // When
        String result = compressor.compress(input);

        // Then
        assertEquals(expected, result, "Хэш-код пустой строки должен быть \"0\"");
    }

    @Test
    void compress_whenSimpleString_shouldReturnCorrectHashCode() {
        // Given
        String input = "test";
        String expected = String.valueOf(input.hashCode());

        // When
        String result = compressor.compress(input);

        // Then
        assertEquals(expected, result, "Результат должен совпадать со строковым представлением hashCode");
    }

    @Test
    void compress_whenLongString_shouldReturnShortResult() {
        // Given
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("long string content ");
        }
        String longInput = sb.toString();

        // When
        String result = compressor.compress(longInput);

        // Then
        assertNotNull(result);
        assertTrue(result.length() <= 11, "Результат должен быть коротким (не более 11 символов для int)");
        assertTrue(result.matches("-?\\d+"), "Результат должен содержать только цифры и возможный знак минуса");
    }

    @Test
    void compress_whenEqualStrings_shouldReturnSameHash() {
        // Given
        String str1 = new String("same content");
        String str2 = new String("same content");

        // When
        String hash1 = compressor.compress(str1);
        String hash2 = compressor.compress(str2);

        // Then
        assertEquals(hash1, hash2, "Равные строки должны иметь одинаковый хэш-код");
    }

    @Test
    void compress_whenDifferentStrings_shouldLikelyReturnDifferentHash() {
        // Given
        String str1 = "content one";
        String str2 = "content two";

        // When
        String hash1 = compressor.compress(str1);
        String hash2 = compressor.compress(str2);

        // Then
        assertNotEquals(hash1, hash2, "Разные строки должны иметь разный хэш-код (за исключением коллизий)");
    }

    @Test
    void compress_whenStringWithNegativeHashCode_shouldReturnNegativeString() {
        // Given
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append("z");
        }
        String input = sb.toString();
        int hashCode = input.hashCode();
        String expected = String.valueOf(hashCode);

        // When
        String result = compressor.compress(input);

        // Then
        assertEquals(expected, result);
        if (hashCode < 0) {
            assertTrue(result.startsWith("-"), "Отрицательный хэш-код должен начинаться с минуса");
        }
    }

    @Test
    void compress_verifyCollisionRisk_shouldDemonstrateSameHashForDifferentStrings() {
        // Given
        String str1 = "Aa";
        String str2 = "BB";

        assertEquals(str1.hashCode(), str2.hashCode(), "Предпосылка: хэш-коды должны совпадать");

        // When
        String hash1 = compressor.compress(str1);
        String hash2 = compressor.compress(str2);

        // Then
        assertEquals(hash1, hash2, "Компрессор должен вернуть одинаковый результат для строк с коллизией hashCode");
        assertNotEquals(str1, str2, "Строки при этом разные");
    }

    @Test
    void compress_whenUtf8String_shouldHandleCorrectly() {
        // Given
        String input = "Привет мир! 🚀";
        String expected = String.valueOf(input.hashCode());

        // When
        String result = compressor.compress(input);

        // Then
        assertEquals(expected, result, "UTF-8 символы должны корректно обрабатываться hashCode()");
    }
}