package io.github.alaptseu.jsonpresence;

/** Controls how an explicitly supplied JSON null affects the effective document. */
public enum NullPolicy {
    /** Keep the property and set its effective value to JSON null. */
    SET_NULL,

    /** Remove the property from the effective document. */
    REMOVE_FIELD
}
