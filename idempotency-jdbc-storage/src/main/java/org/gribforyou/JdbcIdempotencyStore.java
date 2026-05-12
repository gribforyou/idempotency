package org.gribforyou;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.util.*;

/**
 * JDBC-based implementation of {@link IdempotencyStore} for storing and managing idempotency records in a relational database.
 * <p>
 * This class provides thread-safe operations for:
 * - Locking and retrieving active idempotency records with timeout handling
 * - Saving successful results or errors with configurable TTL
 * - Querying record state without locking
 * <p>
 * The underlying table must have the following columns:
 * <ul>
 *   <li>{@code record_key} (VARCHAR) - unique identifier for the idempotency key</li>
 *   <li>{@code state} (VARCHAR) - current state: LOCKED, EXECUTED, or FAILED</li>
 *   <li>{@code result_data} (TEXT) - JSON serialized result object (nullable)</li>
 *   <li>{@code error_data} (TEXT) - JSON serialized error object (nullable)</li>
 *   <li>{@code created_at} (BIGINT) - timestamp when record was created (milliseconds since epoch)</li>
 *   <li>{@code ttl} (BIGINT) - time-to-live in milliseconds</li>
 *   <li>{@code locked_until} (BIGINT) - timestamp when lock expires (nullable)</li>
 * </ul>
 * <p>
 * Usage example:
 * <pre>{@code
 * JdbcIdempotencyStore<MyResponse> store = JdbcIdempotencyStore.<MyResponse>builder()
 *     .dataSource(dataSource)
 *     .resultType(MyResponse.class)
 *     .build();
 * }</pre>
 *
 * @param <T> the type of the result object to be serialized/deserialized
 */
public class JdbcIdempotencyStore<T> implements IdempotencyStore<T> {

    private static final Set<String> REQUIRED_COLUMNS = new HashSet<>(Arrays.asList(
            "record_key", "state", "result_data", "error_data", "created_at", "ttl", "locked_until"
    ));

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final String tableName;
    private final Class<T> resultType;

    /**
     * Private constructor. Use {@link Builder} to create instances.
     *
     * @param dataSource   the JDBC data source for database connections
     * @param objectMapper the ObjectMapper for JSON serialization/deserialization
     * @param tableName    the name of the idempotency table in the database
     * @param resultType   the Java class type for deserializing result objects
     * @throws IllegalStateException if the table schema does not match requirements
     */
    private JdbcIdempotencyStore(DataSource dataSource, ObjectMapper objectMapper, String tableName, Class<T> resultType) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.tableName = tableName;
        this.resultType = resultType;
        validateSchema();
    }

    /**
     * Validates that the configured table contains all required columns for idempotency storage.
     * <p>
     * Checks for the presence of: record_key, state, result_data, error_data, created_at, ttl, locked_until.
     * Throws an exception if the table does not exist or is missing any required columns.
     *
     * @throws IllegalStateException if schema validation fails
     */
    private void validateSchema() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
                Set<String> foundColumns = new HashSet<>();
                while (rs.next()) {
                    foundColumns.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
                if (foundColumns.isEmpty()) {
                    throw new IllegalStateException("Table '" + tableName + "' does not exist.");
                }
                List<String> missing = new ArrayList<>();
                for (String req : REQUIRED_COLUMNS) {
                    if (!foundColumns.contains(req.toLowerCase())) missing.add(req);
                }
                if (!missing.isEmpty()) {
                    throw new IllegalStateException("Table '" + tableName + "' missing columns: " + missing);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to validate schema", e);
        }
    }

    /**
     * Attempts to retrieve an active idempotency record or acquire a lock on it.
     * <p>
     * If the record does not exist, creates a new LOCKED record. If the record exists but is expired or
     * in an invalid state (e.g., stale LOCKED, expired EXECUTED, or FAILED), resets it to LOCKED state.
     * Otherwise, returns the existing record.
     * <p>
     * Uses database-level row locking ({@code FOR UPDATE}) to ensure thread safety.
     * <p>
     * The lock timeout defines how long the caller intends to hold the lock. If the lock expires
     * before the operation completes, another caller may acquire it.
     *
     * @param key         the idempotency key to look up or lock
     * @param lockTimeout the maximum duration to hold the lock before it expires
     * @return an Optional containing the record if found and valid, or empty if a new lock was acquired
     * @throws RuntimeException if a database error occurs
     */
    @Override
    public Optional<IdempotencyRecord<T>> getActiveRecordOrLock(IdempotencyKey key, Duration lockTimeout) {
        long now = System.currentTimeMillis();
        long lockUntil = now + lockTimeout.toMillis();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                String selectSql = "SELECT record_key, state, result_data, error_data, created_at, ttl, locked_until " +
                        "FROM " + tableName + " WHERE record_key = ? FOR UPDATE";

                T existingResult = null;
                Throwable existingError = null;
                RecordState existingState = null;
                long existingCreatedAt = 0;
                long existingTtl = 0;
                Long existingLockedUntil = null;
                boolean recordFound = false;

                try (PreparedStatement ps = connection.prepareStatement(selectSql)) {
                    ps.setString(1, key.key());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            recordFound = true;
                            String stateStr = rs.getString("state");
                            existingState = RecordState.valueOf(stateStr);
                            existingCreatedAt = rs.getLong("created_at");
                            existingTtl = rs.getLong("ttl");
                            existingLockedUntil = rs.getObject("locked_until", Long.class);

                            String resultData = rs.getString("result_data");
                            if (resultData != null && !resultData.isEmpty()) {
                                existingResult = objectMapper.readValue(resultData, resultType);
                            }

                            String errorData = rs.getString("error_data");
                            if (errorData != null && !errorData.isEmpty()) {
                                existingError = new RuntimeException("Restored error: " + errorData);
                            }
                        }
                    }
                }

                if (!recordFound) {
                    insertLockedRecord(connection, key, lockUntil);
                    return Optional.empty();
                }

                boolean shouldReset = false;

                if (existingState == RecordState.FAILED) {
                    shouldReset = true;
                } else if (existingState == RecordState.EXECUTED) {
                    if (now - existingCreatedAt > existingTtl) {
                        shouldReset = true;
                    }
                } else if (existingState == RecordState.LOCKED) {
                    if (existingLockedUntil != null && now > existingLockedUntil) {
                        shouldReset = true;
                    }
                }

                if (shouldReset) {
                    updateToLocked(connection, key, lockUntil, now);
                    return Optional.empty();
                }

                return Optional.of(new IdempotencyRecord<>(
                        key, existingResult, existingError, existingState,
                        existingCreatedAt, existingTtl, existingLockedUntil
                ));

            } catch (SQLException e) {
                connection.rollback();
                throw new RuntimeException("Database error during getActiveRecordOrLock", e);
            } catch (JsonProcessingException e) {
                connection.rollback();
                throw new RuntimeException("Failed to deserialize data from database", e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get connection", e);
        }
    }

    /**
     * Inserts a new idempotency record in LOCKED state with a default TTL of 24 hours.
     * <p>
     * Used when no existing record is found for the given key.
     *
     * @param conn      the active database connection
     * @param key       the idempotency key to insert
     * @param lockUntil timestamp (milliseconds) when the lock expires
     * @throws SQLException if the insert fails
     */
    private void insertLockedRecord(Connection conn, IdempotencyKey key, long lockUntil) throws SQLException {
        long now = System.currentTimeMillis();
        long defaultTtl = Duration.ofHours(24).toMillis();

        String sql = "INSERT INTO " + tableName + " (record_key, state, created_at, ttl, locked_until) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key.key());
            ps.setString(2, RecordState.LOCKED.name());
            ps.setLong(3, now);
            ps.setLong(4, defaultTtl);
            ps.setLong(5, lockUntil);
            ps.executeUpdate();
        }
        conn.commit();
    }

    /**
     * Resets an existing idempotency record to LOCKED state, clearing any previous result or error.
     * <p>
     * Used when an existing record is found but is in an expired or invalid state (e.g., stale LOCKED,
     * expired EXECUTED, or FAILED).
     *
     * @param conn      the active database connection
     * @param key       the idempotency key to update
     * @param lockUntil timestamp (milliseconds) when the new lock expires
     * @param now       current timestamp (milliseconds) to set as the new creation time
     * @throws SQLException if the update fails
     */
    private void updateToLocked(Connection conn, IdempotencyKey key, long lockUntil, long now) throws SQLException {
        long defaultTtl = Duration.ofHours(24).toMillis();

        String sql = "UPDATE " + tableName + " SET state = ?, locked_until = ?, created_at = ?, ttl = ?, result_data = NULL, error_data = NULL " +
                "WHERE record_key = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, RecordState.LOCKED.name());
            ps.setLong(2, lockUntil);
            ps.setLong(3, now);
            ps.setLong(4, defaultTtl);
            ps.setString(5, key.key());
            ps.executeUpdate();
        }
        conn.commit();
    }

    /**
     * Saves a successful result for a locked idempotency record, transitioning it to EXECUTED state.
     * <p>
     * This method must be called only after successfully acquiring a lock via {@link #getActiveRecordOrLock}.
     * If the record is not in LOCKED state, an exception is thrown.
     * <p>
     * The result object is serialized to JSON and stored. The lock is released by setting {@code locked_until} to NULL.
     * The TTL is set to the provided duration.
     *
     * @param key        the idempotency key associated with the record
     * @param result     the result object to store (must be serializable to JSON)
     * @param defaultTtl the time-to-live for this record (must not be null)
     * @return the saved record with updated state and metadata
     * @throws IllegalArgumentException if defaultTtl is null
     * @throws IllegalStateException    if the record is not in LOCKED state
     * @throws RuntimeException         if serialization or database errors occur
     */
    @Override
    public IdempotencyRecord<T> saveResult(IdempotencyKey key, T result, Duration defaultTtl) {
        if (defaultTtl == null) {
            throw new IllegalArgumentException("defaultTtl must not be null");
        }

        long now = System.currentTimeMillis();
        long ttlMillis = defaultTtl.toMillis();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                String selectSql = "SELECT record_key, state, result_data, error_data, created_at, ttl, locked_until " +
                        "FROM " + tableName + " WHERE record_key = ? FOR UPDATE";

                RecordState currentState = null;
                long createdAt = 0;

                try (PreparedStatement ps = connection.prepareStatement(selectSql)) {
                    ps.setString(1, key.key());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String stateStr = rs.getString("state");
                            currentState = RecordState.valueOf(stateStr);
                            createdAt = rs.getLong("created_at");
                        } else {
                            throw new IllegalStateException("Record with key '" + key.key() + "' not found. Cannot save result.");
                        }
                    }
                }

                if (currentState != RecordState.LOCKED) {
                    throw new IllegalStateException(
                            "Cannot save result: record with key '" + key.key() +
                                    "' is in state " + currentState + ", expected LOCKED."
                    );
                }

                String resultJson;
                try {
                    resultJson = objectMapper.writeValueAsString(result);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to serialize result object", e);
                }

                String updateSql = "UPDATE " + tableName +
                        " SET state = ?, result_data = ?, ttl = ?, locked_until = NULL " +
                        "WHERE record_key = ?";

                try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
                    ps.setString(1, RecordState.EXECUTED.name());
                    ps.setString(2, resultJson);
                    ps.setLong(3, ttlMillis);
                    ps.setString(4, key.key());

                    int rowsAffected = ps.executeUpdate();
                    if (rowsAffected == 0) {
                        throw new IllegalStateException("Record disappeared during update");
                    }
                }

                connection.commit();

                return new IdempotencyRecord<>(
                        key,
                        result,
                        null,
                        RecordState.EXECUTED,
                        createdAt,
                        ttlMillis,
                        null
                );

            } catch (SQLException e) {
                connection.rollback();
                throw new RuntimeException("Database error during saveResult", e);
            } catch (IllegalStateException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get connection", e);
        }
    }

    /**
     * Saves an error for a locked idempotency record, transitioning it to FAILED state.
     * <p>
     * This method must be called only after successfully acquiring a lock via {@link #getActiveRecordOrLock}.
     * If the record is not in LOCKED state, an exception is thrown.
     * <p>
     * The error object is serialized to JSON and stored. The lock is released by setting {@code locked_until} to NULL.
     * The TTL is set to the provided duration.
     * <p>
     * If the error cannot be serialized as a Java object, it is serialized as a map containing class name and message.
     *
     * @param key        the idempotency key associated with the record
     * @param error      the error object to store (must not be null)
     * @param defaultTtl the time-to-live for this record (must not be null)
     * @return the saved record with updated state and metadata
     * @throws IllegalArgumentException if defaultTtl or error is null
     * @throws IllegalStateException    if the record is not in LOCKED state
     * @throws RuntimeException         if serialization or database errors occur
     */
    @Override
    public IdempotencyRecord<T> saveError(IdempotencyKey key, Throwable error, Duration defaultTtl) {
        if (defaultTtl == null) {
            throw new IllegalArgumentException("defaultTtl must not be null");
        }
        if (error == null) {
            throw new IllegalArgumentException("error must not be null");
        }

        long now = System.currentTimeMillis();
        long ttlMillis = defaultTtl.toMillis();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                String selectSql = "SELECT record_key, state, created_at " +
                        "FROM " + tableName + " WHERE record_key = ? FOR UPDATE";

                RecordState currentState = null;
                long createdAt = 0;

                try (PreparedStatement ps = connection.prepareStatement(selectSql)) {
                    ps.setString(1, key.key());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String stateStr = rs.getString("state");
                            currentState = RecordState.valueOf(stateStr);
                            createdAt = rs.getLong("created_at");
                        } else {
                            throw new IllegalStateException("Record with key '" + key.key() + "' not found. Cannot save error.");
                        }
                    }
                }

                if (currentState != RecordState.LOCKED) {
                    throw new IllegalStateException(
                            "Cannot save error: record with key '" + key.key() +
                                    "' is in state " + currentState + ", expected LOCKED."
                    );
                }

                String errorJson;
                try {
                    errorJson = objectMapper.writeValueAsString(error);
                } catch (JsonProcessingException e) {
                    try {
                        Map<String, String> errorMap = new HashMap<>();
                        errorMap.put("class", error.getClass().getName());
                        errorMap.put("message", error.getMessage());
                        errorJson = objectMapper.writeValueAsString(errorMap);
                    } catch (JsonProcessingException ex) {
                        throw new RuntimeException("Failed to serialize error object", ex);
                    }
                }

                String updateSql = "UPDATE " + tableName +
                        " SET state = ?, error_data = ?, result_data = NULL, ttl = ?, locked_until = NULL " +
                        "WHERE record_key = ?";

                try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
                    ps.setString(1, RecordState.FAILED.name());
                    ps.setString(2, errorJson);
                    ps.setLong(3, ttlMillis);
                    ps.setString(4, key.key());

                    int rowsAffected = ps.executeUpdate();
                    if (rowsAffected == 0) {
                        throw new IllegalStateException("Record disappeared during update");
                    }
                }

                connection.commit();

                return new IdempotencyRecord<>(
                        key,
                        null,
                        error,
                        RecordState.FAILED,
                        createdAt,
                        ttlMillis,
                        null
                );

            } catch (SQLException e) {
                connection.rollback();
                throw new RuntimeException("Database error during saveError", e);
            } catch (IllegalStateException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get connection", e);
        }
    }

    /**
     * Retrieves the current state of an idempotency record without acquiring a lock.
     * <p>
     * Returns the record if it exists and is still valid (not expired). For EXECUTED records,
     * checks if TTL has expired. For LOCKED records, checks if lock has expired.
     * FAILED records are always returned regardless of TTL, as they represent terminal state.
     * <p>
     * This method is safe for read-only operations and does not interfere with concurrent
     * lock acquisition or result/error saving.
     *
     * @param key the idempotency key to look up
     * @return an Optional containing the record if found and valid, or empty if not found or expired
     * @throws RuntimeException if a database or deserialization error occurs
     */
    @Override
    public Optional<IdempotencyRecord<T>> get(IdempotencyKey key) {
        long now = System.currentTimeMillis();

        try (Connection connection = dataSource.getConnection()) {
            String selectSql = "SELECT record_key, state, result_data, error_data, created_at, ttl, locked_until " +
                    "FROM " + tableName + " WHERE record_key = ?";

            try (PreparedStatement ps = connection.prepareStatement(selectSql)) {
                ps.setString(1, key.key());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String stateStr = rs.getString("state");
                        RecordState state = RecordState.valueOf(stateStr);
                        long createdAt = rs.getLong("created_at");
                        long ttl = rs.getLong("ttl");
                        Long lockedUntil = rs.getObject("locked_until", Long.class);

                        if (state == RecordState.EXECUTED) {
                            if (now - createdAt > ttl) {
                                return Optional.empty();
                            }
                        }

                        if (state == RecordState.LOCKED) {
                            if (lockedUntil != null && now > lockedUntil) {
                                return Optional.empty();
                            }
                        }

                        T result = null;
                        String resultData = rs.getString("result_data");
                        if (resultData != null && !resultData.isEmpty()) {
                            result = objectMapper.readValue(resultData, resultType);
                        }

                        Throwable error = null;
                        String errorData = rs.getString("error_data");
                        if (errorData != null && !errorData.isEmpty()) {
                            error = new RuntimeException("Restored error: " + errorData);
                        }

                        return Optional.of(new IdempotencyRecord<>(
                                key,
                                result,
                                error,
                                state,
                                createdAt,
                                ttl,
                                lockedUntil
                        ));
                    } else {
                        return Optional.empty();
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error during get", e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize data from database", e);
        }
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Builder for creating {@link JdbcIdempotencyStore} instances.
     * <p>
     * Ensures all required parameters are set before building. The {@code resultType} is mandatory
     * to enable proper JSON deserialization of stored results.
     *
     * @param <T> the type of the result object to be serialized/deserialized
     */
    public static class Builder<T> {
        private DataSource dataSource;
        private ObjectMapper objectMapper = new ObjectMapper();
        private String tableName = "idempotency_records";
        private Class<T> resultType;

        /**
         * Sets the JDBC data source for database connections.
         *
         * @param dataSource the data source (must not be null)
         * @return this builder
         */
        public Builder<T> dataSource(DataSource dataSource) {
            this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
            return this;
        }

        /**
         * Sets the ObjectMapper for JSON serialization/deserialization.
         * Defaults to a new instance if not provided.
         *
         * @param objectMapper the ObjectMapper (must not be null)
         * @return this builder
         */
        public Builder<T> objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
            return this;
        }

        /**
         * Sets the name of the idempotency table in the database.
         * Defaults to "idempotency_records".
         *
         * @param tableName the table name (must not be null or empty)
         * @return this builder
         */
        public Builder<T> tableName(String tableName) {
            if (tableName == null || tableName.trim().isEmpty()) {
                throw new IllegalArgumentException("tableName must not be null or empty");
            }
            this.tableName = tableName;
            return this;
        }

        /**
         * Sets the result type class for proper deserialization.
         * This is required to correctly map JSON data back to Java objects.
         *
         * @param resultType the class of the result type
         * @return this builder
         */
        public Builder<T> resultType(Class<T> resultType) {
            this.resultType = Objects.requireNonNull(resultType, "resultType must not be null");
            return this;
        }

        /**
         * Constructs a new {@link JdbcIdempotencyStore} instance with the configured parameters.
         *
         * @return a fully configured JdbcIdempotencyStore
         * @throws IllegalStateException if required fields (dataSource, resultType) are not set
         */
        public JdbcIdempotencyStore<T> build() {
            if (dataSource == null) {
                throw new IllegalStateException("dataSource must be set");
            }
            if (resultType == null) {
                throw new IllegalStateException("resultType must be set for proper deserialization");
            }
            return new JdbcIdempotencyStore<>(dataSource, objectMapper, tableName, resultType);
        }
    }
}