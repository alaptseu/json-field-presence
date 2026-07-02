# Changelog

All notable changes to JSON Field Presence are documented here.

## 2.0.0 - 2026-07-02

### Breaking changes

- Replaced the ambiguous `compare(...)` overloads with `compareJson(...)`,
  `compareTrees(...)`, and `compareMaps(...)`.
- Replaced the ambiguous instance `analyze(...)` overloads with
  `analyzeJson(...)`, `analyzeTrees(...)`, and `analyzeMaps(...)`.
- Restricted Java map values to JSON-native shapes; arbitrary POJOs are now
  rejected before Jackson conversion.
- Added an absolute supported nesting-depth ceiling of 256.

See the [migration guide](MIGRATION.md) for source-level upgrade examples.

### Added

- Configurable depth, node-count, and field-count input limits.
- Additive JSON parsing options for document, string, number, and property-name
  lengths, with streaming node, field, and depth checks before tree creation.
- Iterative overlays, field collection, and defensive JSON tree copies.
- Defensive binary-node copies and rejection of unknown scalar-node subclasses.
- Runtime, source, and Javadoc artifacts with reproducible archive metadata.
- Isolated classpath and JPMS consumer integration tests for packaged artifacts.
- Resumable tag-driven GitHub releases with digest-verified package and release
  artifacts, plus expanded CI verification.
- Pull-request dependency review, CodeQL scanning, and provenance attestations
  for every release JAR.
- CycloneDX JSON SBOM generation and a signed runtime-JAR SBOM attestation.
- Maven build-plan validation, two-build byte comparisons, and published
  `.buildinfo` reproducibility evidence.
- Root Maven reactor and a checksum-verified Maven Wrapper for reproducible
  contributor builds.
- Contributor and security policies covering private vulnerability reports,
  compatibility, verification, and release expectations.
- MIT licensing in repository and Maven artifact metadata.

## 1.0.0

- Initial API with overloaded `compare(...)` and `analyze(...)` entry points.
