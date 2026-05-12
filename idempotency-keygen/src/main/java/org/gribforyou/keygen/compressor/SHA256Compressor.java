package org.gribforyou.keygen.compressor;

import org.gribforyou.keygen.StringCompressor;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * {@link StringCompressor} that uses SHA-256 hashing to produce fixed-length output.
 * <p>
 * This compressor converts any input string to a 64-character hexadecimal string
 * representing the SHA-256 hash of the input. This provides:
 * <ul>
 *   <li>Fixed output length (64 characters)</li>
 *   <li>Extremely low collision probability</li>
 *   <li>Cryptographic security properties</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * ConcatenatingKeyGenerator generator = ConcatenatingKeyGenerator.builder()
 *     .byObjectCompressor(new SHA256Compressor())
 *     .byStringCompressor(new SHA256Compressor())
 *     .build();
 * }</pre>
 * <p>
 * <strong>Note:</strong> This is a lossy compression - the original string cannot be recovered.
 * However, for idempotency key generation, this is typically acceptable since keys are used
 * for lookup, not reconstruction.
 *
 * @see GzipBase64Compressor for lossless compression
 * @see Lz4Base64Compressor for faster lossless compression
 */
public class SHA256Compressor implements StringCompressor {

    private final MessageDigest digest;

    /**
     * Creates a new SHA256Compressor with a SHA-256 MessageDigest instance.
     *
     * @throws RuntimeException if SHA-256 algorithm is not available (should not happen in standard JVM)
     */
    public SHA256Compressor() {
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * Compresses the input string by computing its SHA-256 hash.
     *
     * @param input the string to hash (may be null or empty)
     * @return 64-character hexadecimal string representing the SHA-256 hash, or original input if null/empty
     * @throws RuntimeException if hashing fails
     */
    @Override
    public String compress(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        byte[] hashBytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return HexFormat.of().formatHex(hashBytes);
    }
}
