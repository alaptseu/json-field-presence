package io.github.alaptseu.jsonpresence;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Entry point for comparing a partial JSON request with a previous JSON object.
 */
public final class JsonFieldPresence {
    private static final ObjectMapper DEFAULT_MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private final ObjectMapper mapper;
    private final ComparisonOptions options;
    private final JsonParsingOptions parsingOptions;
    private final JsonFactory parsingFactory;

    /** Creates an analyzer using a default Jackson mapper. */
    public JsonFieldPresence() {
        this(DEFAULT_MAPPER, ComparisonOptions.defaults(), JsonParsingOptions.defaults());
    }

    /**
     * Creates an analyzer using the supplied mapper and default comparison options.
     * The mapper parses JSON strings and converts Java maps into Jackson trees.
     */
    public JsonFieldPresence(ObjectMapper mapper) {
        this(mapper, ComparisonOptions.defaults(), JsonParsingOptions.defaults());
    }

    /** Creates an analyzer using the default mapper and supplied options. */
    public JsonFieldPresence(ComparisonOptions options) {
        this(DEFAULT_MAPPER, options, JsonParsingOptions.defaults());
    }

    /**
     * Creates an analyzer using the supplied mapper and comparison options.
     * The mapper parses JSON strings and converts Java maps into Jackson trees.
     */
    public JsonFieldPresence(ObjectMapper mapper, ComparisonOptions options) {
        this(mapper, options, JsonParsingOptions.defaults());
    }

    private JsonFieldPresence(
            ObjectMapper mapper,
            ComparisonOptions options,
            JsonParsingOptions parsingOptions) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.options = Objects.requireNonNull(options, "options");
        this.parsingOptions = Objects.requireNonNull(parsingOptions, "parsingOptions");
        this.parsingFactory = createParsingFactory(mapper, options, parsingOptions);
    }

    /** Starts a builder initialized with the default mapper and option sets. */
    public static Builder builder() {
        return new Builder();
    }

    /** Compares JSON strings using the default Jackson mapper. */
    public static JsonComparisonReport compareJson(String previousJson, String suppliedJson) {
        return new JsonFieldPresence().analyzeJson(previousJson, suppliedJson);
    }

    /** Compares JSON strings using explicit comparison options. */
    public static JsonComparisonReport compareJson(
            String previousJson,
            String suppliedJson,
            ComparisonOptions options) {
        return new JsonFieldPresence(options).analyzeJson(previousJson, suppliedJson);
    }

    /**
     * Compares already-parsed Jackson JSON objects containing standard Jackson
     * scalar nodes, object nodes, and array nodes.
     */
    public static JsonComparisonReport compareTrees(JsonNode previousJson, JsonNode suppliedJson) {
        return new JsonFieldPresence().analyzeTrees(previousJson, suppliedJson);
    }

    /**
     * Compares Jackson trees using explicit comparison options. Trees may
     * contain standard Jackson scalar nodes, object nodes, and array nodes.
     */
    public static JsonComparisonReport compareTrees(
            JsonNode previousJson,
            JsonNode suppliedJson,
            ComparisonOptions options) {
        return new JsonFieldPresence(options).analyzeTrees(previousJson, suppliedJson);
    }

    /**
     * Compares JSON objects represented as Java maps. Values must be
     * JSON-native scalars, maps with string keys, iterables, arrays, or safe
     * Jackson tree nodes; arbitrary POJOs are rejected.
     */
    public static JsonComparisonReport compareMaps(
            Map<String, ?> previousJson,
            Map<String, ?> suppliedJson) {
        return new JsonFieldPresence().analyzeMaps(previousJson, suppliedJson);
    }

    /**
     * Compares Java maps using explicit comparison options. Values must be
     * JSON-native scalars, maps with string keys, iterables, arrays, or safe
     * Jackson tree nodes; arbitrary POJOs are rejected.
     */
    public static JsonComparisonReport compareMaps(
            Map<String, ?> previousJson,
            Map<String, ?> suppliedJson,
            ComparisonOptions options) {
        return new JsonFieldPresence(options).analyzeMaps(previousJson, suppliedJson);
    }

    /** Parses and compares two JSON objects. */
    public JsonComparisonReport analyzeJson(String previousJson, String suppliedJson) {
        return analyzeTrees(
                parseObject(previousJson, "previousJson"),
                parseObject(suppliedJson, "suppliedJson"));
    }

    /**
     * Converts and compares two JSON objects represented as Java maps. Values
     * must have a JSON-native shape; arbitrary POJOs and unsafe embedded tree
     * nodes are rejected before mapper conversion.
     */
    public JsonComparisonReport analyzeMaps(
            Map<String, ?> previousJson,
            Map<String, ?> suppliedJson) {
        return analyzeTrees(
                mapToObject(previousJson, "previousJson"),
                mapToObject(suppliedJson, "suppliedJson"));
    }

    /** Compares two already-parsed JSON objects. */
    public JsonComparisonReport analyzeTrees(JsonNode previousJson, JsonNode suppliedJson) {
        ObjectNode previousInput = requireObject(previousJson, "previousJson");
        ObjectNode suppliedInput = requireObject(suppliedJson, "suppliedJson");
        validateLimits(previousInput, "previousJson");
        validateLimits(suppliedInput, "suppliedJson");

        ObjectNode previous = JsonNodeCopies.copyObject(previousInput);
        ObjectNode supplied = JsonNodeCopies.copyObject(suppliedInput);
        ObjectNode effective = JsonNodeCopies.copyObject(previous);
        applyOverlay(effective, supplied);
        validateLimits(effective, "effectiveJson");

        Map<String, FieldComparison> fields = new LinkedHashMap<>();
        collectFields("", previous, supplied, effective, fields);

        return new JsonComparisonReport(previous, supplied, effective, fields);
    }

    private ObjectNode parseObject(String json, String parameterName) {
        if (json == null) {
            throw new IllegalArgumentException(parameterName + " must not be null");
        }

        if ((long) json.length() > parsingOptions.maxDocumentLength()) {
            throw new IllegalArgumentException(
                    parameterName + " exceeds maxDocumentLength "
                            + parsingOptions.maxDocumentLength());
        }

        final JsonNode parsed;
        try {
            preflightJson(json, parameterName);
            try (JsonParser parser = parsingFactory.createParser(json)) {
                parsed = mapper.readTree(parser);
            }
        } catch (StreamConstraintsException exception) {
            throw new IllegalArgumentException(
                    parameterName + " exceeds configured JSON parsing limits", exception);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(parameterName + " is not valid JSON", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException(parameterName + " could not be read", exception);
        }
        return requireObject(parsed, parameterName);
    }

    private void preflightJson(String json, String parameterName) throws IOException {
        try (JsonParser parser = parsingFactory.createParser(json)) {
            JsonToken token = parser.nextToken();
            if (token != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException(parameterName + " must be a JSON object");
            }

            long nodeCount = 0;
            long fieldCount = 0;
            int openContainers = 0;
            boolean rootComplete = false;

            while (token != null) {
                if (rootComplete) {
                    throw new IllegalArgumentException(
                            parameterName + " must contain exactly one JSON object");
                }

                if (token == JsonToken.FIELD_NAME) {
                    fieldCount++;
                    if (fieldCount > options.maxFieldCount()) {
                        throw new IllegalArgumentException(
                                parameterName + " exceeds maxFieldCount "
                                        + options.maxFieldCount());
                    }
                } else if (token == JsonToken.START_OBJECT
                        || token == JsonToken.START_ARRAY) {
                    validateParsedNode(openContainers, ++nodeCount, parameterName);
                    openContainers++;
                } else if (token == JsonToken.END_OBJECT
                        || token == JsonToken.END_ARRAY) {
                    openContainers--;
                    if (openContainers == 0) {
                        rootComplete = true;
                    }
                } else if (token.isScalarValue()) {
                    validateParsedNode(openContainers, ++nodeCount, parameterName);
                }

                token = parser.nextToken();
            }

            if (!rootComplete) {
                throw new IllegalArgumentException(parameterName + " is not valid JSON");
            }
        }
    }

    private void validateParsedNode(int depth, long nodeCount, String parameterName) {
        if (depth > options.maxDepth()) {
            throw new IllegalArgumentException(
                    parameterName + " exceeds maxDepth " + options.maxDepth());
        }
        if (nodeCount > options.maxNodes()) {
            throw new IllegalArgumentException(
                    parameterName + " exceeds maxNodes " + options.maxNodes());
        }
    }

    private static JsonFactory createParsingFactory(
            ObjectMapper mapper,
            ComparisonOptions options,
            JsonParsingOptions parsingOptions) {
        JsonFactory factory = mapper.getFactory().copy();
        StreamReadConstraints existing = factory.streamReadConstraints();
        long configuredTokenLimit = 2L * options.maxNodes() + options.maxFieldCount();
        StreamReadConstraints constraints = existing.rebuild()
                .maxNestingDepth(Math.min(
                        existing.getMaxNestingDepth(),
                        options.maxDepth() + 1))
                .maxDocumentLength(mostRestrictive(
                        existing.getMaxDocumentLength(),
                        parsingOptions.maxDocumentLength()))
                .maxTokenCount(mostRestrictive(
                        existing.getMaxTokenCount(),
                        configuredTokenLimit))
                .maxStringLength(Math.min(
                        existing.getMaxStringLength(),
                        parsingOptions.maxStringLength()))
                .maxNumberLength(Math.min(
                        existing.getMaxNumberLength(),
                        parsingOptions.maxNumberLength()))
                .maxNameLength(Math.min(
                        existing.getMaxNameLength(),
                        parsingOptions.maxPropertyNameLength()))
                .build();
        return factory.setStreamReadConstraints(constraints);
    }

    private static long mostRestrictive(long existing, long configured) {
        return existing < 0 ? configured : Math.min(existing, configured);
    }

    private ObjectNode mapToObject(Map<String, ?> value, String parameterName) {
        if (value == null) {
            throw new IllegalArgumentException(parameterName + " must not be null");
        }

        validateJavaValueLimits(value, parameterName);
        try {
            return requireObject(mapper.valueToTree(value), parameterName);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    parameterName + " could not be converted to a JSON object", exception);
        }
    }

    private static ObjectNode requireObject(JsonNode node, String parameterName) {
        if (!(node instanceof ObjectNode object)) {
            throw new IllegalArgumentException(parameterName + " must be a JSON object");
        }
        return object;
    }

    private void applyOverlay(ObjectNode target, ObjectNode supplied) {
        Deque<OverlayTask> pending = new ArrayDeque<>();
        pending.push(new OverlayTask(target, supplied));

        while (!pending.isEmpty()) {
            OverlayTask task = pending.pop();
            for (Map.Entry<String, JsonNode> field : task.supplied().properties()) {
                String name = field.getKey();
                JsonNode suppliedValue = field.getValue();

                if (suppliedValue.isNull()) {
                    if (options.nullPolicy() == NullPolicy.REMOVE_FIELD) {
                        task.target().remove(name);
                    } else {
                        task.target().set(name, suppliedValue);
                    }
                } else if (suppliedValue.isObject()) {
                    ObjectNode destination;
                    if (options.objectPolicy() == ObjectPolicy.MERGE) {
                        JsonNode targetValue = task.target().get(name);
                        if (targetValue != null && targetValue.isObject()) {
                            destination = (ObjectNode) targetValue;
                        } else {
                            destination = task.target().objectNode();
                            task.target().set(name, destination);
                        }
                    } else {
                        destination = task.target().objectNode();
                        task.target().set(name, destination);
                    }
                    pending.push(new OverlayTask(destination, (ObjectNode) suppliedValue));
                } else {
                    task.target().set(name, JsonNodeCopies.copy(suppliedValue));
                }
            }
        }
    }

    private void validateLimits(JsonNode root, String parameterName) {
        long scheduledNodes = 1;
        long fieldCount = 0;
        Deque<NodeVisit> pending = new ArrayDeque<>();
        IdentityHashMap<JsonNode, Boolean> ancestors = new IdentityHashMap<>();
        pending.push(NodeVisit.enter(root, 0));

        while (!pending.isEmpty()) {
            NodeVisit current = pending.pop();
            if (current.exiting()) {
                ancestors.remove(current.node());
                continue;
            }

            if (current.depth() > options.maxDepth()) {
                throw new IllegalArgumentException(
                        parameterName + " exceeds maxDepth " + options.maxDepth());
            }

            validateJsonNodeType(current.node(), parameterName);

            if (!current.node().isContainerNode()) {
                continue;
            }
            if (ancestors.put(current.node(), Boolean.TRUE) != null) {
                throw new IllegalArgumentException(
                        parameterName + " contains a cyclic JSON structure");
            }
            pending.push(NodeVisit.exit(current.node(), current.depth()));

            if (current.node().isObject()) {
                for (Map.Entry<String, JsonNode> field : current.node().properties()) {
                    fieldCount++;
                    if (fieldCount > options.maxFieldCount()) {
                        throw new IllegalArgumentException(
                                parameterName + " exceeds maxFieldCount "
                                        + options.maxFieldCount());
                    }
                    scheduledNodes = scheduleNode(
                            field.getValue(),
                            current.depth() + 1,
                            scheduledNodes,
                            parameterName,
                            pending);
                }
            } else {
                for (JsonNode child : current.node()) {
                    scheduledNodes = scheduleNode(
                            child,
                            current.depth() + 1,
                            scheduledNodes,
                            parameterName,
                            pending);
                }
            }
        }
    }

    private long scheduleNode(
            JsonNode node,
            int depth,
            long scheduledNodes,
            String parameterName,
            Deque<NodeVisit> pending) {
        long nextCount = scheduledNodes + 1;
        if (nextCount > options.maxNodes()) {
            throw new IllegalArgumentException(
                    parameterName + " exceeds maxNodes " + options.maxNodes());
        }
        pending.push(NodeVisit.enter(node, depth));
        return nextCount;
    }

    private void validateJavaValueLimits(Map<String, ?> root, String parameterName) {
        long scheduledNodes = 1;
        long fieldCount = 0;
        Deque<JavaValueVisit> pending = new ArrayDeque<>();
        IdentityHashMap<Object, Boolean> ancestors = new IdentityHashMap<>();
        pending.push(JavaValueVisit.enter(root, 0));

        while (!pending.isEmpty()) {
            JavaValueVisit current = pending.pop();
            Object value = current.value();
            if (current.exiting()) {
                ancestors.remove(value);
                continue;
            }
            if (current.depth() > options.maxDepth()) {
                throw new IllegalArgumentException(
                        parameterName + " exceeds maxDepth " + options.maxDepth());
            }
            if (value == null) {
                continue;
            }
            if (value instanceof JsonNode node) {
                validateJsonNodeType(node, parameterName);
                if (!node.isContainerNode()) {
                    continue;
                }
            } else if (isSupportedJavaScalar(value)) {
                validateFiniteNumber(value, parameterName);
                continue;
            } else if (!isJavaContainer(value)) {
                throw new IllegalArgumentException(
                        parameterName + " contains unsupported Java value type "
                                + value.getClass().getName());
            }
            if (ancestors.put(value, Boolean.TRUE) != null) {
                throw new IllegalArgumentException(
                        parameterName + " contains a cyclic Java container");
            }
            pending.push(JavaValueVisit.exit(value, current.depth()));

            if (value instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> field : map.entrySet()) {
                    if (!(field.getKey() instanceof String)) {
                        throw new IllegalArgumentException(
                                parameterName + " contains a non-string map key");
                    }
                    fieldCount++;
                    if (fieldCount > options.maxFieldCount()) {
                        throw new IllegalArgumentException(
                                parameterName + " exceeds maxFieldCount "
                                        + options.maxFieldCount());
                    }
                    scheduledNodes = scheduleJavaValue(
                            field.getValue(),
                            current.depth() + 1,
                            scheduledNodes,
                            parameterName,
                            pending);
                }
            } else if (value instanceof JsonNode node) {
                if (node.isObject()) {
                    for (Map.Entry<String, JsonNode> field : node.properties()) {
                        fieldCount++;
                        if (fieldCount > options.maxFieldCount()) {
                            throw new IllegalArgumentException(
                                    parameterName + " exceeds maxFieldCount "
                                            + options.maxFieldCount());
                        }
                        scheduledNodes = scheduleJavaValue(
                                field.getValue(),
                                current.depth() + 1,
                                scheduledNodes,
                                parameterName,
                                pending);
                    }
                } else {
                    for (JsonNode child : node) {
                        scheduledNodes = scheduleJavaValue(
                                child,
                                current.depth() + 1,
                                scheduledNodes,
                                parameterName,
                                pending);
                    }
                }
            } else if (value instanceof Iterable<?> iterable) {
                for (Object child : iterable) {
                    scheduledNodes = scheduleJavaValue(
                            child,
                            current.depth() + 1,
                            scheduledNodes,
                            parameterName,
                            pending);
                }
            } else {
                int length = Array.getLength(value);
                for (int index = 0; index < length; index++) {
                    scheduledNodes = scheduleJavaValue(
                            Array.get(value, index),
                            current.depth() + 1,
                            scheduledNodes,
                            parameterName,
                            pending);
                }
            }
        }
    }

    private long scheduleJavaValue(
            Object value,
            int depth,
            long scheduledNodes,
            String parameterName,
            Deque<JavaValueVisit> pending) {
        long nextCount = scheduledNodes + 1;
        if (nextCount > options.maxNodes()) {
            throw new IllegalArgumentException(
                    parameterName + " exceeds maxNodes " + options.maxNodes());
        }
        pending.push(JavaValueVisit.enter(value, depth));
        return nextCount;
    }

    private static boolean isJavaContainer(Object value) {
        return value instanceof Map<?, ?>
                || value instanceof Iterable<?>
                || value instanceof JsonNode node && node.isContainerNode()
                || value != null && value.getClass().isArray();
    }

    private static boolean isSupportedJavaScalar(Object value) {
        return value instanceof String
                || value instanceof Boolean
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigInteger
                || value instanceof BigDecimal;
    }

    private static void validateFiniteNumber(Object value, String parameterName) {
        if (value instanceof Double doubleValue && !Double.isFinite(doubleValue)
                || value instanceof Float floatValue && !Float.isFinite(floatValue)) {
            throw new IllegalArgumentException(
                    parameterName + " contains a non-finite JSON number");
        }
    }

    private static void validateJsonNodeType(JsonNode node, String parameterName) {
        if (node.isPojo() || node.isMissingNode()) {
            throw new IllegalArgumentException(
                    parameterName + " contains unsupported JSON node type "
                            + node.getNodeType());
        }
        if (node.isContainerNode()
                && !(node instanceof ObjectNode)
                && !(node instanceof ArrayNode)) {
            throw new IllegalArgumentException(
                    parameterName + " contains unsupported JSON container type "
                            + node.getClass().getName());
        }
        if (!node.isContainerNode() && !JsonNodeCopies.isSupportedScalar(node)) {
            throw new IllegalArgumentException(
                    parameterName + " contains unsupported JSON scalar type "
                            + node.getClass().getName());
        }
    }

    private void collectFields(
            String parentPointer,
            JsonNode previous,
            JsonNode supplied,
            JsonNode effective,
            Map<String, FieldComparison> result) {
        Deque<FieldToCollect> pending = new ArrayDeque<>();
        scheduleChildFields(parentPointer, previous, supplied, effective, pending);

        while (!pending.isEmpty()) {
            FieldToCollect field = pending.pop();
            if (result.size() >= options.maxFieldCount()) {
                throw new IllegalArgumentException(
                        "comparison exceeds maxFieldCount " + options.maxFieldCount());
            }
            result.put(field.pointer(), new FieldComparison(
                    field.pointer(),
                    field.previous(),
                    field.supplied(),
                    field.effective()));
            scheduleChildFields(
                    field.pointer(),
                    field.previous(),
                    field.supplied(),
                    field.effective(),
                    pending);
        }
    }

    private static void scheduleChildFields(
            String parentPointer,
            JsonNode previous,
            JsonNode supplied,
            JsonNode effective,
            Deque<FieldToCollect> pending) {
        TreeSet<String> names = new TreeSet<>();
        addFieldNames(previous, names);
        addFieldNames(supplied, names);
        addFieldNames(effective, names);

        for (String name : names.descendingSet()) {
            String pointer = parentPointer + '/' + escapePointerToken(name);
            JsonNode previousChild = child(previous, name);
            JsonNode suppliedChild = child(supplied, name);
            JsonNode effectiveChild = child(effective, name);
            pending.push(new FieldToCollect(
                    pointer,
                    previousChild,
                    suppliedChild,
                    effectiveChild));
        }
    }

    private static void addFieldNames(JsonNode node, Set<String> names) {
        if (node.isObject()) {
            node.properties().forEach(entry -> names.add(entry.getKey()));
        }
    }

    private static JsonNode child(JsonNode node, String name) {
        return node.isObject() ? node.path(name) : MissingNode.getInstance();
    }

    private static String escapePointerToken(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }

    private record OverlayTask(ObjectNode target, ObjectNode supplied) {
    }

    private record NodeVisit(JsonNode node, int depth, boolean exiting) {
        private static NodeVisit enter(JsonNode node, int depth) {
            return new NodeVisit(node, depth, false);
        }

        private static NodeVisit exit(JsonNode node, int depth) {
            return new NodeVisit(node, depth, true);
        }
    }

    private record JavaValueVisit(Object value, int depth, boolean exiting) {
        private static JavaValueVisit enter(Object value, int depth) {
            return new JavaValueVisit(value, depth, false);
        }

        private static JavaValueVisit exit(Object value, int depth) {
            return new JavaValueVisit(value, depth, true);
        }
    }

    private record FieldToCollect(
            String pointer,
            JsonNode previous,
            JsonNode supplied,
            JsonNode effective) {
    }

    /** Fluent builder for a configured, reusable analyzer. */
    public static final class Builder {
        private ObjectMapper mapper = DEFAULT_MAPPER;
        private ComparisonOptions comparisonOptions = ComparisonOptions.defaults();
        private JsonParsingOptions parsingOptions = JsonParsingOptions.defaults();

        private Builder() {
        }

        public Builder mapper(ObjectMapper mapper) {
            this.mapper = Objects.requireNonNull(mapper, "mapper");
            return this;
        }

        public Builder comparisonOptions(ComparisonOptions comparisonOptions) {
            this.comparisonOptions = Objects.requireNonNull(
                    comparisonOptions, "comparisonOptions");
            return this;
        }

        public Builder parsingOptions(JsonParsingOptions parsingOptions) {
            this.parsingOptions = Objects.requireNonNull(parsingOptions, "parsingOptions");
            return this;
        }

        public JsonFieldPresence build() {
            return new JsonFieldPresence(mapper, comparisonOptions, parsingOptions);
        }
    }
}
