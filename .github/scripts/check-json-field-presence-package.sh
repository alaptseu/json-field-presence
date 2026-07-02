#!/usr/bin/env bash

set -euo pipefail

version="${1:?Usage: check-json-field-presence-package.sh VERSION [allow-absent|require-present]}"
mode="${2:-allow-absent}"

if [[ "${mode}" != "allow-absent" && "${mode}" != "require-present" ]]; then
    echo "Unsupported package check mode: ${mode}" >&2
    exit 2
fi

: "${GITHUB_ACTOR:?GITHUB_ACTOR must be set}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY must be set}"
: "${GITHUB_TOKEN:?GITHUB_TOKEN must be set}"
: "${GITHUB_WORKSPACE:?GITHUB_WORKSPACE must be set}"
: "${RUNNER_TEMP:?RUNNER_TEMP must be set}"

module_dir="${GITHUB_WORKSPACE}/json-field-presence"
remote_base="https://maven.pkg.github.com/${GITHUB_REPOSITORY}/io/github/alaptseu/json-field-presence/${version}"
download_dir="${RUNNER_TEMP}/json-field-presence-package-${version}"
curl_bin="${CURL_BIN:-curl}"

artifact_specs=(
    "${module_dir}/target/json-field-presence-${version}.jar|json-field-presence-${version}.jar"
    "${module_dir}/target/json-field-presence-${version}-sources.jar|json-field-presence-${version}-sources.jar"
    "${module_dir}/target/json-field-presence-${version}-javadoc.jar|json-field-presence-${version}-javadoc.jar"
    "${module_dir}/pom.xml|json-field-presence-${version}.pom"
)

present=0
absent=0

for spec in "${artifact_specs[@]}"; do
    local_path="${spec%%|*}"
    remote_name="${spec#*|}"
    if [[ ! -f "${local_path}" ]]; then
        echo "Expected local release artifact is missing: ${local_path}" >&2
        exit 1
    fi
    status="$({
        "${curl_bin}" --silent --show-error --location \
            --user "${GITHUB_ACTOR}:${GITHUB_TOKEN}" \
            --output /dev/null \
            --write-out '%{http_code}' \
            "${remote_base}/${remote_name}"
    })"

    case "${status}" in
        200) present=$((present + 1)) ;;
        404) absent=$((absent + 1)) ;;
        *)
            echo "Package preflight returned HTTP ${status} for ${remote_name}." >&2
            exit 1
            ;;
    esac
done

if (( absent == ${#artifact_specs[@]} )); then
    if [[ "${mode}" == "require-present" ]]; then
        echo "Package ${version} is still absent after deployment." >&2
        exit 1
    fi

    printf 'absent\n'
    exit 0
fi

if (( present != ${#artifact_specs[@]} )); then
    echo "Package ${version} is incomplete (${present} expected files present, ${absent} absent)." >&2
    echo "GitHub Packages versions are immutable; remove the partial version manually before rerunning." >&2
    exit 1
fi

mkdir -p "${download_dir}"

for spec in "${artifact_specs[@]}"; do
    local_path="${spec%%|*}"
    remote_name="${spec#*|}"
    remote_path="${download_dir}/${remote_name}"

    "${curl_bin}" --fail --silent --show-error --location \
        --user "${GITHUB_ACTOR}:${GITHUB_TOKEN}" \
        --output "${remote_path}" \
        "${remote_base}/${remote_name}"

    if ! cmp --silent "${local_path}" "${remote_path}"; then
        echo "Published package file ${remote_name} does not match the locally verified artifact." >&2
        sha256sum "${local_path}" "${remote_path}" >&2
        exit 1
    fi
done

printf 'matching\n'
