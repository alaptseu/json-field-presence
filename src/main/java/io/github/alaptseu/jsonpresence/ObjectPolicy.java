package io.github.alaptseu.jsonpresence;

/** Controls how supplied JSON objects are applied to previous object values. */
public enum ObjectPolicy {
    /** Recursively overlay supplied object properties and preserve omitted properties. */
    MERGE,

    /** Replace the complete previous object with the supplied object. */
    REPLACE
}
