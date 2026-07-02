# Security Policy

## Supported versions

Security fixes are released for the latest patch release in the current major
version. Users should upgrade to that release before reporting that a fix is
missing.

| Version | Security support |
| --- | --- |
| Latest 2.x patch release | Supported |
| Earlier 2.x patch releases | Not supported; upgrade to the latest 2.x release |
| 1.x and earlier | Not supported |

Unreleased code on the default branch may receive security fixes, but it is not
a supported release. A vulnerability that affects only an unsupported version
may still be useful context, although a backport is not guaranteed.

## Report a vulnerability privately

Do not disclose a suspected vulnerability in a public issue, discussion, pull
request, commit, test case, or other public channel.

Use GitHub's [private vulnerability reporting
form](../../security/advisories/new). From the repository, the same form is
available under **Security**, **Advisories**, **Report a vulnerability**. GitHub
keeps the report private between the reporter and repository maintainers.

If the private reporting form is unavailable, open a public issue containing
only a request for a private reporting channel. Do not include vulnerability
details, affected inputs, proof-of-concept code, logs, or proposed patches in
that issue.

Include as much of the following as is safe and relevant:

- a concise description of the weakness and its likely impact;
- affected library versions or commit identifiers;
- the Java version, Jackson version, and operating environment;
- exact reproduction steps and a minimal proof of concept;
- whether untrusted JSON strings, `JsonNode` trees, or Java maps are involved;
- known mitigations or workarounds;
- any disclosure deadline or coordination requirements; and
- whether you would like public credit.

Use synthetic data. Do not send credentials, production documents, access
tokens, personal information, or other secrets.

## What to report

Security reports may include, but are not limited to:

- bypasses of document, string, number, property-name, depth, node, or field
  limits;
- stack exhaustion, excessive allocation, or other denial-of-service behavior
  reachable with untrusted input;
- acceptance of unsafe Java values or Jackson node subclasses;
- mutation of comparison reports through retained mutable input;
- inconsistent validation between JSON-string, tree, and map entry points;
- exploitable vulnerabilities in transitive dependencies; or
- compromise of published packages, release assets, checksums, or provenance
  attestations.

Ordinary correctness bugs, feature requests, documentation problems, and
performance improvements without a security impact should use the public issue
tracker.

## Response and disclosure process

Maintainers aim to acknowledge a private report within five business days and
provide an initial assessment within ten business days. These are targets, not
a service-level agreement. Complex reports may require additional time, but
maintainers will aim to provide an update at least every fourteen days while an
accepted report remains open.

After confirming a vulnerability, maintainers will determine affected
supported versions, prepare regression tests and a fix privately, and
coordinate disclosure with the reporter. When appropriate, the project will
publish a GitHub Security Advisory and request a CVE. Please allow the fix and
security release to become available before publishing technical details.

Security releases follow the repository's normal verification and resumable
release process: the complete Maven build must pass, package and GitHub Release
artifacts must match their expected digests, provenance attestations must be
verified, the runtime JAR's CycloneDX SBOM attestation must be verified, and the
draft release is made public only after those checks finish.

Good-faith reports and responsible testing are appreciated. Testing must not
access other people's data, degrade services, or target infrastructure outside
the reporter's authorization.
