package io.github.alaptseu.jsonpresence;

/**
 * Immutable limits applied while JSON strings are tokenized, before a Jackson
 * tree is materialized.
 */
public record JsonParsingOptions(
        long maxDocumentLength,
        int maxStringLength,
        int maxNumberLength,
        int maxPropertyNameLength) {

    /** Default maximum JSON document length in characters. */
    public static final long DEFAULT_MAX_DOCUMENT_LENGTH = 20_000_000L;

    /** Default maximum decoded JSON string length in characters. */
    public static final int DEFAULT_MAX_STRING_LENGTH = 20_000_000;

    /** Default maximum JSON number token length in characters. */
    public static final int DEFAULT_MAX_NUMBER_LENGTH = 1_000;

    /** Default maximum decoded JSON property-name length in characters. */
    public static final int DEFAULT_MAX_PROPERTY_NAME_LENGTH = 50_000;

    private static final JsonParsingOptions DEFAULTS = new JsonParsingOptions(
            DEFAULT_MAX_DOCUMENT_LENGTH,
            DEFAULT_MAX_STRING_LENGTH,
            DEFAULT_MAX_NUMBER_LENGTH,
            DEFAULT_MAX_PROPERTY_NAME_LENGTH);

    public JsonParsingOptions {
        if (maxDocumentLength < 1) {
            throw new IllegalArgumentException("maxDocumentLength must be at least one");
        }
        if (maxStringLength < 1) {
            throw new IllegalArgumentException("maxStringLength must be at least one");
        }
        if (maxNumberLength < 1) {
            throw new IllegalArgumentException("maxNumberLength must be at least one");
        }
        if (maxPropertyNameLength < 1) {
            throw new IllegalArgumentException(
                    "maxPropertyNameLength must be at least one");
        }
    }

    /** Returns the default bounded parsing limits. */
    public static JsonParsingOptions defaults() {
        return DEFAULTS;
    }

    /** Starts a builder initialized with the default parsing limits. */
    public static Builder builder() {
        return new Builder();
    }

    /** Starts a builder initialized with this option set. */
    public Builder toBuilder() {
        return new Builder()
                .maxDocumentLength(maxDocumentLength)
                .maxStringLength(maxStringLength)
                .maxNumberLength(maxNumberLength)
                .maxPropertyNameLength(maxPropertyNameLength);
    }

    /** Fluent builder for {@link JsonParsingOptions}. */
    public static final class Builder {
        private long maxDocumentLength = DEFAULT_MAX_DOCUMENT_LENGTH;
        private int maxStringLength = DEFAULT_MAX_STRING_LENGTH;
        private int maxNumberLength = DEFAULT_MAX_NUMBER_LENGTH;
        private int maxPropertyNameLength = DEFAULT_MAX_PROPERTY_NAME_LENGTH;

        private Builder() {
        }

        public Builder maxDocumentLength(long maxDocumentLength) {
            this.maxDocumentLength = maxDocumentLength;
            return this;
        }

        public Builder maxStringLength(int maxStringLength) {
            this.maxStringLength = maxStringLength;
            return this;
        }

        public Builder maxNumberLength(int maxNumberLength) {
            this.maxNumberLength = maxNumberLength;
            return this;
        }

        public Builder maxPropertyNameLength(int maxPropertyNameLength) {
            this.maxPropertyNameLength = maxPropertyNameLength;
            return this;
        }

        public JsonParsingOptions build() {
            return new JsonParsingOptions(
                    maxDocumentLength,
                    maxStringLength,
                    maxNumberLength,
                    maxPropertyNameLength);
        }
    }
}
