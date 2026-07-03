# Null Is Not Missing: Building a Safe JSON Field-Presence Library in Java

Partial updates look simple until a client sends `null`.

Imagine that an application already stores this document:

```json
{
  "displayName": "Ada",
  "phone": "123",
  "address": {
    "city": "London"
  }
}
```

Now it receives one of the following requests:

```json
{}
```

```json
{"displayName": null}
```

```json
{"displayName": "Grace"}
```

These requests express three different intentions:

| Request state | Meaning |
| --- | --- |
| Field omitted | Leave the previous value alone |
| Field set to `null` | Explicitly clear the value |
| Field given a value | Replace the previous value |

That distinction matters in PATCH-style APIs, configuration updates, audit
logs, form submissions, event processing, and anywhere else that accepts a
partial document. Unfortunately, it is easy to lose the distinction before
business logic gets a chance to use it.

This small problem led me to build **JSON Field Presence**, a Java 17 library
that compares a partial JSON request with a previous document while keeping
omitted fields, explicit nulls, and supplied values separate.

The basic implementation was small. Making it safe and predictable as a real
library was the interesting part.

## Why ordinary POJO binding is not enough

Suppose a request is deserialized into this record:

```java
public record ProfileUpdate(String displayName, String phone) {
}
```

With typical data binding, both `{}` and `{"displayName":null}` can produce a
record whose `displayName` component is Java `null`. At that point, the original
JSON shape has disappeared. The application cannot reliably determine whether
the caller omitted the field or explicitly supplied JSON `null`.

There are framework-specific ways to track setter invocation or wrap every
property in another type, but those approaches leak update semantics into each
request class. They also become cumbersome for nested and dynamic documents.

The simpler rule is to perform presence-sensitive work before converting the
request into an ordinary POJO. A JSON string, Jackson `JsonNode`, or Java map
still preserves which keys were present.

## A three-state model

For each JSON Pointer path, the library records one of three supplied states:

```java
public enum FieldState {
    ABSENT,
    NULL,
    VALUE
}
```

The supplied state is only part of the report. A comparison also contains the
previous state, the effective state after applying the request, and whether the
effective value changed.

Here is a complete example:

```java
import io.github.alaptseu.jsonpresence.FieldComparison;
import io.github.alaptseu.jsonpresence.JsonComparisonReport;
import io.github.alaptseu.jsonpresence.JsonFieldPresence;

String previous = """
        {
          "displayName": "Ada",
          "phone": "123",
          "address": {"city": "London"}
        }
        """;

String request = """
        {
          "displayName": null,
          "address": {"city": "Sydney"}
        }
        """;

JsonComparisonReport report =
        JsonFieldPresence.compareJson(previous, request);

FieldComparison displayName = report.field("/displayName");
displayName.isExplicitNull(); // true
displayName.isChanged();      // true

FieldComparison phone = report.field("/phone");
phone.wasOmitted();           // true
phone.isChanged();            // false

FieldComparison city = report.field("/address/city");
city.wasSupplied();           // true
city.isChanged();             // true

String effective = report.effectiveJsonString();
// {"displayName":null,"phone":"123","address":{"city":"Sydney"}}
```

The effective document is useful because it centralizes the update rules.
Application code does not need to repeat “use the request value when supplied,
otherwise keep the previous value” for every property.

## Make the input representation explicit

The library accepts the three common key-preserving representations:

```java
JsonFieldPresence.compareJson(previousString, requestString);
JsonFieldPresence.compareTrees(previousNode, requestNode);
JsonFieldPresence.compareMaps(previousMap, requestMap);
```

An earlier API used overloaded `compare(...)` methods. That looked tidy until a
call such as this appeared:

```java
compare(null, null);
```

The compiler could not choose between the string, tree, and map overloads.
Distinct names removed the ambiguity and made call sites more descriptive. It
was a small reminder that an API can be concise without being clear.

Configured analyzer instances follow the same naming:

```java
analyzer.analyzeJson(previousString, requestString);
analyzer.analyzeTrees(previousNode, requestNode);
analyzer.analyzeMaps(previousMap, requestMap);
```

## Merge behavior must be deliberate

The default behavior recursively merges JSON objects. Omitted nested
properties survive, while supplied properties are applied. Scalars and arrays
replace their previous values.

Consider this previous value:

```json
{
  "address": {
    "city": "London",
    "postcode": "SW1A"
  }
}
```

With the default merge policy, this request changes the city but preserves the
postcode:

```json
{
  "address": {
    "city": "Sydney"
  }
}
```

Not every API wants that behavior, so object and null handling are configurable:

```java
ComparisonOptions options = ComparisonOptions.builder()
        .nullPolicy(NullPolicy.REMOVE_FIELD)
        .objectPolicy(ObjectPolicy.REPLACE)
        .maxDepth(64)
        .maxNodes(20_000)
        .maxFieldCount(10_000)
        .build();
```

`NullPolicy.SET_NULL`, the default, keeps an explicitly null property in the
effective document. `NullPolicy.REMOVE_FIELD` treats explicit null as a removal
instruction while still reporting that the caller supplied `NULL`.

`ObjectPolicy.MERGE` recursively overlays objects. `ObjectPolicy.REPLACE`
replaces a complete supplied object, so children omitted within that object do
not survive.

Arrays are replacement values in both modes. Element-by-element array merging
would require domain-specific identity and ordering rules that a generic JSON
library cannot safely guess.

## JSON Pointer gives paths a standard vocabulary

The report uses RFC 6901 JSON Pointers:

```java
report.field("/displayName");
report.field("/address/city");
report.field("/items/0/name");
```

`report.fields()` enumerates every object-property path found in the previous,
supplied, or effective documents. `report.changedFields()` returns the
enumerated paths whose effective values changed.

Arrays are deliberately atomic during enumeration. If `/items` contains an
array, `/items` is reported, but its indices and descendants are not expanded
into the map. A caller can still query `/items/0/name` directly with
`report.field(...)`.

Documenting that detail matters. Saying an API returns “every path” would be
misleading when arrays have intentional replacement semantics.

## Hostile input changes the implementation

The first implementation used straightforward recursive tree walking. That is
pleasant to read, but a deeply nested pre-parsed tree or Java map can overflow
the stack. Parser limits alone are insufficient because `JsonNode` and map
inputs can bypass parsing completely.

The hardened implementation validates every input representation before it is
copied or overlaid. It enforces configurable limits for:

- nesting depth;
- total JSON nodes;
- object fields;
- JSON document length;
- decoded string length;
- number-token length; and
- property-name length.

The default maximum depth is 100, with an absolute supported ceiling of 256.
Each document is limited to 100,000 nodes and 100,000 object fields by default.
Applications can choose tighter values.

For JSON strings, limits are enforced while Jackson tokenizes the input, before
an in-memory tree is materialized. This distinction is important. Rejecting an
oversized document only after `readTree()` has allocated it does not protect
memory.

Parsing limits are configured separately:

```java
JsonParsingOptions parsingOptions = JsonParsingOptions.builder()
        .maxDocumentLength(1_000_000)
        .maxStringLength(100_000)
        .maxNumberLength(100)
        .maxPropertyNameLength(1_000)
        .build();

JsonFieldPresence analyzer = JsonFieldPresence.builder()
        .comparisonOptions(options)
        .parsingOptions(parsingOptions)
        .build();
```

Using a separate parsing-options type was also an API compatibility decision.
Adding components to an already published Java record changes its canonical
constructor and binary shape. A new additive type avoided turning a security
improvement into an accidental compatibility break.

Tree traversal, overlay, field collection, and defensive copying use bounded
iterative algorithms. Cyclic Java-container and `JsonNode` graphs are rejected
instead of looping forever.

## Maps are restricted to JSON-native values

`ObjectMapper.valueToTree()` can serialize arbitrary Java objects. That is
convenient for trusted application data, but it makes a public map API much
harder to reason about. A POJO could invoke custom serialization, produce a
surprising shape, or build a structure that preflight validation never saw.

For that reason, `compareMaps` accepts only JSON-native shapes:

- `null`, strings, and booleans;
- finite standard Java numbers;
- maps with string keys;
- iterables and arrays; and
- supported standard Jackson nodes.

It rejects arbitrary POJOs, non-string map keys, non-finite numbers,
`POJONode`, `MissingNode`, and unknown custom scalar-node subclasses.

This makes the boundary less magical and more auditable. Trusted application
objects can still be converted explicitly before calling the tree API.

## Immutability requires more than unmodifiable maps

The report exposes unmodifiable field maps and returns defensive copies of its
JSON snapshots. That sounds routine, but Jackson scalar nodes contain a subtle
edge case: `BinaryNode` can expose mutable byte data.

Returning every scalar node unchanged would allow a caller to mutate data that
was supposed to be a stable comparison result. The copy logic therefore clones
binary data, shares only known immutable Jackson scalar types, and rejects
unknown scalar subclasses.

Mutation tests are valuable here. The important assertion is not merely that a
method returned a different object reference, but that changing the original
input or a returned value cannot alter a previously constructed report.

## Testing a library as a consumer would

Unit tests cover the tri-state model, merge policies, paths, limits, hostile
input, cycles, duplicate keys, numeric representations, and defensive copies.
Generated combinations exercise the same behavior through strings, trees, and
maps.

That still does not prove that another project can use the packaged JAR.

The build therefore creates isolated Maven consumer projects. One compiles and
runs on the ordinary classpath. Another uses the Java Platform Module System
through the stable automatic module name:

```text
io.github.alaptseu.jsonpresence
```

Those consumers verify the runtime JAR, transitive Jackson dependencies,
source JAR, Javadoc JAR, packaged MIT license, and module behavior. This catches
packaging mistakes that tests running inside the producer project can miss.

## Reproducibility is a test, not a timestamp

Setting `project.build.outputTimestamp` is necessary for reproducible Maven
archives, but it is not evidence that a build is reproducible.

The verification process also:

1. runs Maven's `artifact:check-buildplan` goal to detect plugins with known
   reproducibility problems;
2. creates the release artifacts from a clean build;
3. preserves those outputs;
4. performs a second clean build; and
5. compares the JARs, SBOM, and `.buildinfo` files byte for byte.

The `.buildinfo` file records the build environment and output checksums in the
JVM reproducible-build format. A CycloneDX JSON SBOM records the library and its
dependencies. Random SBOM serial numbers are disabled, and the configured
output timestamp is used so the SBOM participates in the reproducibility test.

## Releasing a small library responsibly

The release pipeline is intentionally resumable rather than described as
atomic. Publishing spans GitHub Packages, artifact attestations, and GitHub
Releases. No workflow can roll all of those independent systems back as one
transaction.

Instead, the workflow follows a recoverable sequence:

1. validate that the tag matches the Maven version;
2. verify two byte-identical builds;
3. check whether the package version is absent or already matches;
4. create or reuse a draft GitHub Release;
5. upload only missing, matching release assets;
6. publish the Maven package only when the version is absent;
7. verify downloaded package and release bytes;
8. create missing provenance attestations for the JARs;
9. create a signed CycloneDX SBOM attestation for the runtime JAR; and
10. publish the draft release last.

A rerun can continue a matching partial release. A byte mismatch or a partially
published immutable package fails safely and requires investigation instead of
silently overwriting evidence.

This may seem elaborate for a small library, but consumers do not experience a
dependency as “small.” They experience its API contract, transitive
dependencies, security boundary, upgrade path, and published artifacts.

## What I learned

The central algorithm was not the hardest part of this project. The difficult
questions appeared around its edges:

- At what point does deserialization destroy information?
- Which merge semantics are generic enough to promise?
- Can pre-parsed inputs bypass parser protections?
- Does a supposedly immutable result retain mutable data?
- Will an additive-looking Java change break binary compatibility?
- Can a consumer actually use the packaged artifact?
- Is reproducibility demonstrated, or merely configured?
- Can a failed release be resumed without changing published bytes?

Several principles emerged:

1. **Preserve intent before converting data.** Presence-sensitive logic belongs
   at the JSON boundary, before ordinary POJO binding.
2. **Name representations explicitly.** `compareJson`, `compareTrees`, and
   `compareMaps` are clearer than ambiguous overloads.
3. **Apply limits before allocation and copying.** Validation after tree
   creation is too late for hostile input.
4. **Treat every public input form as a separate attack surface.** Strings,
   trees, and maps reach the same result through different risks.
5. **Verify the artifact, not only the source code.** Isolated consumers,
   two-build comparisons, SBOMs, and attestations test what users receive.
6. **Describe distributed releases honestly.** Resumability is achievable;
   perfect atomicity across services is not.

The result is still a small library. Its job is narrow: preserve the difference
between “not supplied” and “supplied as null” while applying a partial JSON
request to a previous document.

But narrow libraries often sit directly on important boundaries. That is where
small semantic distinctions—and the engineering around them—matter most.
