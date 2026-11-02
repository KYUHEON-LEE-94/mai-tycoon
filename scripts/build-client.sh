#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -d .tools/jdk25/jdk-25.0.5+8/Contents/Home ]]; then
  export JAVA_HOME="$PWD/.tools/jdk25/jdk-25.0.5+8/Contents/Home"
fi
cd client
exec ./gradlew build "$@"
