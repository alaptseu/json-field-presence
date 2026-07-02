package io.github.alaptseu.jsonpresence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BigIntegerNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.FloatNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ShortNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Iterative defensive copies for validated Jackson trees. */
final class JsonNodeCopies {
    private static final Set<Class<?>> IMMUTABLE_SCALAR_TYPES = Set.of(
            BigIntegerNode.class,
            BooleanNode.class,
            DecimalNode.class,
            DoubleNode.class,
            FloatNode.class,
            IntNode.class,
            LongNode.class,
            NullNode.class,
            ShortNode.class,
            TextNode.class);

    private JsonNodeCopies() {
    }

    static ObjectNode copyObject(ObjectNode source) {
        return (ObjectNode) copy(source);
    }

    static JsonNode copy(JsonNode source) {
        Objects.requireNonNull(source, "source");
        if (!source.isContainerNode()) {
            return copyScalar(source);
        }

        ContainerNode<?> target = emptyContainer(source);
        Deque<CopyTask> pending = new ArrayDeque<>();
        pending.push(new CopyTask((ContainerNode<?>) source, target));

        while (!pending.isEmpty()) {
            CopyTask task = pending.pop();
            if (task.source() instanceof ObjectNode sourceObject) {
                ObjectNode targetObject = (ObjectNode) task.target();
                for (Map.Entry<String, JsonNode> field : sourceObject.properties()) {
                    JsonNode child = field.getValue();
                    JsonNode childCopy = copyValueOrCreateContainer(child, pending);
                    targetObject.set(field.getKey(), childCopy);
                }
            } else {
                ArrayNode sourceArray = (ArrayNode) task.source();
                ArrayNode targetArray = (ArrayNode) task.target();
                for (JsonNode child : sourceArray) {
                    targetArray.add(copyValueOrCreateContainer(child, pending));
                }
            }
        }
        return target;
    }

    private static JsonNode copyValueOrCreateContainer(
            JsonNode source,
            Deque<CopyTask> pending) {
        if (!source.isContainerNode()) {
            return copyScalar(source);
        }

        ContainerNode<?> target = emptyContainer(source);
        pending.push(new CopyTask((ContainerNode<?>) source, target));
        return target;
    }

    static boolean isSupportedScalar(JsonNode source) {
        Class<?> sourceType = source.getClass();
        return sourceType == BinaryNode.class || IMMUTABLE_SCALAR_TYPES.contains(sourceType);
    }

    private static JsonNode copyScalar(JsonNode source) {
        if (source.getClass() == BinaryNode.class) {
            byte[] value = ((BinaryNode) source).binaryValue();
            return new BinaryNode(value == null ? null : value.clone());
        }
        if (IMMUTABLE_SCALAR_TYPES.contains(source.getClass())) {
            return source;
        }
        throw new IllegalArgumentException(
                "Unsupported JSON scalar type: " + source.getClass().getName());
    }

    private static ContainerNode<?> emptyContainer(JsonNode source) {
        if (source instanceof ObjectNode object) {
            return object.objectNode();
        }
        if (source instanceof ArrayNode array) {
            return array.arrayNode();
        }
        throw new IllegalArgumentException(
                "Unsupported JSON container type: " + source.getClass().getName());
    }

    private record CopyTask(ContainerNode<?> source, ContainerNode<?> target) {
    }
}
