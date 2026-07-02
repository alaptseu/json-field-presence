# Contributing to JSON Field Presence

Thank you for helping improve JSON Field Presence. Contributions may include
bug fixes, tests, documentation, dependency maintenance, performance work, and
carefully designed API enhancements.

## Before starting

Search existing issues before opening a new one. For a substantial feature,
public API change, behavioral change, or new dependency, open an issue first so
the approach and compatibility impact can be agreed before implementation.

Suspected vulnerabilities must follow the [security policy](SECURITY.md). Do
not open a public issue or pull request containing vulnerability details before
coordinated disclosure.

## Development setup

Install JDK 17 or newer. Use the checked-in Maven Wrapper rather than relying on
a separately installed Maven version.

From the repository root, run the complete build:

```shell
./mvnw clean verify
```

On Windows, use:

```powershell
mvnw.cmd clean verify
```

The root POM is an aggregator for the `json-field-presence` module. The full
build compiles for Java 17 and runs unit tests, generated tests, JaCoCo coverage
checks, SpotBugs, `artifact:check-buildplan`, SBOM and `.buildinfo` generation,
packaging, and isolated classpath and JPMS consumer builds. CI repeats
verification on Java 17, 21, and 25.

For a quicker unit-test cycle, run:

```shell
./mvnw -pl json-field-presence test
```

Run the complete build before submitting a pull request.

## Contribution workflow

1. Create a focused branch from the current default branch.
2. Make the smallest coherent change that solves the problem.
3. Add or update tests that fail without the change.
4. Update Javadoc, README examples, migration guidance, and the changelog when
   observable behavior or public API changes.
5. Run `./mvnw clean verify` and resolve every failure.
6. Open a pull request describing the problem, approach, compatibility impact,
   security implications, and verification performed.

Keep commits reviewable and avoid unrelated formatting or dependency changes.
Do not commit build output, local Maven repositories, IDE state, credentials,
tokens, private test data, or generated reports from `target/`.

## Code and test expectations

Follow the existing Java style: four-space indentation, explicit imports,
immutable public results, precise validation errors, and Javadoc for public
types and behavior. Prefer clear, bounded iteration for data controlled by a
caller. Any recursion or allocation proportional to input must remain within a
documented enforced limit.

Tests use JUnit 5. Cover the successful path, boundary values, invalid inputs,
and regression case. Security-limit changes should test every applicable input
form: JSON strings, pre-parsed Jackson trees, and Java maps. Defensive-copy
changes should include mutation attempts after comparison.

The build enforces at least 90% line coverage, 85% branch coverage, and no
SpotBugs findings at the configured `Low` threshold. Coverage percentage alone
does not replace assertions about externally observable behavior.

Changes affecting packaging or dependencies must preserve and test:

- ordinary Maven classpath consumption;
- the `io.github.alaptseu.jsonpresence` automatic module name;
- transitive Jackson availability;
- runtime, source, Javadoc, and license artifacts;
- CycloneDX JSON SBOM and reproducible `.buildinfo` evidence; and
- reproducible release artifacts.

## Compatibility rules

The project follows semantic versioning for the public Java API and documented
behavior:

- patch releases contain compatible fixes and documentation improvements;
- minor releases may add functionality without breaking existing source or
  binary consumers; and
- source-incompatible, binary-incompatible, or materially incompatible
  behavioral changes require a new major version.

Within a major release line:

- do not remove or narrow public types, methods, constructors, fields, or enum
  constants;
- avoid overloads that introduce ambiguous calls, especially with `null`;
- do not add components to a published public record, because doing so changes
  its canonical constructor and binary shape;
- introduce new option types or builder methods additively when evolving
  configuration;
- retain Java 17 bytecode compatibility;
- preserve the distinction between omitted fields and explicit `null` values;
- preserve documented array enumeration and JSON Pointer behavior; and
- do not weaken input limits, JSON-native map restrictions, defensive copying,
  or parser constraints without an explicit security review.

After the project version advances beyond a published baseline in the same
major line, run the API compatibility profile:

```shell
./mvnw -f json-field-presence/pom.xml \
    -Papi-compatibility \
    -Dapi.baseline.version=<previous-published-version> \
    clean verify
```

The baseline must differ from the current project version. Incompatible changes
fail the build by default. Intentional major-version changes require changelog
entries, a migration-guide update, compatibility tests where practical, and an
API audit with failure disabled only for reviewing the expected breakage.

## Dependencies

Explain why each new runtime dependency is necessary and consider its API,
license, maintenance, security, module behavior, and effect on consumers. Avoid
snapshot dependencies and unnecessary version ranges. Dependency updates must
pass the complete build and GitHub's dependency review.

## Release expectations

Maintainers perform releases. Normal contribution pull requests should not bump
project versions, create release tags, or publish artifacts unless the change
is explicitly release preparation.

A release must:

- keep the root aggregator and library module versions aligned;
- contain no snapshot dependencies;
- pass `./mvnw clean verify` and the applicable API compatibility check;
- update `CHANGELOG.md` and, for breaking changes, `MIGRATION.md`;
- retain runtime, source, Javadoc, license, CycloneDX SBOM, `.buildinfo`,
  checksum, provenance, and SBOM attestation artifacts;
- reproduce all published files byte-for-byte in a second clean build; and
- use an annotated tag named `json-field-presence-v<version>` whose version
  exactly matches the library POM.

The release workflow is resumable, not atomic. It creates or reuses a draft
GitHub Release, reuses only byte-identical assets and packages, fills in missing
recoverable state, verifies checksums and attestations, and publishes the draft
last. Never reuse a published version for different bytes. A partial or
mismatched immutable package must be investigated and repaired by a maintainer
before rerunning the release.

## Pull-request checklist

Before requesting review, confirm that:

- the change is focused and its motivation is documented;
- tests cover the change and relevant hostile or boundary inputs;
- `./mvnw clean verify` passes;
- public API compatibility has been checked when applicable;
- user-facing documentation and changelog entries are current;
- no secrets, private data, generated build output, or unrelated edits are
  included; and
- security-sensitive details use the private process in `SECURITY.md`.
