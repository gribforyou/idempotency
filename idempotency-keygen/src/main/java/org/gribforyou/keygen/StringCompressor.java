package org.gribforyou.keygen;

/**
 * Strategy interface for compressing strings during idempotency key generation.
 * <p>
 * Implementations define how strings are compressed or transformed to reduce the length
 * of the generated idempotency key while maintaining uniqueness. Compression is applied
 * after serialization to keep keys within reasonable size limits.
 * <p>
 * Example implementations:
 * <ul>
 *   <li>{@link org.gribforyou.keygen.compressor.HashcodeCompressor} - reduces to hash code</li>
 *   <li>{@link org.gribforyou.keygen.compressor.GzipBase64Compressor} - GZIP compression with Base64 encoding</li>
 *   <li>{@link org.gribforyou.keygen.compressor.Lz4Base64Compressor} - LZ4 compression with Base64 encoding</li>
 *   <li>{@link org.gribforyou.keygen.compressor.SHA256Compressor} - SHA-256 hash (fixed length)</li>
 * </ul>
 * <p>
 * <strong>Note:</strong> Some implementations are lossy (e.g., hash-based), which may introduce
 * collision risk. Choose based on your collision tolerance and key length requirements.
 *
 * @see ConcatenatingKeyGenerator
 */
public interface StringCompressor {

    /**
     * Compresses the input string to a shorter representation.
     * <p>
     * Implementations may return the original string if compression would not reduce size,
     * or if the input is null/empty.
     *
     * @param input the string to compress (may be null or empty)
     * @return compressed string, or original input if compression is not beneficial
     * @throws RuntimeException if compression fails
     */
    String compress(String input);
}
