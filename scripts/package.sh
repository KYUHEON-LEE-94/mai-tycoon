#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./build.sh -q
version=$(sed -n 's#.*<artifactId>mai-tycoon</artifactId><version>\([^<]*\)</version>.*#\1#p' pom.xml)
mkdir -p dist
stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
mkdir -p "$stage/mai-tycoon-starter/plugins"
cp distribution/start.* "$stage/mai-tycoon-starter/"
cp distribution/설치안내.md "$stage/mai-tycoon-starter/INSTALL.md"
cp "target/mai-tycoon-$version.jar" "$stage/mai-tycoon-starter/plugins/"
cp README.md "$stage/mai-tycoon-starter/DEVELOPMENT.md"
cp -R docs "$stage/mai-tycoon-starter/docs"
archive="$PWD/dist/mai-tycoon-starter-$version.zip"
# zip replaces the output only after the new archive is complete.
(cd "$stage" && zip -qr package.zip mai-tycoon-starter)
mv "$stage/package.zip" "$archive"
echo "$archive"
