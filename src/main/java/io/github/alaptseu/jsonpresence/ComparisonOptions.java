package io.github.alaptseu.jsonpresence;

import java.util.Objects;

/**
 * Immutable behavior and input-safety options for a JSON comparison.
 */
public record ComparisonOptions(
        NullPolicy nullPolicy,
        ObjectPolicy objectPolicy,
        int maxDepth,
        int maxNodes,
        int maxFieldCount) {

    /** Default maximum nesting depth, counting the document root as depth zero. */
    public static final int DEFAULT_MAX_DEPTH = 100;

    /**
     * Absolute depth ceiling that bounds Jackson operations performed by a
     * trusted custom mapper.
     */
    public static final int MAX_SUPPORTED_DEPTH = 256;

    /** Default maximum number of object, array, and scalar nodes per input document. */
    public static final int DEFAULT_MAX_NODES = 100_000;

    /** Default maximum number of object fields per input document and comparison report. */
    public static final int DEFAULT_MAX_FIELD_COUNT = 100_000;

    private static final ComparisonOptions DEFAULTS = new ComparisonOptions(
            NullPolicy.SET_NULL,
            ObjectPolicy.MERGE,
            DEFAULT_MAX_DEPTH,
            DEFAULT_MAX_NODES,
            DEFAULT_MAX_FIELD_COUNT);

    /**
     * Creates options using the default field-count limit.
     *
     * @deprecated Prefer the builder or the canonical constructor so the
     * field-count limit is explicit.
     */
    @Deprecated(forRemoval = false)
    public ComparisonOptions(
            NullPolicy nullPolicy,
            ObjectPolicy objectPolicy,
            int maxDepth,
            int maxNodes) {
        this(nullPolicy, objectPolicy, maxDepth, maxNodes, DEFAULT_MAX_FIELD_COUNT);
    }

    public ComparisonOptions {
        Objects.requireNonNull(nullPolicy, "nullPolicy");
        Objects.requireNonNull(objectPolicy, "objectPolicy");
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be zero or greater");
        }
        if (maxDepth > MAX_SUPPORTED_DEPTH) {
            throw new IllegalArgumentException(
                    "maxDepth must not exceed " + MAX_SUPPORTED_DEPTH);
        }
        if (maxNodes < 1) {
            throw new IllegalArgumentException("maxNodes must be at least one");
        }
        if (maxFieldCount < 0) {
            throw new IllegalArgumentException("maxFieldCount must be zero or greater");
        }
    }

    /** Returns the default behavior: preserve nulls, merge objects, and enforce safe limits. */
    public static ComparisonOptions defaults() {
        return DEFAULTS;
    }

    /** Starts a builder initialized with the default options. */
    public static Builder builder() {
        return new Builder();
    }

    /** Starts a builder initialized with this option set. */
    public Builder toBuilder() {
        return new Builder()
                .nullPolicy(nullPolicy)
                .objectPolicy(objectPolicy)
                .maxDepth(maxDepth)
                .maxNodes(maxNodes)
                .maxFieldCount(maxFieldCount);
    }

    /** Fluent builder for {@link ComparisonOptions}. */
    public static final class Builder {
        private NullPolicy nullPolicy = NullPolicy.SET_NULL;
        private ObjectPolicy objectPolicy = ObjectPolicy.MERGE;
        private int maxDepth = DEFAULT_MAX_DEPTH;
        private int maxNodes = DEFAULT_MAX_NODES;
        private int maxFieldCount = DEFAULT_MAX_FIELD_COUNT;

        private Builder() {
        }

        public Builder nullPolicy(NullPolicy nullPolicy) {
            this.nullPolicy = Objects.requireNonNull(nullPolicy, "nullPolicy");
            return this;
        }

        public Builder objectPolicy(ObjectPolicy objectPolicy) {
            this.objectPolicy = Objects.requireNonNull(objectPolicy, "objectPolicy");
            return this;
        }

        /** Sets a depth from zero through {@link ComparisonOptions#MAX_SUPPORTED_DEPTH}. */
        public Builder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder maxNodes(int maxNodes) {
            this.maxNodes = maxNodes;
            return this;
        }

        public Builder maxFieldCount(int maxFieldCount) {
            this.maxFieldCount = maxFieldCount;
            return this;
        }

        public ComparisonOptions build() {
            return new ComparisonOptions(
                    nullPolicy,
                    objectPolicy,
                    maxDepth,
                    maxNodes,
                    maxFieldCount);
        }
    }
}
