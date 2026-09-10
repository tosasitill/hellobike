#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="${JAVA_HOME:-$ROOT/toolchain/jdk-17.0.20.1+1/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$ROOT/toolchain/android-sdk}"
exec "$ROOT/toolchain/gradle-9.3.1/bin/gradle" -p "$ROOT" :app:assembleRelease :app:testDebugUnitTest :app:lintDebug --console=plain "$@"
