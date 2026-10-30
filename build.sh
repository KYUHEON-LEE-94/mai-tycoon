#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -d .tools/jdk-21.0.13+9/Contents/Home ]]; then
  export JAVA_HOME="$PWD/.tools/jdk-21.0.13+9/Contents/Home"
fi
if [[ -x .tools/apache-maven-3.9.9/bin/mvn ]]; then
  exec .tools/apache-maven-3.9.9/bin/mvn verify "$@"
fi
exec mvn verify "$@"
