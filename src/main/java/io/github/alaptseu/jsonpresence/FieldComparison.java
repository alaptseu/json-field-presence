package io.github.alaptseu.jsonpresence;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.Optional;

/**
 * Presence and value information for one JSON Pointer path.
 */
public final class FieldComparison {
    private final String pointer;
    private final FieldState previousState;
    private final FieldState suppliedState;
    private final FieldState effectiveState;
    private final JsonNode previousValue;
    private final JsonNode suppliedValue;
    private final JsonNode effectiveValue;

    FieldComparison(
            String pointer,
            JsonNode previousValue,
            JsonNode suppliedValue,
            JsonNode effectiveValue) {
        this.pointer = Objects.requireNonNull(pointer, "pointer");
        this.previousValue = Objects.requireNonNull(previousValue, "previousValue");
        this.suppliedValue = Objects.requireNonNull(suppliedValue, "suppliedValue");
        this.effectiveValue = Objects.requireNonNull(effectiveValue, "effectiveValue");
        this.previousState = FieldState.from(previousValue);
        this.suppliedState = FieldState.from(suppliedValue);
        this.effectiveState = FieldState.from(effectiveValue);
    }

    /** Returns the RFC 6901 JSON Pointer for this field. */
    public String pointer() {
        return pointer;
    }

    /** Returns the field state in the previous document. */
    public FieldState previousState() {
        return previousState;
    }

    /**
     * Returns the field state in the supplied request. {@link FieldState#ABSENT}
     * means the caller did not pass this path.
     */
    public FieldState suppliedState() {
        return suppliedState;
    }

    /** Returns the field state after the request is overlaid on the previous document. */
    public FieldState effectiveState() {
        return effectiveState;
    }

    /** Returns a defensive copy of the previous value, or empty when it was absent. */
    public Optional<JsonNode> previousValue() {
        return optionalCopy(previousState, previousValue);
    }

    /** Returns a defensive copy of the supplied value, or empty when it was omitted. */
    public Optional<JsonNode> suppliedValue() {
        return optionalCopy(suppliedState, suppliedValue);
    }

    /** Returns a defensive copy of the effective value, or empty when it is absent. */
    public Optional<JsonNode> effectiveValue() {
        return optionalCopy(effectiveState, effectiveValue);
    }

    /** Returns true when this exact pointer occurs in the supplied request. */
    public boolean wasSupplied() {
        return suppliedState != FieldState.ABSENT;
    }

    /** Returns true when this exact pointer was explicitly supplied as JSON null. */
    public boolean isExplicitNull() {
        return suppliedState == FieldState.NULL;
    }

    /** Returns true when this exact pointer was not supplied. */
    public boolean wasOmitted() {
        return suppliedState == FieldState.ABSENT;
    }

    /**
     * Returns true when the effective value differs from the previous value.
     * An omitted descendant can be changed indirectly when an ancestor is replaced.
     */
    public boolean isChanged() {
        return !previousValue.equals(effectiveValue);
    }

    private static Optional<JsonNode> optionalCopy(FieldState state, JsonNode value) {
        return state == FieldState.ABSENT ? Optional.empty() : Optional.of(copy(value));
    }

    private static JsonNode copy(JsonNode value) {
        return JsonNodeCopies.copy(value);
    }

    @Override
    public String toString() {
        return "FieldComparison{" +
                "pointer='" + pointer + '\'' +
                ", previousState=" + previousState +
                ", suppliedState=" + suppliedState +
                ", effectiveState=" + effectiveState +
                ", changed=" + isChanged() +
                '}';
    }
}
