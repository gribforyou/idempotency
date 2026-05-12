package org.gribforyou.keygen.compressor;

import org.gribforyou.keygen.StringCompressor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

/**
 * {@link StringCompressor} that uses GZIP compression followed by Base64 URL-safe encoding.
 * <p>
 * This compressor provides lossless compression with good compression ratios for longer strings.
 * The compression process:
 * <ol>
 *   <li>Compress input using GZIP</li>
 *   <li>Encode compressed bytes with Base64 URL-safe encoder (no padding)</li>
 *   <li>Return original if compressed size is not smaller</li>
 * </ol>
 * <p>
 * Example usage:
 * <pre>{@code
 * ConcatenatingKeyGenerator generator = ConcatenatingKeyGenerator.builder()
 *     .byObjectCompressor(new GzipBase64Compressor())
 *     .build();
 * }</pre>
 * <p>
 * <strong>Note:</strong> GZIP compression has overhead and may increase size for very short strings.
 * This implementation automatically falls back to the original string if compression is not beneficial.
 *
 * @see Lz4Base64Compressor for faster compression with similar ratios
 * @see SHA256Compressor for fixed-length hash-based compression
 */
public class GzipBase64Compressor implements StringCompressor {

    /**
     * Compresses the input string using GZIP and encodes it with Base64 URL-safe encoding.
     *
     * @param input the string to compress (may be null or empty)
     * @return compressed Base64-encoded string, or original input if compression is not beneficial
     * @throws RuntimeException if compression fails
     */
    @Override
    public String compress(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        try {
            byte[] inputBytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] compressedBytes = gzip(inputBytes);

            if (compressedBytes.length >= inputBytes.length) {
                return input;
            }

            return Base64.getUrlEncoder().withoutPadding().encodeToString(compressedBytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to compress string", e);
        }
    }

    private byte[] gzip(byte[] data) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data);
            return baos.toByteArray();
        }
    }
}