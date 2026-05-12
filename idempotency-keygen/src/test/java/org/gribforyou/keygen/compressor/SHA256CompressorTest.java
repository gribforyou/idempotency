package org.gribforyou.keygen.compressor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SHA256Compressor}.
 */
class SHA256CompressorTest {

    private final SHA256Compressor compressor = new SHA256Compressor();

    @Test
    void compress_whenSimpleString_shouldReturnCorrectHash() {
        // Given
        String input = "hello";
        // SHA-256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        String expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

        // When
        String result = compressor.compress(input);

        // Then
        assertEquals(expected, result);
        assertEquals(64, result.length());
    }

    @Test
    void compress_whenLongString_shouldReturnFixedLengthHash() {
        // Given
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("This is a long string for testing SHA-256 fixed length output. ");
        }
        String longInput = sb.toString();

        // When
        String result = compressor.compress(longInput);

        // Then
        assertNotNull(result);
        assertEquals(64, result.length(), "Длина хэша должна быть фиксированной (64 символа) независимо от размера входа");
        assertTrue(result.matches("[a-f0-9]+"), "Результат должен содержать только hex-символы");
    }

    @Test
    void compress_whenEqualStrings_shouldReturnSameHash() {
        // Given
        String str1 = "same content";
        String str2 = "same content";

        // When
        String hash1 = compressor.compress(str1);
        String hash2 = compressor.compress(str2);

        // Then
        assertEquals(hash1, hash2, "Одинаковые строки должны давать одинаковый хэш");
    }

    @Test
    void compress_whenDifferentStrings_shouldReturnDifferentHash() {
        // Given
        String str1 = "content A";
        String str2 = "content B";

        // When
        String hash1 = compressor.compress(str1);
        String hash2 = compressor.compress(str2);

        // Then
        assertNotEquals(hash1, hash2, "Разные строки должны давать разные хэши");
    }

    @Test
    void compress_whenUtf8String_shouldHandleCorrectly() {
        // Given
        String input = "Привет мир! 🚀";

        // When
        String result = compressor.compress(input);

        // Then
        assertNotNull(result);
        assertEquals(64, result.length(), "Длина хэша должна быть фиксированной");
        // Проверка детерминированности для UTF-8
        String result2 = compressor.compress(input);
        assertEquals(result, result2);
    }

    @Test
    void compress_verifyAvalancheEffect_shouldChangeCompletelyOnSmallChange() {
        // Given
        String str1 = "test";
        String str2 = "test."; // Добавлен один символ

        // When
        String hash1 = compressor.compress(str1);
        String hash2 = compressor.compress(str2);

        // Then
        assertNotEquals(hash1, hash2, "Хэши должны отличаться");
        assertFalse(hash1.startsWith(hash2.substring(0, 10)), "Хэши должны сильно отличаться даже при малом изменении входа");
    }

    @Test
    void compress_whenSingleCharacterDifference_shouldProduceDifferentHash() {
        // Given
        String str1 = "The quick brown fox jumps over the lazy dog";
        String str2 = "The quick brown fox jumps over the lazy dog.";

        // When
        String hash1 = compressor.compress(str1);
        String hash2 = compressor.compress(str2);

        // Then
        assertEquals("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592", hash1);
        assertNotEquals(hash1, hash2);
    }
}