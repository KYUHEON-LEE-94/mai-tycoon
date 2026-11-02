# 클로드 작업 이어받기 — P0 지도 모드

2026-11-02

## 이어받은 상태

클로드가 작성한 Fabric 클라이언트와 Paper ClientLink를 바탕으로 진행했다. 통신 규격 1.0, 양쪽 codec 및 공통 바이트 테스트, M 키 지도, 플레이어/소유지 표시, 확대/이동, 필수 모드 접속 제한이 이미 있었다. 기존 개발 로그에서 localhost:25590 연결 성공과 지도 캡처를 확인했다.

## 이번 변경

- hello를 전송하고 답이 없으면 계속 기다리던 상태를 약 5초 후 재접속 안내로 바꿨다.
- 클라이언트는 성공 상태뿐 아니라 응답 major와 full_map 기능을 확인한다.
- 서버는 클라이언트가 제안한 기능과 지원 기능의 교집합을 응답하며, full_map 협의 없이 지도 요청을 처리하지 않는다.
- 모드 확인 대기 중 인벤토리 드래그와 양손 교체도 제한한다.
- `scripts/build-client.sh`: Loom에 필요한 로컬 JDK 25로 빌드한다. 산출물은 Java 21 대상이다.
- `scripts/package-client.sh`: 테스트/빌드 후 배포용 remap JAR, 설치 안내, 호환표, SHA256 체크섬만 ZIP에 넣는다. 개발용 JAR나 게임 디렉터리를 배포하지 않는다.
- `distribution/client/INSTALL.md`, `COMPATIBILITY.md`를 추가하고 README의 “미구현” 설명을 수정했다.

## 산출물과 검증

- 서버: `./build.sh -q` 성공, 110개 테스트 통과.
- 클라이언트: `./scripts/package-client.sh` 성공. 양쪽에서 공통 protocol/vectors 테스트 각각 5개 통과.
- `dist/mai-tycoon-client-0.1.0-preview.zip`: 수동 설치 시험용. Fabric Loader/API는 설치 안내에 따라 별도로 설치한다.
- 운영 서버의 플러그인 교체 및 `client.required: true` 전환은 이번 작업에 포함하지 않았다.

## 다음 작업

1. 실제 참가자의 정품 계정/Mac·Windows 설치와 재접속 검증. 필수 모드 대기·미설치·불일치 제한을 새 서버 빌드로 재확인.
2. P1: 램프 구매 로직을 클릭 처리에서 서비스로 분리하고, NPC → 모드 화면 → 서버 거래 → 결과 갱신을 연결.
3. requestId 중복 방지/거래 결과 조회는 기존 TransactionJournal·WalletLog를 재사용해 추가. 램프는 고정 가격이며 시세 quote는 일반 상점 단계에서 처리.
4. 검증 후 .mrpack 배포 및 필수 접속 정책 적용. 현재 ZIP을 자동 설치 모드팩이나 정식 운영 완성본으로 안내하지 않는다.

동시에 변경 중인 마을/상점 설계 코드는 보존했다. 모든 수정은 현재 작업 디렉터리에 있으며 별도 커밋하지 않았다.

## 개발 명령

```sh
./build.sh -q                 # 서버 Java 21 / Maven
./scripts/build-client.sh    # 클라이언트 JDK 25 / Gradle, 출력은 Java 21
./scripts/package-client.sh  # 미리보기 ZIP 생성 (Python 3 필요)
```

로컬 JDK가 없으면 각 스크립트에 맞는 JAVA_HOME을 지정한다. 클라이언트는 Loom 1.19.3 때문에 JDK 21로 빌드할 수 없다.

## 실제 화면 재확인

새 클라이언트로 기존 localhost:25590 시험 서버에 재접속했고 handshake ready → 지도 수신 → 화면 저장 → 정상 종료를 확인했다. 결과는 `target/client-check/screenshots/maitycoon-map.png`, 로그는 `target/client-check/run.log`에 있다. 개발 계정 특성상 프로필 키 401과 서명 채팅 경고가 나타난다. 정품 로그인 성공으로 해석하지 않는다. 이 실행은 JDK 25이며 서버의 이번 ClientLink 수정은 기존 시험 서버에 배포하지 않았다.
