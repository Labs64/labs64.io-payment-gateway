#!/usr/bin/env bash
set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
readonly JAR_PATH="${JAR_PATH:-${PROJECT_DIR}/target/payment-gateway.jar}"
readonly POLL_SECONDS="${WATCH_POLL_SECONDS:-1}"
readonly SETTLE_SECONDS="${WATCH_SETTLE_SECONDS:-1}"

compose=(
    bash "${SCRIPT_DIR}/compose.sh"
    -f "${PROJECT_DIR}/docker-compose.yml"
    -f "${PROJECT_DIR}/docker-compose.dev.yml"
)

fingerprint() {
    stat -c '%s:%y' "${JAR_PATH}" 2>/dev/null
}

jar_checksum() {
    sha256sum "${JAR_PATH}" | awk '{print $1}'
}

is_executable_jar() {
    local listing

    listing="$(jar tf "${JAR_PATH}" 2>/dev/null)" || return 1
    grep -q '^BOOT-INF/classes/' <<<"${listing}"
}

wait_for_initial_jar() {
    local before
    local after

    while true; do
        before="$(fingerprint)" || {
            sleep "${POLL_SECONDS}"
            continue
        }

        sleep "${SETTLE_SECONDS}"
        after="$(fingerprint)" || continue

        if [[ "${before}" == "${after}" ]] && is_executable_jar; then
            return
        fi
    done
}

cd "${PROJECT_DIR}"

echo "Waiting for executable JAR: ${JAR_PATH}"
wait_for_initial_jar

active_checksum="$(jar_checksum)"
processed_fingerprint="$(fingerprint)"
echo "Watching ${JAR_PATH} for completed builds..."

while sleep "${POLL_SECONDS}"; do
    candidate_fingerprint="$(fingerprint)" || continue
    [[ "${candidate_fingerprint}" != "${processed_fingerprint}" ]] || continue

    sleep "${SETTLE_SECONDS}"
    stable_fingerprint="$(fingerprint)" || continue
    [[ "${candidate_fingerprint}" == "${stable_fingerprint}" ]] || continue

    processed_fingerprint="${stable_fingerprint}"
    is_executable_jar || continue

    candidate_checksum="$(jar_checksum)"
    [[ "$(fingerprint)" == "${stable_fingerprint}" ]] || continue
    [[ "${candidate_checksum}" != "${active_checksum}" ]] || continue

    echo "Completed JAR detected; restarting app..."
    "${compose[@]}" restart app
    active_checksum="${candidate_checksum}"
    echo "App restarted; watching for the next completed build..."
done
