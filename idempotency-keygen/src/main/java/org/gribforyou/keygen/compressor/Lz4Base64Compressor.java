package org.gribforyou.keygen.compressor;

import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import org.gribforyou.keygen.StringCompressor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * {@link StringCompressor} that uses LZ4 compression followed by Base64 URL-safe encoding.
 * <p>
 * This compressor provides fast lossless compression with good compression ratios.
 * LZ4 is generally faster than GZIP but may have slightly different compression ratios.
 * The compression process:
 * <ol>
 *   <li>Compress input using LZ4 block compression</li>
 *   <li>Encode compressed bytes with Base64 URL-safe encoder (no padding)</li>
 *   <li>Return original if compressed size is not smaller</li>
 * </ol>
 * <p>
 * Example usage:
 * <pre>{@code
 * ConcatenatingKeyGenerator generator = ConcatenatingKeyGenerator.builder()
 *     .byObjectCompressor(new Lz4Base64Compressor())
 *     .build();
 * }</pre>
 * <p>
 * <strong>Note:</strong> Requires the LZ4 Java library (net.jpountz.lz4) as a dependency.
 *
 * @see GzipBase64Compressor for standard compression
 * @see SHA256Compressor for fixed-length hash-based compression
 */
public class Lz4Base64Compressor implements StringCompressor {

    private final LZ4Factory factory = LZ4Factory.fastestInstance();
    private final LZ4Compressor compressor = factory.fastCompressor();

    /**
     * Compresses the input string using LZ4 and encodes it with Base64 URL-safe encoding.
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
            byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
            byte[] compressed = compressLZ4(inputBytes);

            if (compressed.length >= inputBytes.length) {
                return input;
            }

            return Base64.getUrlEncoder().withoutPadding().encodeToString(compressed);
        } catch (IOException e) {
            throw new RuntimeException("Failed to compress with LZ4", e);
        }
    }

    private byte[] compressLZ4(byte[] data) throws IOException {
        int blockSize = 65536;

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             LZ4BlockOutputStream lz4Out = new LZ4BlockOutputStream(out, blockSize, compressor)) {

            lz4Out.write(data);
            lz4Out.finish();

            return out.toByteArray();
        }
    }
}