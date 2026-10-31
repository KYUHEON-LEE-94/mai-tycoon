#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
trap 'echo "실행이 종료되었습니다. Enter를 눌러 닫으세요."; read -r _' EXIT
JAVA=java
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then JAVA="$JAVA_HOME/bin/java"; fi
if [[ -x ../.tools/jdk-21.0.13+9/Contents/Home/bin/java ]]; then JAVA=../.tools/jdk-21.0.13+9/Contents/Home/bin/java; fi
if ! "$JAVA" -version; then
  echo 'Java 21을 설치하세요: https://adoptium.net/temurin/releases/?version=21'
  exit 1
fi
if [[ ! -f paper.jar ]]; then
  curl -fL --retry 3 'https://fill-data.papermc.io/v1/objects/5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba/paper-1.21.11-132.jar' -o paper.jar.download
  if command -v shasum >/dev/null; then
    echo '5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba  paper.jar.download' | shasum -a 256 -c -
  else
    echo '5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba  paper.jar.download' | sha256sum -c -
  fi
  mv paper.jar.download paper.jar
fi
if ! [[ -f eula.txt ]] || ! grep -Eq '^eula=true\r?$' eula.txt; then
  echo 'Minecraft EULA: https://www.minecraft.net/eula'
  echo '서버 운영을 위해 위 약관에 동의하는 경우에만 AGREE를 입력하세요:'
  read -r consent
  [[ "$consent" == AGREE ]] || exit 1
  echo 'eula=true' > eula.txt
fi
if [[ ! -f server.properties ]]; then
  printf '%s\n' 'server-ip=127.0.0.1' 'server-port=25565' 'online-mode=true' 'level-seed=607115319713086458' 'gamemode=survival' 'difficulty=easy' 'view-distance=8' 'simulation-distance=6' 'motd=Mai Tycoon Local' > server.properties
fi
"$JAVA" -Xms512M -Xmx2G -jar paper.jar --nogui
