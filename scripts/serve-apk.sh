#!/usr/bin/env bash
# Build debug APK and serve it over HTTP for remote installs.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SERVE_DIR="${SCRIPT_DIR}/serve"
COUNTER_FILE="${SCRIPT_DIR}/.apk-build-counter"

rm -rf "${SERVE_DIR}"
mkdir -p "${SERVE_DIR}"

cd "${REPO_ROOT}"
./gradlew assembleDebug

VERSION="$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts)"

if [[ -f "${COUNTER_FILE}" ]]; then
	BUILD_NUM="$(($(cat "${COUNTER_FILE}") + 1))"
else
	BUILD_NUM=1
fi
echo "${BUILD_NUM}" > "${COUNTER_FILE}"

APK_NAME="stopptanz-v${VERSION}-build${BUILD_NUM}.apk"
cp app/build/outputs/apk/debug/app-debug.apk "${SERVE_DIR}/${APK_NAME}"

LAN_IP="$(hostname -I | awk '{print $1}')"
URL="http://${LAN_IP}:8000/${APK_NAME}"

echo "Serving: ${URL}"
echo "All local IPs: $(hostname -I)"

if command -v qrencode >/dev/null 2>&1; then
	qrencode -t ANSIUTF8 "${URL}"
fi

cleanup() {
	if [[ -n "${SERVER_PID:-}" ]]; then
		kill "${SERVER_PID}" 2>/dev/null || true
		wait "${SERVER_PID}" 2>/dev/null || true
	fi
	exit 0
}
trap cleanup SIGINT SIGTERM

cd "${SERVE_DIR}"
python3 -m http.server 8000 &
SERVER_PID=$!
wait "${SERVER_PID}"
