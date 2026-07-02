package io.github.alaptseu.jsonpresence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonFieldPresenceGeneratedTest {
    private static final long SEED = 0x5EED_C0DEL;
    private static final int CASES = 250;
    private static final String[] KEYS = {
            "alpha", "beta", "gamma", "a/b", "til~de", "", "space key", "unicode-π"
    };

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void allInputFormsProduceEquivalentReportsForGeneratedDocuments() throws Exception {
        Random random = new Random(SEED);
        List<ComparisonOptions> optionSets = List.of(
                ComparisonOptions.defaults(),
                ComparisonOptions.builder().nullPolicy(NullPolicy.REMOVE_FIELD).build(),
                ComparisonOptions.builder().objectPolicy(ObjectPolicy.REPLACE).build(),
                ComparisonOptions.builder()
                        .nullPolicy(NullPolicy.REMOVE_FIELD)
                        .objectPolicy(ObjectPolicy.REPLACE)
                        .build());

        for (int caseNumber = 0; caseNumber < CASES; caseNumber++) {
            ObjectNode previous = randomObject(random, 0);
            ObjectNode supplied = randomObject(random, 0);
            Map<String, Object> previousMap = mapper.convertValue(
                    previous, new TypeReference<>() { });
            Map<String, Object> suppliedMap = mapper.convertValue(
                    supplied, new TypeReference<>() { });

            for (ComparisonOptions options : optionSets) {
                JsonComparisonReport fromStrings = JsonFieldPresence.compareJson(
                        mapper.writeValueAsString(previous),
                        mapper.writeValueAsString(supplied),
                        options);
                JsonComparisonReport fromTrees = JsonFieldPresence.compareTrees(
                        previous,
                        supplied,
                        options);
                JsonComparisonReport fromMaps = JsonFieldPresence.compareMaps(
                        previousMap,
                        suppliedMap,
                        options);

                assertEquivalent(fromStrings, fromTrees, caseNumber, options);
                assertEquivalent(fromStrings, fromMaps, caseNumber, options);
            }
        }
    }

    private static void assertEquivalent(
            JsonComparisonReport expected,
            JsonComparisonReport actual,
            int caseNumber,
            ComparisonOptions options) {
        String context = "generated case " + caseNumber + " with " + options;
        assertEquals(expected.previousJson(), actual.previousJson(), context);
        assertEquals(expected.suppliedJson(), actual.suppliedJson(), context);
        assertEquals(expected.effectiveJson(), actual.effectiveJson(), context);
        assertEquals(expected.fields().keySet(), actual.fields().keySet(), context);
        assertEquals(expected.changedFields().keySet(), actual.changedFields().keySet(), context);

        expected.fields().forEach((pointer, expectedField) -> {
            FieldComparison actualField = actual.field(pointer);
            assertEquals(expectedField.previousState(), actualField.previousState(), context);
            assertEquals(expectedField.suppliedState(), actualField.suppliedState(), context);
            assertEquals(expectedField.effectiveState(), actualField.effectiveState(), context);
            assertEquals(expectedField.previousValue(), actualField.previousValue(), context);
            assertEquals(expectedField.suppliedValue(), actualField.suppliedValue(), context);
            assertEquals(expectedField.effectiveValue(), actualField.effectiveValue(), context);
            assertEquals(expectedField.isChanged(), actualField.isChanged(), context);
        });
    }

    private ObjectNode randomObject(Random random, int depth) {
        ObjectNode object = mapper.createObjectNode();
        int fieldCount = random.nextInt(5);
        for (int index = 0; index < fieldCount; index++) {
            String key = KEYS[random.nextInt(KEYS.length)];
            object.set(key, randomValue(random, depth));
        }
        return object;
    }

    private JsonNode randomValue(Random random, int depth) {
        int kind = random.nextInt(depth < 3 ? 7 : 5);
        return switch (kind) {
            case 0 -> mapper.getNodeFactory().nullNode();
            case 1 -> mapper.getNodeFactory().textNode("value-" + random.nextInt(100));
            case 2 -> mapper.getNodeFactory().numberNode(random.nextInt(200) - 100);
            case 3 -> mapper.getNodeFactory().numberNode(random.nextDouble() * 20 - 10);
            case 4 -> mapper.getNodeFactory().booleanNode(random.nextBoolean());
            case 5 -> randomObject(random, depth + 1);
            default -> randomArray(random, depth + 1);
        };
    }

    private ArrayNode randomArray(Random random, int depth) {
        ArrayNode array = mapper.createArrayNode();
        int size = random.nextInt(5);
        for (int index = 0; index < size; index++) {
            array.add(randomValue(random, depth));
        }
        return array;
    }
}
