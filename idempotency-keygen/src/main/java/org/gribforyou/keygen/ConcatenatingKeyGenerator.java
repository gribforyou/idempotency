package org.gribforyou.keygen;

import org.gribforyou.IdempotencyKey;
import org.gribforyou.IdempotencyKeyGenerator;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of {@link org.gribforyou.IdempotencyKeyGenerator} that generates keys by
 * concatenating serialized and compressed representations of operation parameters.
 * <p>
 * This generator provides full control over the key generation process through:
 * <ul>
 *   <li>Custom {@link ObjectSerializer} instances for different parameter types</li>
 *   <li>Custom {@link StringCompressor} implementations to control key length</li>
 *   <li>Configurable delimiter for separating parameters</li>
 * </ul>
 * <p>
 * Key generation process:
 * <ol>
 *   <li>Each parameter is serialized using a type-specific or default serializer</li>
 *   <li>Serialized values are compressed using the object compressor</li>
 *   <li>Compressed values are joined with the delimiter</li>
 *   <li>The operation type is prepended to the parameters</li>
 *   <li>The full key is compressed using the string compressor</li>
 * </ol>
 * <p>
 * Example usage:
 * <pre>{@code
 * ConcatenatingKeyGenerator generator = ConcatenatingKeyGenerator.builder()
 *     .defaultSerializer(new JsonObjectSerializer())
 *     .byObjectCompressor(new SHA256Compressor())
 *     .delimiter("::")
 *     .build();
 * }</pre>
 * <p>
 * This implementation is recommended for production use as it provides deterministic
 * key generation with configurable collision resistance.
 *
 * @see HashCodeKeyGenerator for a simpler but less reliable alternative
 */
public class ConcatenatingKeyGenerator implements org.gribforyou.IdempotencyKeyGenerator {

    private final SerializerRegistry registry;
    private final ObjectSerializer defaultSerializer;
    private final String delimiter;
    private final StringCompressor byObjectCompressor;
    private final StringCompressor byStringCompressor;

    /**
     * Creates a new ConcatenatingKeyGenerator with the specified configuration.
     *
     * @param registry registry for type-specific serializers
     * @param defaultSerializer default serializer for unregistered types
     * @param delimiter delimiter for joining serialized parameters
     * @param byObjectCompressor compressor applied to each serialized parameter
     * @param byStringCompressor compressor applied to the final concatenated key
     */
    private ConcatenatingKeyGenerator(
            SerializerRegistry registry,
            ObjectSerializer defaultSerializer,
            String delimiter,
            StringCompressor byObjectCompressor,
            StringCompressor byStringCompressor
    ) {
        this.registry = registry;
        this.defaultSerializer = defaultSerializer;
        this.delimiter = delimiter;
        this.byObjectCompressor = byObjectCompressor;
        this.byStringCompressor = byStringCompressor;
    }

    /**
     * Generates an idempotency key by concatenating serialized and compressed parameters.
     * <p>
     * The key format is: {@code {operationType}[delimiter]{compressedParam1}[delimiter]{compressedParam2}...}
     * The entire key is then compressed using the string compressor.
     *
     * @param operationType the type of operation (must not be null)
     * @param params operation parameters (must not be null)
     * @return an idempotency key uniquely identifying this operation with these parameters
     * @throws IllegalArgumentException if operationType or params is null
     * @throws RuntimeException if serialization or compression fails
     */
    @Override
    public IdempotencyKey generate(String operationType, Object[] params) {
        Objects.requireNonNull(operationType, "operationType must not be null");
        Objects.requireNonNull(params, "params must not be null");

        String paramsPart = Arrays.stream(params)
                .map(param -> {
                    String serialized = Optional.ofNullable(registry.getSerializer(param.getClass()))
                            .orElse(defaultSerializer)
                            .serialize(param);

                    return byObjectCompressor.compress(serialized);
                })
                .collect(Collectors.joining(delimiter));

        String fullKey = paramsPart.isEmpty()
                ? operationType
                : operationType + delimiter + paramsPart;

        return new IdempotencyKey(byStringCompressor.compress(fullKey));
    }

    /**
     * Creates a new builder for configuring and constructing a ConcatenatingKeyGenerator.
     *
     * @return a new Builder instance with default configuration
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private SerializerRegistry registry = new SerializerRegistry();
        private ObjectSerializer defaultSerializer = Object::toString;
        private String delimiter = "::";
        private StringCompressor byObjectCompressor = s -> s;
        private StringCompressor byStringCompressor = s -> s;

        /**
         * Sets the serializer registry for type-specific parameter serialization.
         *
         * @param registry the serializer registry (must not be null)
         * @return this builder
         * @throws IllegalArgumentException if registry is null
         */
        public Builder registry(SerializerRegistry registry) {
            Objects.requireNonNull(registry, "registry must not be null");
            this.registry = registry;
            return this;
        }

        /**
         * Sets the default serializer for parameter types without a specific registration.
         * <p>
         * Default is {@code Object::toString}.
         *
         * @param defaultSerializer the default serializer (must not be null)
         * @return this builder
         * @throws IllegalArgumentException if defaultSerializer is null
         */
        public Builder defaultSerializer(ObjectSerializer defaultSerializer) {
            Objects.requireNonNull(defaultSerializer, "defaultSerializer must not be null");
            this.defaultSerializer = defaultSerializer;
            return this;
        }

        /**
         * Sets the delimiter used to join serialized parameters.
         * <p>
         * Default is "{@code ::}".
         *
         * @param delimiter the delimiter (must not be null)
         * @return this builder
         * @throws IllegalArgumentException if delimiter is null
         */
        public Builder delimiter(String delimiter) {
            Objects.requireNonNull(delimiter, "delimiter must not be null");
            this.delimiter = delimiter;
            return this;
        }

        /**
         * Sets the compressor applied to each serialized parameter before concatenation.
         * <p>
         * Default is identity function (no compression).
         *
         * @param byObjectCompressor the compressor (must not be null)
         * @return this builder
         * @throws IllegalArgumentException if byObjectCompressor is null
         */
        public Builder byObjectCompressor(StringCompressor byObjectCompressor) {
            Objects.requireNonNull(byObjectCompressor, "byObjectCompressor must not be null");
            this.byObjectCompressor = byObjectCompressor;
            return this;
        }

        /**
         * Sets the compressor applied to the final concatenated key.
         * <p>
         * Default is identity function (no compression).
         *
         * @param byStringCompressor the compressor (must not be null)
         * @return this builder
         * @throws IllegalArgumentException if byStringCompressor is null
         */
        public Builder byStringCompressor(StringCompressor byStringCompressor) {
            Objects.requireNonNull(byStringCompressor, "byStringCompressor must not be null");
            this.byStringCompressor = byStringCompressor;
            return this;
        }

        /**
         * Builds and returns a new ConcatenatingKeyGenerator with the configured settings.
         *
         * @return a new ConcatenatingKeyGenerator instance
         */
        public ConcatenatingKeyGenerator build() {
            return new ConcatenatingKeyGenerator(
                    registry,
                    defaultSerializer,
                    delimiter,
                    byObjectCompressor,
                    byStringCompressor
            );
        }
    }
}