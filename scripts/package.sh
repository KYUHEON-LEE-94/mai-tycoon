#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./build.sh -q
mkdir -p dist
stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
mkdir -p "$stage/mai-tycoon-starter/plugins"
cp distribution/start.* "$stage/mai-tycoon-starter/"
cp distribution/설치안내.md "$stage/mai-tycoon-starter/INSTALL.md"
cp target/mai-tycoon-0.4.0.jar "$stage/mai-tycoon-starter/plugins/"
cp README.md "$stage/mai-tycoon-starter/DEVELOPMENT.md"
archive="$PWD/dist/mai-tycoon-starter-0.4.0.zip"
# zip replaces the output only after the new archive is complete.
(cd "$stage" && zip -qr package.zip mai-tycoon-starter)
mv "$stage/package.zip" "$archive"
echo "$archive"
