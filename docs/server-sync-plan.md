# M4.5 서버 동기화 설계

> 상태: **설계 제안(미구현)**. 구현 전에 아래 "결정할 것"을 정한다.

## 목표

모드가 서버에도 설치되어 있으면 서버가 UI 를 **배포**하고, **열어 주고**, **값을 실시간으로 보내고**, 클라이언트의 **버튼 동작을 받아 처리**한다.
서버에 모드가 없거나 클라이언트에 UI 가 없으면 바닐라처럼 동작한다(필수 모드가 아니다).

```
 서버 ── UI 번들(XML/CSS/이미지/영상 + 해시) ──▶ 클라이언트(캐시: config/better-uiux/server-cache/<서버>/)
 서버 ── open_ui(id, params) ───────────────────▶ 화면 열기
 서버 ── state(key, value) 델타 ────────────────▶ 바인딩 {server.key} 갱신
 서버 ◀─ action(id, args) ──────────────────────  onClick="server:mymod:buy"
```

## 현재 구조와 가장 큰 변화

지금 모드는 **클라이언트 전용**이다(`fabric.mod.json` `"environment": "client"`, NeoForge `@Mod(dist = CLIENT)`).
서버 기능을 넣으려면 서버에서도 로드되어야 하므로 진입점을 둘로 나눈다.

| 모듈 | 역할 |
|---|---|
| `core` | 프로토콜(메시지 인코딩/디코딩, 번들 매니페스트, 해시, 검증 규칙, 상한) — 마인크래프트 비의존이라 단위 테스트 가능 |
| `backend-mc26` | 클라이언트 쪽 처리(캐시, 화면 열기, 상태 바인딩, 액션 보내기) + 서버 쪽 처리(번들 서빙, 액션 검증, 상태 보내기) |
| `fabric` / `neoforge` | 공통 진입점(서버·클라 모두) 추가, 패킷 채널 등록(Fabric Networking API / NeoForge payload), 서버 명령 등록 |

모드를 "양쪽 설치, 클라이언트는 선택 사항(optional channel)"으로 만든다: 모드 없는 클라이언트는 접속할 수 있고 서버 UI 만 못 본다.

## 프로토콜

- **채널 하나**(`better_uiux:sync`), 모든 메시지는 JSON 이 아니라 **길이 제한이 있는 이진 레코드**(type + 필드)로 한다. 아래 메시지:
  - 핸드셰이크: C2S `hello(protocolVersion, hasCache: 번들 해시)`, S2C `hello(protocolVersion, bundleHash, policy)`. 버전이 다르면 서버 기능을 끈다.
  - 번들: S2C `bundle_manifest(hash, files[{path, sha256, size}])` → C2S `bundle_need(paths[])` → S2C `bundle_chunk(path, index, bytes)` (청크 16KB, 파일별 해시 검증 후 캐시에 원자적으로 저장).
  - 화면: S2C `open_ui(id, params)`, S2C `close_ui(id)`.
  - 상태: S2C `state_set(key, value)`, `state_clear(key)`, `state_snapshot(map)`. 값은 문자열/숫자/불리언만.
  - 동작: C2S `action(uiId, actionId, args)`.
- **상한**(위반하면 연결의 모드 기능을 끈다, 연결은 끊지 않는다): 번들 16MB / 파일 8MB / 파일 256개, 상태 키 256개·값 4KB, 청크 16KB,
  메시지 속도 제한(초당 N개), 파일 형식 허용 목록(`xml uicss png jpg gif mp4 js`).

## 보안 규칙 (가장 중요)

1. 서버가 보낸 것은 **데이터**다: XML, CSS, 이미지, 영상. 서버가 보낸 UI 의 **스크립트(js)는 기본 거부**(클라이언트 설정 `server.allowScripts=true` 로만 허용).
   서버 UI 는 **명령어를 실행할 수 없다**(스크립트가 허용돼도 서버 UI 에서는 `command` 비활성).
2. 경로는 번들 안의 상대 경로만, `..`·절대 경로·드라이브 문자 거부. 번들 파일은 `server-cache/<서버>/` 안에만 쓴다. 다른 서버의 캐시와 섞이지 않게 서버 id 로 구분한다.
3. 클라이언트 설정으로 서버 UI 를 **끌 수 있다**(`server.accept=false`, 서버별 허용/거부 목록). 외부 URL 리소스는 없다(`file:`/번들 안의 것만).
4. **서버는 클라이언트를 믿지 않는다**: 모든 `action` 은 서버에서 다시 검증한다 — ① 그 플레이어에게 그 UI 가 열려 있는가, ② 등록된 액션 id 인가,
   ③ 인자 스키마(타입/범위/길이), ④ 권한, ⑤ 쿨다운/속도 제한. 실패는 조용히 버리고 로그만 남긴다.
5. 서버 UI 와 클라이언트 로컬 UI 의 우선순위는 설정(`policy`): `server-first`(서버 번들이 같은 id 의 로컬 파일을 덮음) / `local-first`(로컬이 우선).
6. 컨테이너 화면(인벤토리 등)은 바닐라 `AbstractContainerMenu` 동기화를 그대로 쓰고 이 프로토콜로 복제하지 않는다(M5/M6 범위).

## 서버 쪽 API (모드 개발자용)

```java
ServerUi.registerBundle(Path folder);                         // 서버가 배포할 UI 폴더(또는 config/better-uiux/server-ui/)
ServerUi.open(player, "shop", Map.of("title", "상점"));
ServerUi.state(player).set("coins", 120);                     // 클라: {server.coins}
ServerUi.registerAction("mymod:buy", schema, (player, args) -> ...);   // XML: onClick="server:mymod:buy"
// 명령: /uiux open <player> <id> [params]   (권한 레벨 2)
```

## 단계(브랜치 `feat/m4.5-server-sync`)

| 단계 | 내용 | 규모 | 검증 |
|---|---|---|---|
| a | `core.sync`: 메시지 레코드와 이진 인코더/디코더, 번들 매니페스트·해시·경로 검증·상한, 액션 인자 스키마 검증 | M | 단위 테스트(변조/초과/경로 탈출 입력) |
| b | 공통(서버+클라) 진입점, 채널 등록, 핸드셰이크/버전 협상, 모드 없는 쪽과의 폴백 | M | `runServer` + `runClient` 로 접속 |
| c | 번들 배포(매니페스트→필요한 파일만 청크 전송→해시 검증→캐시), `server-first/local-first` | M | 캐시 적중 시 재전송 없음, 변조 청크 거부 |
| d | `open_ui` / `close_ui` / `state_*` + 바인딩 `{server.key}`, `/uiux open <player> <id>` | M | 서버 명령으로 클라 화면 열림, 값 실시간 갱신 |
| e | `action` + 서버 검증(UI 열림, 스키마, 권한, 쿨다운) | M | 위조 패킷/초과/미등록 액션 거부 테스트 |
| f | (나중) 에디터에서 서버 UI 편집 → 로컬 오버라이드 복사본, OP 전용 "서버로 게시(publish)" | M | |

각 단계는 Fabric/NeoForge 둘 다에서 확인한다. 서버 실행은 두 로더의 `runServer`(전용 서버)와 클라이언트 접속으로 한다.

## 결정할 것

1. **양쪽 설치 + 클라이언트 선택(optional)** 구조로 가도 되는가? (아니면 서버·클라 필수 모드)
2. 서버 UI 의 스크립트: **기본 거부**(권장) vs 서버 허용 목록으로 허용.
3. 전송 형식: **이진 레코드**(권장, 상한 강제가 쉽다) vs JSON.
4. `publish`(에디터 → 서버)는 이번 M4.5 에 넣을지 **뒤로 미룰지**(권장: f 로 분리해 뒤로).
5. 서버 플랫폼 우선순위: Fabric 서버 먼저 → NeoForge, 아니면 동시에.
