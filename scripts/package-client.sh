#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/build-client.sh
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import json
import os
import tempfile

root = Path.cwd()
props = dict(line.split('=', 1) for line in (root / 'client/gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
version = props['version']
jar = root / f'client/build/libs/mai-tycoon-client-{version}.jar'
with ZipFile(jar) as archive:
    metadata = json.loads(archive.read('fabric.mod.json'))
    assert metadata['version'] == version
    assert metadata['depends']['minecraft'] == props['minecraft_version']
files = {
    f'mods/{jar.name}': jar.read_bytes(),
    'INSTALL.md': (root / 'distribution/client/INSTALL.md').read_bytes(),
    'COMPATIBILITY.md': (root / 'docs/client/COMPATIBILITY.md').read_bytes(),
}
files['SHA256SUMS.txt'] = ''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n' for name, data in files.items()).encode()
dist = root / 'dist'
dist.mkdir(exist_ok=True)
output = dist / f'mai-tycoon-client-{version}-preview.zip'
fd, staging = tempfile.mkstemp(suffix='.zip', dir=dist)
os.close(fd)
try:
    with ZipFile(staging, 'w', ZIP_DEFLATED) as archive:
        for name, data in files.items():
            archive.writestr(name, data)
    os.replace(staging, output)
finally:
    if os.path.exists(staging):
        os.unlink(staging)
print(output)
PY
