package org.gribforyou;

/**
 * Represents the state of an idempotency record.
 */
public enum RecordState {
    LOCKED,
    EXECUTED,
    FAILED
}
