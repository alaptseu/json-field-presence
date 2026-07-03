# Migrating from 1.x to 2.0

Version 2.0 removes ambiguous overloads and rejects Java values that cannot be
represented safely as JSON. The Maven coordinates remain
`io.github.alaptseu:json-field-presence`; update the dependency version to
`2.0.0` and apply the source changes below.

## Rename comparison entry points

Replace each overloaded static call according to its input representation:

| 1.x | 2.0 |
| --- | --- |
| `compare(String, String)` | `compareJson(String, String)` |
| `compare(JsonNode, JsonNode)` | `compareTrees(JsonNode, JsonNode)` |
| `compare(Map, Map)` | `compareMaps(Map, Map)` |

The overloads that accept `ComparisonOptions` use the same new method names.
For example:

```java
// 1.x
JsonComparisonReport report = JsonFieldPresence.compare(previous, request);

// 2.0, for JSON strings
JsonComparisonReport report = JsonFieldPresence.compareJson(previous, request);
```

For configured analyzer instances, rename methods in the same way:

| 1.x | 2.0 |
| --- | --- |
| `analyze(String, String)` | `analyzeJson(String, String)` |
| `analyze(JsonNode, JsonNode)` | `analyzeTrees(JsonNode, JsonNode)` |
| `analyze(Map, Map)` | `analyzeMaps(Map, Map)` |

Distinct names make calls such as `compareJson(null, null)` unambiguous at
compile time. Null documents are still rejected at runtime.

## Convert POJO map values before comparison

`compareMaps` now accepts only JSON-native values: `null`, strings, booleans,
finite standard Java numbers, maps with string keys, iterables, arrays, and
Jackson `JsonNode` values. It rejects arbitrary POJOs, non-string map keys,
non-finite numbers, `POJONode`, and `MissingNode`.

If a map previously contained application objects, convert the complete input
to a Jackson object tree and use `compareTrees`:

```java
ObjectMapper mapper = new ObjectMapper();
JsonNode previousTree = mapper.valueToTree(previousObject);
JsonNode requestTree = mapper.valueToTree(requestObject);

JsonComparisonReport report =
        JsonFieldPresence.compareTrees(previousTree, requestTree);
```

Perform this conversion only for trusted application objects. For untrusted
requests, parse constrained JSON or construct JSON-native map values directly.

## Review input limits

Every comparison now enforces depth, node-count, and object-field limits before
copying or overlaying input. Defaults are depth 100, 100,000 nodes, and 100,000
fields. Customize them with `ComparisonOptions.builder()` if the application
needs tighter limits. `maxDepth` cannot exceed 256.

## Account for array enumeration semantics

Arrays remain replacement values. `report.fields()` and
`report.changedFields()` enumerate the property containing an array but do not
enumerate array indices. Direct JSON Pointer lookup still supports array paths,
for example `report.field("/items/0/name")`.

## Verify the migration

Run the complete repository build with the pinned Maven version:

```shell
./mvnw verify
```
