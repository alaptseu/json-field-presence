#!/usr/bin/env bash

mock_curl() {
    local output=""
    local write_out=""
    local url=""

    while (( $# > 0 )); do
        case "$1" in
            --output | --user | --write-out)
                if [[ "$1" == "--output" ]]; then
                    output="$2"
                elif [[ "$1" == "--write-out" ]]; then
                    write_out="$2"
                fi
                shift 2
                ;;
            --fail | --silent | --show-error | --location)
                shift
                ;;
            *)
                url="$1"
                shift
                ;;
        esac
    done

    local remote_path="${MOCK_REMOTE_DIR}/${url##*/}"
    if [[ -n "${write_out}" ]]; then
        if [[ -f "${remote_path}" ]]; then
            printf '200'
        else
            printf '404'
        fi
        return 0
    fi

    [[ -f "${remote_path}" ]] || return 22
    cp "${remote_path}" "${output}"
}

if [[ "${MOCK_CURL:-false}" == "true" ]]; then
    mock_curl "$@"
    exit $?
fi

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
checker="${script_dir}/check-json-field-presence-package.sh"
temporary_dir="$(mktemp -d)"
trap 'rm -rf "${temporary_dir}"' EXIT

version="2.0.0"
workspace="${temporary_dir}/workspace"
target="${workspace}/json-field-presence/target"
remote="${temporary_dir}/remote"
runner_temp="${temporary_dir}/runner"
mkdir -p "${target}" "${remote}" "${runner_temp}"

printf 'runtime\n' > "${target}/json-field-presence-${version}.jar"
printf 'sources\n' > "${target}/json-field-presence-${version}-sources.jar"
printf 'javadoc\n' > "${target}/json-field-presence-${version}-javadoc.jar"
printf '<project/>\n' > "${workspace}/json-field-presence/pom.xml"

run_checker() {
    CURL_BIN="$0" \
    GITHUB_ACTOR="release-test" \
    GITHUB_REPOSITORY="owner/repository" \
    GITHUB_TOKEN="test-token" \
    GITHUB_WORKSPACE="${workspace}" \
    MOCK_CURL="true" \
    MOCK_REMOTE_DIR="${remote}" \
    RUNNER_TEMP="${runner_temp}" \
        bash "${checker}" "${version}" "$1"
}

assert_state() {
    local expected="$1"
    local mode="$2"
    local actual
    if ! actual="$(run_checker "${mode}")"; then
        echo "The ${mode} package check failed unexpectedly." >&2
        exit 1
    fi
    if [[ "${actual}" != "${expected}" ]]; then
        echo "Expected package state ${expected}, got ${actual}." >&2
        exit 1
    fi
}

assert_state absent allow-absent
if run_checker require-present >/dev/null 2>&1; then
    echo "require-present accepted an absent package." >&2
    exit 1
fi

cp "${target}/json-field-presence-${version}.jar" "${remote}/json-field-presence-${version}.jar"
cp "${target}/json-field-presence-${version}-sources.jar" "${remote}/json-field-presence-${version}-sources.jar"
cp "${target}/json-field-presence-${version}-javadoc.jar" "${remote}/json-field-presence-${version}-javadoc.jar"
cp "${workspace}/json-field-presence/pom.xml" "${remote}/json-field-presence-${version}.pom"
assert_state matching require-present

printf 'different\n' > "${remote}/json-field-presence-${version}.jar"
if run_checker require-present >/dev/null 2>&1; then
    echo "The package check accepted a mismatched artifact." >&2
    exit 1
fi
cp "${target}/json-field-presence-${version}.jar" "${remote}/json-field-presence-${version}.jar"

rm "${remote}/json-field-presence-${version}-sources.jar"
if run_checker allow-absent >/dev/null 2>&1; then
    echo "The package check accepted a partial package version." >&2
    exit 1
fi

echo "Package-state checks passed."
