# JSON Field Presence

A small Java 17 library for partial JSON requests. It keeps these three cases
separate at every RFC 6901 JSON Pointer path:

- `ABSENT`: the caller did not pass the field.
- `NULL`: the caller explicitly passed JSON `null`.
- `VALUE`: the caller passed a non-null value.

The request is compared with a previous JSON object. The report also exposes an
effective object where omitted properties retain their previous values and
supplied properties—including explicit nulls—are applied. By default, nested
objects are overlaid recursively while arrays and scalar values replace their
previous values. These rules are configurable.

The entry-point name makes the input representation explicit:
`compareJson` accepts JSON strings, `compareTrees` accepts Jackson `JsonNode`
objects, and `compareMaps` accepts Java maps. This keeps calls such as
`compareJson(null, null)` unambiguous at compile time.

Users upgrading from the overloaded 1.x API should follow the
[migration guide](../MIGRATION.md) and [changelog](../CHANGELOG.md).

## Build and install locally

From the repository root:

```shell
./mvnw clean install
```

`package`, `install`, and `deploy` produce three artifacts:

- `json-field-presence-2.0.0.jar`: the runtime library;
- `json-field-presence-2.0.0-sources.jar`: IDE source attachment; and
- `json-field-presence-2.0.0-javadoc.jar`: generated API documentation.

All archives use deterministic timestamps. The runtime JAR declares the stable
automatic module name `io.github.alaptseu.jsonpresence` and includes
specification and implementation version metadata.

Then add the library to another Maven project:

```xml
<dependency>
    <groupId>io.github.alaptseu</groupId>
    <artifactId>json-field-presence</artifactId>
    <version>2.0.0</version>
</dependency>
```

## Usage with JSON strings

```java
import io.github.alaptseu.jsonpresence.FieldComparison;
import io.github.alaptseu.jsonpresence.FieldState;
import io.github.alaptseu.jsonpresence.JsonComparisonReport;
import io.github.alaptseu.jsonpresence.JsonFieldPresence;

String previous = """
        {"displayName":"Ada","phone":"123","address":{"city":"London"}}
        """;
String request = """
        {"displayName":null,"address":{"city":"Sydney"}}
        """;

JsonComparisonReport report = JsonFieldPresence.compareJson(previous, request);

FieldComparison displayName = report.field("/displayName");
displayName.suppliedState();  // NULL
displayName.isExplicitNull(); // true

FieldComparison phone = report.field("/phone");
phone.suppliedState();        // ABSENT
phone.wasOmitted();           // true
phone.effectiveValue();       // Optional containing "123"

FieldComparison city = report.field("/address/city");
city.suppliedState();         // VALUE
city.isChanged();             // true

String effective = report.effectiveJsonString();
// {"displayName":null,"phone":"123","address":{"city":"Sydney"}}
```

## Usage with Jackson JSON objects

```java
ObjectMapper mapper = new ObjectMapper();
JsonNode previous = mapper.readTree(
        "{\"displayName\":\"Ada\",\"phone\":\"123\"}");
JsonNode request = mapper.readTree(
        "{\"displayName\":null}");

JsonComparisonReport report = JsonFieldPresence.compareTrees(previous, request);

report.field("/displayName").isExplicitNull(); // true
report.field("/phone").wasOmitted();           // true
```

## Usage with Java maps

```java
Map<String, Object> previous = new LinkedHashMap<>();
previous.put("displayName", "Ada");
previous.put("phone", "123");

Map<String, Object> request = new LinkedHashMap<>();
request.put("displayName", null);

JsonComparisonReport report = JsonFieldPresence.compareMaps(previous, request);
```

## Merge policies and input limits

The default behavior keeps explicit nulls, recursively merges objects, and
limits each input document to a depth of 100, 100,000 total JSON nodes, and
100,000 object fields:

```java
ComparisonOptions options = ComparisonOptions.builder()
        .nullPolicy(NullPolicy.REMOVE_FIELD)
        .objectPolicy(ObjectPolicy.REPLACE)
        .maxDepth(64)
        .maxNodes(20_000)
        .maxFieldCount(10_000)
        .build();

JsonComparisonReport report = JsonFieldPresence.compareMaps(previous, request, options);
```

`NullPolicy.REMOVE_FIELD` keeps the supplied state as `NULL` but removes the
property from the effective document. `ObjectPolicy.REPLACE` replaces a complete
nested object, so properties omitted inside that object do not survive. Arrays
and scalar values are always replacements.

JSON strings are also bounded while they are tokenized, before Jackson creates
a tree. The defaults allow at most 20,000,000 characters in a document or
decoded string, 1,000 characters in a number token, and 50,000 characters in a
property name. Configure tighter limits through the analyzer builder without
changing `ComparisonOptions`:

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

JsonComparisonReport report = analyzer.analyzeJson(previousJson, requestJson);
```

The streaming preflight also applies `maxDepth`, `maxNodes`, and
`maxFieldCount` before tree materialization. A supplied custom mapper keeps any
stricter `StreamReadConstraints`; library configuration never loosens them.

Limits are checked before snapshots, overlays, or reports are created. Object
properties, array elements, container nodes, scalar nodes, and each document
root count toward `maxNodes`; every property in every nested object counts
toward `maxFieldCount`. The field-count limit also caps the combined comparison
report. Java maps and other Java containers nested within them are preflighted
before Jackson conversion. Cyclic `JsonNode` and Java-container graphs are
rejected. `maxDepth` cannot exceed `ComparisonOptions.MAX_SUPPORTED_DEPTH`
(256), which keeps any remaining trusted Jackson mapper operations within a
conservative stack bound. Defensive tree copies are iterative.

Map values must already have a JSON-native shape: `null`, strings, booleans,
standard finite Java numbers, `Map` objects with string keys, `Iterable`
objects, arrays, or standard Jackson scalar, object, and array nodes. Binary
nodes are copied byte-for-byte. Arbitrary POJOs, custom scalar-node subclasses,
non-string map keys, non-finite numbers, `POJONode`, and `MissingNode` values are
rejected before conversion. Convert application objects to one of these shapes
before calling `compareMaps`.

Call `report.fields()` to enumerate every object-property path found in the
previous, supplied, or effective objects, or `report.changedFields()` to get
only changed object-property paths. Arrays are atomic values for enumeration:
the property containing an array is included, but array indices and their
descendants are not. You can still query any array path directly with
`report.field("/items/0")` or `report.field("/items/0/name")`.

The request must be provided in a key-preserving form: a JSON string, Jackson
`JsonNode`, or `Map<String, ?>`. Once JSON has been bound to a normal POJO, most
serializers no longer retain enough information to tell an omitted property
from one explicitly set to null.

The default string parser rejects duplicate object keys instead of silently
keeping the first or last occurrence. A custom `ObjectMapper` may choose a
different duplicate-key policy.

## Verification

Run the complete local verification suite with:

From the repository root:

```shell
./mvnw verify
```

The `verify` phase enforces all of the following:

- Java 17 or newer and Maven 3.8.5 or newer;
- deterministic generated comparisons across strings, Jackson trees, Java maps,
  and all merge-policy combinations;
- isolated classpath and JPMS consumer builds against the staged runtime JAR,
  including transitive Jackson resolution and packaged artifact checks;
- edge cases for duplicate keys, JSON Pointer escaping, numeric representations,
  array replacement, configured limits, and large documents;
- a Maven reproducible-build-plan check, deterministic CycloneDX JSON SBOM, and
  reproducible `.buildinfo` evidence;
- at least 90% line coverage and 85% branch coverage through JaCoCo; and
- zero SpotBugs findings at `Low` threshold with maximum analysis effort.

The HTML coverage report is written to
`json-field-presence/target/site/jacoco/index.html`, and the SpotBugs XML report
is written to `json-field-presence/target/spotbugsXml.xml`. The CycloneDX SBOM
is written to
`json-field-presence/target/json-field-presence-<version>-cyclonedx.json`.
The root reactor build writes aggregate evidence to
`target/json-field-presence-reactor-<version>.buildinfo`; the module-only
release build writes library evidence to
`json-field-presence/target/json-field-presence-<version>.buildinfo`. Consumer
build results are written to `json-field-presence/target/invoker-reports/`,
with cloned external projects under `json-field-presence/target/it/` and their
isolated repository under `json-field-presence/target/it-repo/`.

To run the build-plan validation independently, use:

```shell
./mvnw -f json-field-presence/pom.xml artifact:check-buildplan \
    -Dcheck.buildplan.tasks=clean,verify -Ddiagnose=true
```

After at least one version in the current major line has been published, check
public API compatibility against that different baseline version with:

```shell
./mvnw -f json-field-presence/pom.xml \
    -Papi-compatibility -Dapi.baseline.version=2.0.0 clean verify
```

Use this example after the project version has advanced beyond `2.0.0`. The
profile rejects a baseline equal to the current project version, preventing a
meaningless self-comparison, and fails for source- or binary-incompatible API
changes by default.

For an intentional major-version audit, generate the report without failing
the build:

```shell
./mvnw -f json-field-presence/pom.xml \
    -Papi-compatibility -Dapi.baseline.version=1.0.0 \
    -Dapi.breakBuildOnIncompatibility=false clean verify
```

The report is written under `target/japicmp/`.

## GitHub CI/CD

The repository workflows automatically:

- verify the library on Java 17, 21, and 25 for relevant pushes and pull requests;
- review pull-request dependency changes and reject newly introduced
  vulnerabilities of moderate severity or higher;
- run CodeQL's extended Java security queries on pushes, pull requests, a weekly
  schedule, and manual runs;
- retain the runtime, source, and Javadoc JARs, CycloneDX SBOM, `.buildinfo`,
  JaCoCo report, and SpotBugs report as workflow artifacts;
- publish releases to GitHub Packages;
- rebuild and byte-compare every release artifact;
- attach the SBOM, `.buildinfo`, and a SHA-256 manifest to each GitHub Release;
- create an SBOM attestation for the runtime JAR; and
- attest the provenance of all three release JARs before publishing the draft
  GitHub Release.

GitHub Actions are pinned to immutable commit hashes. Dependabot checks Maven
dependencies, build plugins, and Action revisions every week.

To release version `2.0.0`, ensure the POM version is `2.0.0`, then push its
release tag:

```shell
git tag -a json-field-presence-v2.0.0 -m "JSON Field Presence 2.0.0"
git push origin json-field-presence-v2.0.0
```

The workflow rejects a tag whose version does not match the POM, validates the
Maven build plan, performs two clean builds, and requires byte-identical JARs,
SBOM, and `.buildinfo` before checking whether that package version already
exists. It then creates or reuses a draft GitHub Release, reuses matching
assets, adds missing assets, publishes the package only when the version is
absent, and verifies downloaded package and release bytes before publishing the
draft. Missing provenance and SBOM attestations are also created and verified.
A rerun therefore resumes a matching partial release instead of attempting to
overwrite it, and runs for the same tag are serialized.

Release publication spans GitHub Packages, attestations, and GitHub Releases,
so it is resumable rather than atomic. A mixed or byte-mismatched immutable
package version fails safely and requires manual removal before a rerun; an
immutable GitHub Release that is missing assets also requires manual repair.

After downloading a release JAR, verify that it was built by this repository's
release workflow, replacing `OWNER/REPOSITORY` with the GitHub repository path:

```shell
gh attestation verify json-field-presence-2.0.0.jar \
    --repo OWNER/REPOSITORY \
    --predicate-type https://slsa.dev/provenance/v1 \
    --signer-workflow github.com/OWNER/REPOSITORY/.github/workflows/json-field-presence-release.yml
```

Verify the runtime JAR's signed CycloneDX SBOM attestation with:

```shell
gh attestation verify json-field-presence-2.0.0.jar \
    --repo OWNER/REPOSITORY \
    --predicate-type https://cyclonedx.org/bom \
    --signer-workflow github.com/OWNER/REPOSITORY/.github/workflows/json-field-presence-release.yml
```

Download all three JARs, the `-cyclonedx.json` SBOM, `.buildinfo`, and
`SHA256SUMS` into the same directory, then verify their release digests with:

```shell
sha256sum --check SHA256SUMS
```

Consumers of the GitHub Packages artifact must add the repository to Maven,
replacing `OWNER/REPOSITORY` with this repository's GitHub path:

```xml
<repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/OWNER/REPOSITORY</url>
</repository>
```

GitHub Packages authentication can then be configured for the `github` server
ID in the consumer's Maven `settings.xml`.

## License

JSON Field Presence is available under the [MIT License](LICENSE).
