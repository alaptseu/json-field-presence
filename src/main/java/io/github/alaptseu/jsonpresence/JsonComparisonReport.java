package io.github.alaptseu.jsonpresence;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The comparison between a previous JSON object and a supplied partial request.
 */
public final class JsonComparisonReport {
    private final ObjectNode previous;
    private final ObjectNode supplied;
    private final ObjectNode effective;
    private final Map<String, FieldComparison> fields;
    private final Map<String, FieldComparison> changedFields;

    JsonComparisonReport(
            ObjectNode previous,
            ObjectNode supplied,
            ObjectNode effective,
            Map<String, FieldComparison> fields) {
        this.previous = Objects.requireNonNull(previous, "previous");
        this.supplied = Objects.requireNonNull(supplied, "supplied");
        this.effective = Objects.requireNonNull(effective, "effective");

        Map<String, FieldComparison> allFields = new LinkedHashMap<>(fields);
        this.fields = Collections.unmodifiableMap(allFields);

        Map<String, FieldComparison> changed = new LinkedHashMap<>();
        allFields.forEach((pointer, comparison) -> {
            if (comparison.isChanged()) {
                changed.put(pointer, comparison);
            }
        });
        this.changedFields = Collections.unmodifiableMap(changed);
    }

    /**
     * Gets comparison details for any RFC 6901 JSON Pointer. Use an empty string
     * for the document root, {@code /name} for a top-level field, and
     * {@code /address/city} for a nested field. This method can also address
     * array elements and their descendants, such as {@code /items/0/name}, even
     * though {@link #fields()} treats arrays as atomic values.
     */
    public FieldComparison field(String pointer) {
        Objects.requireNonNull(pointer, "pointer");

        final JsonPointer compiled;
        try {
            compiled = JsonPointer.compile(pointer);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid RFC 6901 JSON Pointer: " + pointer, exception);
        }

        return new FieldComparison(
                pointer,
                previous.at(compiled),
                supplied.at(compiled),
                effective.at(compiled));
    }

    /**
     * Returns all object-property paths found in the previous, supplied, or
     * effective document. The root is not included. Arrays are atomic values:
     * the property containing an array is enumerated, but array indices and
     * their descendants are not. Use {@link #field(String)} to query those
     * paths directly. Map iteration order is deterministic.
     */
    public Map<String, FieldComparison> fields() {
        return fields;
    }

    /**
     * Returns all enumerated object-property paths whose effective value
     * changed. An array replacement is reported at the property containing the
     * array; array indices and their descendants are not enumerated.
     */
    public Map<String, FieldComparison> changedFields() {
        return changedFields;
    }

    /** Returns a defensive copy of the previous JSON object. */
    public JsonNode previousJson() {
        return JsonNodeCopies.copy(previous);
    }

    /** Returns a defensive copy of the supplied JSON object. */
    public JsonNode suppliedJson() {
        return JsonNodeCopies.copy(supplied);
    }

    /**
     * Returns a defensive copy of the effective object. Omitted properties keep
     * their previous values; supplied properties, including nulls, are applied.
     */
    public JsonNode effectiveJson() {
        return JsonNodeCopies.copy(effective);
    }

    /** Returns the effective object as compact JSON. */
    public String effectiveJsonString() {
        return effective.toString();
    }
}
