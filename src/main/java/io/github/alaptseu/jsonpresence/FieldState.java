package io.github.alaptseu.jsonpresence;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Describes whether a field is absent, explicitly null, or has a JSON value.
 */
public enum FieldState {
    /** The field does not occur at the requested JSON Pointer. */
    ABSENT,

    /** The field occurs and its value is the JSON literal {@code null}. */
    NULL,

    /** The field occurs and has a non-null value, including an object or array. */
    VALUE;

    static FieldState from(JsonNode node) {
        if (node.isMissingNode()) {
            return ABSENT;
        }
        if (node.isNull()) {
            return NULL;
        }
        return VALUE;
    }
}
