package org.gribforyou.keygen.compressor;

import org.gribforyou.keygen.StringCompressor;

/**
 * {@link StringCompressor} that reduces strings to their {@link String#hashCode()} value.
 * <p>
 * This compressor converts any input string to its integer hash code representation,
 * resulting in very short keys (typically 10 characters or less).
 * <p>
 * <strong>Warning:</strong> This compressor has high collision risk and should only be used for:
 * <ul>
 *   <li>Non-critical operations where occasional collisions are acceptable</li>
 *   <li>Low-volume systems with limited unique inputs</li>
 *   <li>Testing and development environments</li>
 * </ul>
 * <p>
 * For production use, consider {@link GzipBase64Compressor}, {@link Lz4Base64Compressor},
 * or {@link SHA256Compressor} for better collision resistance.
 *
 * @see GzipBase64Compressor
 * @see Lz4Base64Compressor
 * @see SHA256Compressor
 */
public class HashcodeCompressor implements StringCompressor {

    /**
     * Compresses the input string to its hash code string representation.
     *
     * @param input the string to compress
     * @return string representation of {@code input.hashCode()}
     */
    @Override
    public String compress(String input) {
        return String.valueOf(input.hashCode());
    }
}
