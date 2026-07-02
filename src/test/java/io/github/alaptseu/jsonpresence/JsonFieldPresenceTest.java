package io.github.alaptseu.jsonpresence;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BinaryNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFieldPresenceTest {

    @Test
    void distinguishesOmittedNullAndValueFields() {
        JsonComparisonReport report = JsonFieldPresence.compareJson(
                """
                {"name":"Ada","nickname":"A","age":36}
                """,
                """
                {"nickname":null,"age":37}
                """);

        FieldComparison name = report.field("/name");
        assertEquals(FieldState.ABSENT, name.suppliedState());
        assertTrue(name.wasOmitted());
        assertFalse(name.isChanged());
        assertEquals("Ada", name.effectiveValue().orElseThrow().textValue());

        FieldComparison nickname = report.field("/nickname");
        assertEquals(FieldState.NULL, nickname.suppliedState());
        assertTrue(nickname.wasSupplied());
        assertTrue(nickname.isExplicitNull());
        assertTrue(nickname.isChanged());
        assertTrue(nickname.effectiveValue().orElseThrow().isNull());

        FieldComparison age = report.field("/age");
        assertEquals(FieldState.VALUE, age.suppliedState());
        assertEquals(37, age.effectiveValue().orElseThrow().intValue());
        assertTrue(age.isChanged());
    }

    @Test
    void explicitNullRetainsIntentEvenWhenValueWasAlreadyNull() {
        JsonComparisonReport report = JsonFieldPresence.compareJson(
                "{\"nickname\":null}",
                "{\"nickname\":null}");

        FieldComparison comparison = report.field("/nickname");
        assertTrue(comparison.isExplicitNull());
        assertFalse(comparison.isChanged());
    }

    @Test
    void canRemoveFieldsWhenExplicitNullUsesRemovePolicy() {
        ComparisonOptions options = ComparisonOptions.builder()
                .nullPolicy(NullPolicy.REMOVE_FIELD)
                .build();

        JsonComparisonReport report = JsonFieldPresence.compareJson(
                "{\"name\":\"Ada\",\"phone\":\"123\"}",
                "{\"phone\":null}",
                options);

        FieldComparison phone = report.field("/phone");
        assertTrue(phone.isExplicitNull());
        assertEquals(FieldState.ABSENT, phone.effectiveState());
        assertTrue(phone.effectiveValue().isEmpty());
        assertTrue(phone.isChanged());
        assertEquals("{\"name\":\"Ada\"}", report.effectiveJsonString());
    }

    @Test
    void recursivelyOverlaysNestedObjects() {
        JsonComparisonReport report = JsonFieldPresence.compareJson(
                """
                {"profile":{"first":"Ada","last":"Lovelace","contact":{"email":"old@example.com"}}}
                """,
                """
                {"profile":{"first":null,"contact":{"email":"new@example.com"}}}
                """);

        assertTrue(report.field("/profile/first").isExplicitNull());
        assertEquals(FieldState.ABSENT, report.field("/profile/last").suppliedState());
        assertEquals("Lovelace",
                report.field("/profile/last").effectiveValue().orElseThrow().textValue());
        assertEquals("new@example.com",
                report.field("/profile/contact/email").effectiveValue().orElseThrow().textValue());
        assertEquals(
                "{\"profile\":{\"first\":null,\"last\":\"Lovelace\",\"contact\":{\"email\":\"new@example.com\"}}}",
                report.effectiveJsonString());
    }

    @Test
    void canReplaceNestedObjectsInsteadOfMergingThem() {
        ComparisonOptions options = ComparisonOptions.builder()
                .objectPolicy(ObjectPolicy.REPLACE)
                .build();

        JsonComparisonReport report = JsonFieldPresence.compareJson(
                "{\"profile\":{\"first\":\"Ada\",\"last\":\"Lovelace\"}}",
                "{\"profile\":{\"first\":\"Augusta\"}}",
                options);

        assertEquals("Augusta",
                report.field("/profile/first").effectiveValue().orElseThrow().textValue());
        FieldComparison last = report.field("/profile/last");
        assertTrue(last.wasOmitted());
        assertEquals(FieldState.ABSENT, last.effectiveState());
        assertTrue(last.isChanged());
        assertEquals("{\"profile\":{\"first\":\"Augusta\"}}",
                report.effectiveJsonString());
    }

    @Test
    void nullAndObjectPoliciesComposeAtEveryNestedLevel() {
        ComparisonOptions options = ComparisonOptions.builder()
                .nullPolicy(NullPolicy.REMOVE_FIELD)
                .objectPolicy(ObjectPolicy.REPLACE)
                .build();

        JsonComparisonReport report = JsonFieldPresence.compareJson(
                "{\"profile\":{\"first\":\"Ada\",\"last\":\"Lovelace\"}}",
                "{\"profile\":{\"first\":null,\"nested\":{\"remove\":null,\"keep\":1}}}",
                options);

        assertEquals(
                "{\"profile\":{\"nested\":{\"keep\":1}}}",
                report.effectiveJsonString());
        assertTrue(report.field("/profile/first").isExplicitNull());
        assertEquals(FieldState.ABSENT, report.field("/profile/first").effectiveState());
        assertTrue(report.field("/profile/nested/remove").isExplicitNull());
    }

    @Test
    void replacingAnAncestorCanIndirectlyRemoveAnOmittedDescendant() {
        JsonComparisonReport report = JsonFieldPresence.compareJson(
                "{\"profile\":{\"name\":\"Ada\"}}",
                "{\"profile\":null}");

        assertTrue(report.field("/profile").isExplicitNull());

        FieldComparison descendant = report.field("/profile/name");
        assertTrue(descendant.wasOmitted());
        assertEquals(FieldState.ABSENT, descendant.effectiveState());
        assertTrue(descendant.isChanged());
    }

    @Test
    void arraysAreValuesAndReplaceThePreviousArray() {
        JsonComparisonReport report = JsonFieldPresence.compareJson(
                "{\"roles\":[\"reader\",\"writer\"]}",
                "{\"roles\":[\"reader\"]}");

        assertEquals(FieldState.VALUE, report.field("/roles").suppliedState());
        assertEquals(1, report.field("/roles").effectiveValue().orElseThrow().size());
        assertTrue(report.field("/roles").isChanged());
    }

    @Test
    void reportsArrayElementsChangedThroughArrayReplacement() {
        JsonComparisonReport report = JsonFieldPresence.compareJson(
                "{\"roles\":[\"reader\",\"writer\"]}",
                "{\"roles\":[\"reader\"]}");

        FieldComparison removedElement = report.field("/roles/1");
        assertTrue(removedElement.wasOmitted());
        assertEquals("writer", removedElement.previousValue().orElseThrow().textValue());
        assertEquals(FieldState.ABSENT, removedElement.effectiveState());
        assertTrue(removedElement.isChanged());
    }

    @Test
    void arrayIndicesAreQueryableButArraysAreEnumeratedAtomically() {
        JsonComparisonReport report = JsonFieldPresence.compareJson(
                "{\"items\":[{\"name\":\"before\"}]}",
                "{\"items\":[{\"name\":\"after\"}]}");

        FieldComparison nestedArrayField = report.field("/items/0/name");
        assertEquals("before",
                nestedArrayField.previousValue().orElseThrow().textValue());
        assertEquals("after",
                nestedArrayField.effectiveValue().orElseThrow().textValue());
        assertTrue(nestedArrayField.isChanged());

        assertEquals(Set.of("/items"), report.fields().keySet());
        assertEquals(Set.of("/items"), report.changedFields().keySet());
        assertFalse(report.fields().containsKey("/items/0"));
        assertFalse(report.fields().containsKey("/items/0/name"));
    }

    @Test
    void treatsDifferentJsonNumberRepresentationsAsChanges() {
        JsonComparisonReport report = JsonFieldPresence.compareJson(
                "{\"number\":1}",
                "{\"number\":1.0}");

        assertTrue(report.field("/number").isChanged());
    }

    @Test
    void reportsAddedFieldsAndUnknownFields() {
        JsonComparisonReport report = JsonFieldPresence.compareJson("{}", "{\"newField\":5}");

        FieldComparison added = report.field("/newField");
        assertEquals(FieldState.ABSENT, added.previousState());
        assertEquals(FieldState.VALUE, added.suppliedState());
        assertTrue(added.isChanged());

        FieldComparison unknown = report.field("/unknown");
        assertEquals(FieldState.ABSENT, unknown.previousState());
        assertEquals(FieldState.ABSENT, unknown.suppliedState());
        assertEquals(FieldState.ABSENT, unknown.effectiveState());
        assertFalse(unknown.isChanged());
    }

    @Test
    void enumeratesEscapedJsonPointerPathsInDeterministicOrder() {
        JsonComparisonReport report = JsonFieldPresence.compareJson(
                "{\"a/b\":1,\"til~de\":2}",
                "{\"a/b\":null}");

        Map<String, FieldComparison> fields = report.fields();
        assertEquals(java.util.List.of("/a~1b", "/til~0de"),
                fields.keySet().stream().toList());
        assertTrue(report.field("/a~1b").isExplicitNull());
        assertEquals(2, report.field("/til~0de").effectiveValue().orElseThrow().intValue());
    }

    @Test
    void returnsOnlyActuallyChangedPaths() {
        JsonComparisonReport report = JsonFieldPresence.compareJson(
                "{\"same\":1,\"omitted\":2,\"changed\":3}",
                "{\"same\":1,\"changed\":4}");

        assertEquals(java.util.Set.of("/changed"), report.changedFields().keySet());
    }

    @Test
    void cachesEnumeratedAndChangedFieldReports() {
        JsonComparisonReport report = JsonFieldPresence.compareJson(
                "{\"same\":1,\"changed\":2}",
                "{\"changed\":3}");

        assertSame(report.fields(), report.fields());
        assertSame(report.changedFields(), report.changedFields());
    }

    @Test
    void returnedJsonValuesAreDefensiveCopies() {
        JsonComparisonReport report = JsonFieldPresence.compareJson(
                "{\"profile\":{\"name\":\"Ada\"}}",
                "{}");

        ObjectNode returnedField = (ObjectNode) report.field("/profile")
                .effectiveValue().orElseThrow();
        returnedField.put("name", "Changed");

        ObjectNode returnedDocument = (ObjectNode) report.effectiveJson();
        returnedDocument.withObject("/profile").put("name", "Also changed");

        assertEquals("Ada", report.field("/profile/name")
                .effectiveValue().orElseThrow().textValue());
    }

    @Test
    void binaryNodesAreDefensivelyCopiedAtEveryPublicBoundary() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        byte[] previousBytes = {1, 2, 3};
        byte[] suppliedBytes = {4, 5, 6};
        ObjectNode previous = mapper.createObjectNode();
        previous.set("binary", BinaryNode.valueOf(previousBytes));
        ObjectNode supplied = mapper.createObjectNode();
        supplied.set("binary", BinaryNode.valueOf(suppliedBytes));

        JsonComparisonReport report = JsonFieldPresence.compareTrees(previous, supplied);
        previousBytes[0] = 9;
        suppliedBytes[0] = 9;

        assertArrayEquals(new byte[]{1, 2, 3},
                report.field("/binary").previousValue().orElseThrow().binaryValue());
        assertArrayEquals(new byte[]{4, 5, 6},
                report.field("/binary").effectiveValue().orElseThrow().binaryValue());

        byte[] returnedField = report.field("/binary")
                .effectiveValue().orElseThrow().binaryValue();
        returnedField[1] = 9;
        byte[] returnedDocument = report.effectiveJson().path("binary").binaryValue();
        returnedDocument[2] = 9;

        assertArrayEquals(new byte[]{4, 5, 6},
                report.field("/binary").effectiveValue().orElseThrow().binaryValue());
        assertArrayEquals(new byte[]{4, 5, 6},
                report.effectiveJson().path("binary").binaryValue());
    }

    @Test
    void supportsPreParsedTreesAndDoesNotMutateInputs() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode previous = (ObjectNode) mapper.readTree("{\"name\":\"Ada\"}");
        ObjectNode supplied = (ObjectNode) mapper.readTree("{\"name\":null}");

        JsonComparisonReport report = JsonFieldPresence.compareTrees(previous, supplied);

        assertEquals("Ada", previous.get("name").textValue());
        assertTrue(supplied.get("name").isNull());
        assertTrue(report.field("/name").effectiveValue().orElseThrow().isNull());

        previous.put("name", "Changed later");
        supplied.put("name", "Changed later");
        assertEquals("Ada", report.field("/name").previousValue().orElseThrow().textValue());
        assertTrue(report.field("/name").suppliedValue().orElseThrow().isNull());
    }

    @Test
    void supportsJavaMapsAsJsonObjects() {
        Map<String, Object> previous = new LinkedHashMap<>();
        previous.put("name", "Ada");
        previous.put("phone", "123");
        previous.put("address", Map.of("city", "London", "country", "UK"));

        Map<String, Object> supplied = new LinkedHashMap<>();
        supplied.put("name", null);
        supplied.put("address", Map.of("city", "Sydney"));

        JsonComparisonReport report = JsonFieldPresence.compareMaps(previous, supplied);

        assertTrue(report.field("/name").isExplicitNull());
        assertTrue(report.field("/phone").wasOmitted());
        assertEquals("123", report.field("/phone").effectiveValue().orElseThrow().textValue());
        assertEquals("Sydney",
                report.field("/address/city").effectiveValue().orElseThrow().textValue());
        assertEquals("UK",
                report.field("/address/country").effectiveValue().orElseThrow().textValue());
    }

    @Test
    void supportsComparisonOptionsWithJavaMaps() {
        Map<String, Object> supplied = new LinkedHashMap<>();
        supplied.put("phone", null);
        ComparisonOptions options = ComparisonOptions.builder()
                .nullPolicy(NullPolicy.REMOVE_FIELD)
                .build();

        JsonComparisonReport report = JsonFieldPresence.compareMaps(
                Map.of("phone", "123"),
                supplied,
                options);

        assertEquals(FieldState.NULL, report.field("/phone").suppliedState());
        assertEquals(FieldState.ABSENT, report.field("/phone").effectiveState());
    }

    @Test
    void preflightsEverySupportedJavaContainerShape() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode embeddedObject = mapper.createObjectNode().put("value", 1);
        ArrayNode embeddedArray = mapper.createArrayNode().add(
                mapper.createObjectNode().put("value", 2));
        Map<String, Object> previous = new LinkedHashMap<>();
        previous.put("tree", embeddedObject);
        previous.put("treeArray", embeddedArray);
        previous.put("iterable", List.of(Map.of("value", 3)));
        previous.put("array", new Object[]{Map.of("value", 4)});

        JsonComparisonReport report = JsonFieldPresence.compareMaps(previous, Map.of());

        assertEquals(1, report.field("/tree/value").effectiveValue().orElseThrow().intValue());
        assertEquals(2,
                report.field("/treeArray/0/value").effectiveValue().orElseThrow().intValue());
        assertEquals(3,
                report.field("/iterable/0/value").effectiveValue().orElseThrow().intValue());
        assertEquals(4,
                report.field("/array/0/value").effectiveValue().orElseThrow().intValue());
    }

    @Test
    void rejectsNonJsonNativeJavaValuesBeforeJacksonConversion() {
        record SerializablePojo(String value) {
        }

        IllegalArgumentException pojoException = assertThrows(
                IllegalArgumentException.class,
                () -> JsonFieldPresence.compareMaps(
                        Map.of("value", new SerializablePojo("serializable by Jackson")),
                        Map.of()));
        assertTrue(pojoException.getMessage().contains(
                "unsupported Java value type"));

        Map<Object, Object> invalidNestedMap = new LinkedHashMap<>();
        invalidNestedMap.put(1, "value");
        IllegalArgumentException keyException = assertThrows(
                IllegalArgumentException.class,
                () -> JsonFieldPresence.compareMaps(
                        Map.of("nested", invalidNestedMap),
                        Map.of()));
        assertTrue(keyException.getMessage().contains("non-string map key"));

        IllegalArgumentException numberException = assertThrows(
                IllegalArgumentException.class,
                () -> JsonFieldPresence.compareMaps(
                        Map.of("value", Double.NaN),
                        Map.of()));
        assertTrue(numberException.getMessage().contains("non-finite JSON number"));
    }

    @Test
    void rejectsEmbeddedPojoAndMissingJsonNodes() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode withPojo = mapper.createObjectNode();
        withPojo.putPOJO("value", new Object());

        IllegalArgumentException pojoException = assertThrows(
                IllegalArgumentException.class,
                () -> JsonFieldPresence.compareTrees(
                        withPojo,
                        mapper.createObjectNode()));
        assertTrue(pojoException.getMessage().contains("JSON node type POJO"));

        ObjectNode withMissingNode = mapper.createObjectNode();
        withMissingNode.set("value", MissingNode.getInstance());
        IllegalArgumentException missingException = assertThrows(
                IllegalArgumentException.class,
                () -> JsonFieldPresence.compareTrees(
                        withMissingNode,
                        mapper.createObjectNode()));
        assertTrue(missingException.getMessage().contains("JSON node type MISSING"));
    }

    @Test
    void rejectsUnknownScalarNodeSubclasses() {
        ObjectMapper mapper = new ObjectMapper();
        TextNode customScalar = new TextNode("value") {
        };
        ObjectNode previous = mapper.createObjectNode();
        previous.set("value", customScalar);

        IllegalArgumentException treeException = assertThrows(
                IllegalArgumentException.class,
                () -> JsonFieldPresence.compareTrees(
                        previous,
                        mapper.createObjectNode()));
        assertTrue(treeException.getMessage().contains(
                "unsupported JSON scalar type"));

        IllegalArgumentException mapException = assertThrows(
                IllegalArgumentException.class,
                () -> JsonFieldPresence.compareMaps(
                        Map.of("value", customScalar),
                        Map.of()));
        assertTrue(mapException.getMessage().contains(
                "unsupported JSON scalar type"));
    }

    @Test
    void rejectsInvalidJsonAndNonObjectDocuments() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareJson("{broken", "{}"));
        assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareJson("[]", "{}"));
        assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareJson("{}", "null"));
        assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareJson(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareTrees(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareMaps(null, null));

        Map<String, Object> unsupported = Map.of("value", new Object());
        IllegalArgumentException conversionException = assertThrows(
                IllegalArgumentException.class,
                () -> JsonFieldPresence.compareMaps(unsupported, Map.of()));
        assertTrue(conversionException.getMessage().contains(
                "previousJson contains unsupported Java value type"));
    }

    @Test
    void defaultParserRejectsDuplicateObjectKeys() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareJson("{}", "{\"name\":1,\"name\":2}"));

        assertTrue(exception.getMessage().contains("suppliedJson is not valid JSON"));
    }

    @Test
    void rejectsDocumentsBeyondConfiguredDepth() {
        ComparisonOptions options = ComparisonOptions.builder()
                .maxDepth(1)
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareJson("{\"nested\":{\"value\":1}}", "{}", options));

        assertTrue(exception.getMessage().contains("previousJson exceeds maxDepth 1"));
    }

    @Test
    void depthLimitAlsoCoversArraysInPreParsedTrees() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var previous = mapper.readTree("{\"items\":[[1]]}");
        ComparisonOptions options = ComparisonOptions.builder()
                .maxDepth(1)
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareTrees(
                        previous,
                        mapper.createObjectNode(),
                        options));
    }

    @Test
    void rejectsDocumentsBeyondConfiguredNodeCount() {
        ComparisonOptions options = ComparisonOptions.builder()
                .maxNodes(2)
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareJson("{\"first\":1,\"second\":2}", "{}", options));

        assertTrue(exception.getMessage().contains("previousJson exceeds maxNodes 2"));
    }

    @Test
    void rejectsTokenLimitOverflowBeforeMaterializingATree() {
        assertPreflightRejectsBeforeTree(
                "{\"first\":1,\"second\":2}",
                ComparisonOptions.builder().maxNodes(2).build(),
                "previousJson exceeds maxNodes 2");
        assertPreflightRejectsBeforeTree(
                "{\"first\":1,\"second\":2}",
                ComparisonOptions.builder().maxFieldCount(1).build(),
                "previousJson exceeds maxFieldCount 1");
        assertPreflightRejectsBeforeTree(
                "{\"value\":1}",
                ComparisonOptions.builder().maxDepth(0).build(),
                "previousJson exceeds maxDepth 0");
    }

    @Test
    void enforcesDocumentLengthBeforeTokenizing() {
        JsonFieldPresence analyzer = JsonFieldPresence.builder()
                .parsingOptions(JsonParsingOptions.builder()
                        .maxDocumentLength(8)
                        .build())
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> analyzer.analyzeJson("{\"value\":1}", "{}"));

        assertTrue(exception.getMessage().contains(
                "previousJson exceeds maxDocumentLength 8"));
    }

    @Test
    void enforcesStringNumberAndPropertyNameLimitsDuringTokenization() {
        JsonFieldPresence stringAnalyzer = JsonFieldPresence.builder()
                .parsingOptions(JsonParsingOptions.builder()
                        .maxStringLength(3)
                        .build())
                .build();
        JsonFieldPresence numberAnalyzer = JsonFieldPresence.builder()
                .parsingOptions(JsonParsingOptions.builder()
                        .maxNumberLength(3)
                        .build())
                .build();
        JsonFieldPresence nameAnalyzer = JsonFieldPresence.builder()
                .parsingOptions(JsonParsingOptions.builder()
                        .maxPropertyNameLength(3)
                        .build())
                .build();

        assertParsingLimitExceeded(stringAnalyzer, "{\"v\":\"four\"}");
        assertParsingLimitExceeded(numberAnalyzer, "{\"v\":1234}");
        assertParsingLimitExceeded(nameAnalyzer, "{\"name\":1}");
    }

    @Test
    void preservesStricterConstraintsFromCustomMapper() {
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(2)
                        .build())
                .build();
        ObjectMapper mapper = new ObjectMapper(factory);
        JsonFieldPresence analyzer = JsonFieldPresence.builder()
                .mapper(mapper)
                .parsingOptions(JsonParsingOptions.builder()
                        .maxStringLength(100)
                        .build())
                .build();

        assertParsingLimitExceeded(analyzer, "{\"v\":\"abc\"}");
    }

    @Test
    void rejectsJavaContainersBeyondConfiguredNodeCountBeforeConversion() {
        ComparisonOptions options = ComparisonOptions.builder()
                .maxNodes(1)
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareMaps(Map.of("value", 1), Map.of(), options));

        assertTrue(exception.getMessage().contains("previousJson exceeds maxNodes 1"));
    }

    @Test
    void rejectsDocumentsBeyondConfiguredFieldCount() {
        ComparisonOptions options = ComparisonOptions.builder()
                .maxFieldCount(2)
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareJson(
                        "{\"first\":1,\"nested\":{\"second\":2}}",
                        "{}",
                        options));

        assertTrue(exception.getMessage().contains(
                "previousJson exceeds maxFieldCount 2"));
    }

    @Test
    void fieldCountLimitCoversPreParsedTreesAndJavaMaps() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode previousTree = mapper.createObjectNode();
        previousTree.put("first", 1);
        previousTree.put("second", 2);
        ComparisonOptions options = ComparisonOptions.builder()
                .maxFieldCount(1)
                .build();

        IllegalArgumentException treeException = assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareTrees(
                        previousTree,
                        mapper.createObjectNode(),
                        options));
        assertTrue(treeException.getMessage().contains("maxFieldCount 1"));

        IllegalArgumentException mapException = assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareMaps(
                        Map.of("first", 1, "second", 2),
                        Map.of(),
                        options));
        assertTrue(mapException.getMessage().contains("maxFieldCount 1"));
    }

    @Test
    void rejectsCombinedReportBeyondConfiguredFieldCount() {
        ComparisonOptions options = ComparisonOptions.builder()
                .objectPolicy(ObjectPolicy.REPLACE)
                .maxFieldCount(2)
                .build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareJson(
                        "{\"profile\":{\"previous\":1}}",
                        "{\"profile\":{\"supplied\":2}}",
                        options));

        assertTrue(exception.getMessage().contains(
                "comparison exceeds maxFieldCount 2"));
    }

    @Test
    void acceptsDocumentsExactlyAtConfiguredLimits() {
        ComparisonOptions options = ComparisonOptions.builder()
                .maxDepth(1)
                .maxNodes(2)
                .maxFieldCount(1)
                .build();

        JsonComparisonReport report = JsonFieldPresence.compareJson(
                "{\"value\":1}",
                "{}",
                options);

        assertEquals(1, report.field("/value").effectiveValue().orElseThrow().intValue());
    }

    @Test
    void supportsTheHardDepthCeilingWithoutRecursiveCopies() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode previous = mapper.createObjectNode();
        ObjectNode cursor = previous;
        for (int depth = 0; depth < ComparisonOptions.MAX_SUPPORTED_DEPTH; depth++) {
            ObjectNode child = mapper.createObjectNode();
            cursor.set("nested", child);
            cursor = child;
        }
        ComparisonOptions options = ComparisonOptions.builder()
                .maxDepth(ComparisonOptions.MAX_SUPPORTED_DEPTH)
                .build();

        JsonComparisonReport report = JsonFieldPresence.compareTrees(
                previous,
                mapper.createObjectNode(),
                options);

        assertEquals(previous, report.effectiveJson());
    }

    @Test
    void rejectsVeryDeepPreParsedTreeWithoutRecursiveTraversal() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode previous = mapper.createObjectNode();
        ObjectNode cursor = previous;
        for (int depth = 0; depth < 10_000; depth++) {
            ObjectNode child = mapper.createObjectNode();
            cursor.set("nested", child);
            cursor = child;
        }

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareTrees(previous, mapper.createObjectNode()));

        assertTrue(exception.getMessage().contains("previousJson exceeds maxDepth 100"));
    }

    @Test
    void rejectsVeryDeepJavaMapBeforeJacksonConversion() {
        Map<String, Object> previous = new LinkedHashMap<>();
        Map<String, Object> cursor = previous;
        for (int depth = 0; depth < 10_000; depth++) {
            Map<String, Object> child = new LinkedHashMap<>();
            cursor.put("nested", child);
            cursor = child;
        }

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareMaps(previous, Map.of()));

        assertTrue(exception.getMessage().contains("previousJson exceeds maxDepth 100"));
    }

    @Test
    void rejectsCyclicPreParsedAndJavaMapInputs() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode cyclicTree = mapper.createObjectNode();
        cyclicTree.set("self", cyclicTree);

        IllegalArgumentException treeException = assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareTrees(cyclicTree, mapper.createObjectNode()));
        assertTrue(treeException.getMessage().contains("cyclic JSON structure"));

        Map<String, Object> cyclicMap = new LinkedHashMap<>();
        cyclicMap.put("self", cyclicMap);
        IllegalArgumentException mapException = assertThrows(IllegalArgumentException.class,
                () -> JsonFieldPresence.compareMaps(cyclicMap, Map.of()));
        assertTrue(mapException.getMessage().contains("cyclic Java container"));
    }

    @Test
    void handlesLargeFlatDocumentsWithinDefaultLimits() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode previous = mapper.createObjectNode();
        for (int index = 0; index < 5_000; index++) {
            previous.put("field" + index, index);
        }

        JsonComparisonReport report = JsonFieldPresence.compareTrees(
                previous,
                mapper.createObjectNode());

        assertEquals(5_000, report.fields().size());
        assertEquals(4_999,
                report.field("/field4999").effectiveValue().orElseThrow().intValue());
    }

    @Test
    void validatesComparisonOptionRanges() {
        assertThrows(IllegalArgumentException.class,
                () -> ComparisonOptions.builder().maxDepth(-1).build());
        assertThrows(IllegalArgumentException.class,
                () -> ComparisonOptions.builder()
                        .maxDepth(ComparisonOptions.MAX_SUPPORTED_DEPTH + 1)
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> ComparisonOptions.builder().maxNodes(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> ComparisonOptions.builder().maxFieldCount(-1).build());

        ComparisonOptions options = ComparisonOptions.builder()
                .maxDepth(7)
                .maxNodes(20)
                .maxFieldCount(10)
                .build();
        assertEquals(options, options.toBuilder().build());

        @SuppressWarnings("deprecation")
        ComparisonOptions legacyOptions = new ComparisonOptions(
                NullPolicy.SET_NULL,
                ObjectPolicy.MERGE,
                7,
                20);
        assertEquals(ComparisonOptions.DEFAULT_MAX_FIELD_COUNT,
                legacyOptions.maxFieldCount());
    }

    @Test
    void validatesJsonParsingOptionRanges() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonParsingOptions.builder().maxDocumentLength(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> JsonParsingOptions.builder().maxStringLength(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> JsonParsingOptions.builder().maxNumberLength(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> JsonParsingOptions.builder().maxPropertyNameLength(0).build());

        JsonParsingOptions options = JsonParsingOptions.builder()
                .maxDocumentLength(1_000)
                .maxStringLength(100)
                .maxNumberLength(20)
                .maxPropertyNameLength(50)
                .build();
        assertEquals(options, options.toBuilder().build());
    }

    @Test
    void acceptsACustomMapperWithDefaultOptions() {
        JsonComparisonReport report = new JsonFieldPresence(new ObjectMapper())
                .analyzeJson("{\"value\":1}", "{}");

        assertEquals(1, report.field("/value").effectiveValue().orElseThrow().intValue());
    }

    @Test
    void rejectsInvalidJsonPointer() {
        JsonComparisonReport report = JsonFieldPresence.compareJson("{}", "{}");
        assertThrows(IllegalArgumentException.class, () -> report.field("not-a-pointer"));
    }

    private static void assertParsingLimitExceeded(
            JsonFieldPresence analyzer,
            String previousJson) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> analyzer.analyzeJson(previousJson, "{}"));
        assertTrue(exception.getMessage().contains(
                "previousJson exceeds configured JSON parsing limits"));
    }

    private static void assertPreflightRejectsBeforeTree(
            String previousJson,
            ComparisonOptions options,
            String expectedMessage) {
        TrackingObjectMapper mapper = new TrackingObjectMapper();
        JsonFieldPresence analyzer = JsonFieldPresence.builder()
                .mapper(mapper)
                .comparisonOptions(options)
                .build();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> analyzer.analyzeJson(previousJson, "{}"));

        assertTrue(exception.getMessage().contains(expectedMessage));
        assertFalse(mapper.readTreeCalled);
    }

    private static final class TrackingObjectMapper extends ObjectMapper {
        private boolean readTreeCalled;

        @Override
        public <T extends TreeNode> T readTree(JsonParser parser) throws IOException {
            readTreeCalled = true;
            return super.readTree(parser);
        }
    }
}
