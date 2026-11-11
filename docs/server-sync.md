# 서버 UI 동기화 (M4.5)

서버가 클라이언트에 XML 화면을 내려보내고, 값을 바꾸고, 버튼 동작을 받는 기능. 설계 배경은 [server-sync-plan.md](server-sync-plan.md).

- 이 모드가 **양쪽에 모두** 있으면 서버 기능이 켜지고, 한쪽에만 있으면 서버 기능만 꺼진다(접속과 로컬 기능은 그대로).
- 채널: `better_uiux:sync`(NeoForge는 optional 채널이라 모드 없는 클라이언트도 접속 가능). 프로토콜 버전이 다르면 서버 기능을 끈다.

## 서버: 화면 배포

`config/better-uiux/server-ui/` 에 `*.uiml`, `*.uicss`, 이미지 등을 둔다(서버 시작 시 읽음). 이 폴더가 **번들**이다.
번들은 매니페스트(경로·sha256·크기)로 관리되고, 클라이언트는 **바뀐 파일만** 받아 `config/better-uiux/server-cache/<서버>/files/` 에 저장한다.
허용 확장자: xml, uicss, png, jpg, jpeg, gif, mp4, m4v, mov, js. 경로에 `..`, 절대경로, 역슬래시는 불가.

## 서버: 화면 열기와 값

```
/serverui open <플레이어> <화면id>      # 화면 id = server-ui/<id>.uiml (권한 레벨 2)
/serverui close <플레이어> <화면id>
/serverui state <플레이어> <키> <값>    # 문서에서 {server.<키>} 로 읽는다
```

모드 코드에서는 `ServerSync.open/close/set` 과 `ServerSync.core().registerAction(id, ActionSchema, handler)`.
값이 아직 없을 때 `{server.키}` 는 빈 문자열로 보인다.

## 버튼 동작

```xml
<button text="구매" onClick="server:shop:buy" arg-item="sword" arg-n="2" />
```

`arg-이름` 속성이 인자가 된다(정수/소수/true·false 는 해당 형으로, 그 외는 문자열). 서버는 등록된 **스키마**로 형 변환 후 검증하고,
등록되지 않은 동작·모르는 인자·범위 밖 값·열려 있지 않은 화면의 동작은 버린다.

```java
ServerSync.core().registerAction("shop:buy",
    ActionSchema.none().with("item", Arg.string(32)).with("n", Arg.integer(1, 64)),
    (playerId, args) -> { ... });
```

## 보안 규칙

- 클라이언트가 보낸 것은 모두 검증한다(크기·형식·개수 상한). 어긋나면 위반으로 세고, 위반이 10회를 넘으면(60초간 위반이 없으면 초기화) 그 클라이언트의 서버 기능을 끈다. 연타는 위반이 아니라 조용히 버린다.
- 서버 화면에서는 `script:` 가 기본 **꺼짐**이다(`config.toml` 의 `[server] allowScripts = true` 로 켤 수 있고, 클라이언트 `[scripting] enabled` 도 필요). 마인크래프트 명령은 어떤 경우에도 실행할 수 없다.
- 서버 화면은 인게임 에디터로 열 수 없다.
- 클라이언트→서버 패킷은 32KB 이하(로더/바닐라 한도)여서 번들 요청은 나눠 보낸다.

## 이미지와 영상: https 로 받기

번들(패킷)에 큰 파일을 싣지 않고, 서버/문서가 https 주소를 쓴다. 번들에는 xml/uicss 같은 작은 파일만 둔다.

```xml
<image src="https://cdn.example.com/banner.png#sha256=5776cd87...(64자리)" />
<video src="https://cdn.example.com/intro.mp4#sha256=..." />
```

- **핀(`#sha256=`)**: 받은 파일이 정확히 그 해시여야 한다(다르면 버린다). 기본 설정에서는 **핀이 있는 주소만** 받는다 — 서버가 내 IP 로 임의 사이트를 찌르거나 내용을 바꿔치기하지 못하게.
- 설정 `[media] remote = "pinned"`(기본) | `"on"`(핀 없는 주소도 허용, 24시간 캐시) | `"off"`(끄기).
- 안전 규칙: https 와 443 포트만, 도메인 이름만(IP 직접 입력/localhost/.local 금지), 이름이 풀린 주소가 하나라도 사설·루프백·링크로컬이면 거부,
  리다이렉트는 직접 따라가며 매번 같은 검사(최대 3회), 응답 형식(이미지는 `image/*`, 영상은 `video/*`)과 크기 상한(이미지 8MB, 영상 64MB), 시간 제한 15초.
- 백그라운드 스레드에서 받아 `config/better-uiux/remote-cache/` 에 둔다(전체 256MB 넘으면 오래된 것부터 삭제). 핀이 있는 캐시는 읽을 때마다 해시를 다시 확인한다.
- 알려진 한계: DNS 가 검사와 접속 사이에 다른 주소로 바뀌는 경우(재바인딩)까지는 막지 못한다(포트가 443 으로 고정되고 유효한 TLS 인증서가 필요해 영향은 제한적).

## 화면 닫힘 통지

서버가 연 화면을 클라이언트가 닫으면(버튼, Esc, 접속 종료 등) 서버에 알려, 서버는 그 화면의 동작을 더 받지 않는다.

## server-first / local-first 정책

서버 화면의 id 와 같은 로컬 파일(`config/better-uiux/ui/<id>.uiml`)이 있을 때 어느 쪽을 열지 정한다.

- **기본: local-first** — 내 파일이 이긴다. 플레이어가 서버 화면을 자기 취향대로 꾸밀 수 있다(서버 값과 서버 동작은 그대로 동작).
- 서버는 `config.toml` 의 `[server] policy = "server-first"`(기본) 또는 `"local-first"` 로 자기 의도를 알린다.
- 서버가 server-first 를 요청해도 클라이언트가 `[server] allowOverride = true` 로 허락해야만 서버 파일이 이긴다(서버가 로컬 설정을 마음대로 덮지 못하게).

## 권한 노드

| 노드 | 용도 | 기본 |
|---|---|---|
| `better_uiux:publish` | 에디터에서 서버로 발행 | 권한 레벨 4 |
| `better_uiux:serverui` | `/serverui` 명령 | 권한 레벨 2 |

- **Fabric**: Fabric API 의 permission-api-v1 을 쓴다(LuckPerms 같은 권한 플러그인이 이 API 를 제공한다). 별도 라이브러리는 필요 없다.
- **NeoForge**: PermissionAPI 노드로 등록한다(권한 핸들러가 값을 주지 않으면 기본 레벨).
- 노드 값이 없거나 확인 중 오류가 나면 기본 레벨로 판단하거나(값 없음) 거부한다(오류).
- 콘솔/명령 블록은 노드가 없으므로 기본 레벨로 판단한다.

## 에디터에서 서버로 발행

- 에디터 도구 모음의 **Pub** 버튼(저장 후 발행) 또는 클라이언트 명령 `/uiux publish <id>`. 서버에 모드가 있어 서버 기능이 켜져 있을 때만 쓸 수 있다.
- 올라가는 파일: `ui/<id>.uiml` 과 그 문서가 `<include src>` / `<link href>` 로 쓰는 로컬 파일(최대 64개, 합계 2MB). 이미지/영상은 올리지 않는다(https 주소 사용).
- **권한**: 권한 노드 `better_uiux:publish`(기본 권한 레벨 4). 권한 플러그인(예: LuckPerms)이 있으면 그 노드로 개별 허용/거부할 수 있고, 값을 주지 않으면 기본 레벨로 판단한다. 시작할 때와 저장할 때 모두 서버가 확인하고, 거부는 위반으로 센다.
- 서버는 파일 경로/해시/크기를 다시 검증하고 `server-ui/` 에 원자적으로 저장한 뒤 번들을 갱신해, 접속 중인 모든 클라이언트가 바뀐 파일만 새로 받는다. 서버 로그에 발행자와 파일 목록이 남는다.

## 알려진 한계

- 에디터 발행은 파일 단위 추가/덮어쓰기만 한다(서버 파일 삭제는 직접).
- 서버 번들 안의 이미지/영상 파일(png, mp4)은 아직 `file:` 로 쓸 수 없다(https 를 쓰세요).

## 테스트

- 자동 시험(사람 조작 없이): 서버를 띄운 뒤 클라이언트를 `JAVA_TOOL_OPTIONS=-Dbetteruiux.serverselftest=true` 로 실행하면, 접속→`/serverui open`→버튼 클릭→연타→닫기를 스스로 수행하고 `[selftest] PASS/FAIL` 을 로그에 남긴 뒤 종료한다(`ServerSelfTest`, 개발용).

- 코어: `SyncProtocolTest`, `SyncIntegrationTest`(클라이언트↔서버를 실제 코덱으로 연결).
- 실제: `./gradlew :fabric:runServer` 후 `:fabric:runClient --args="--username Dev --quickPlayMultiplayer localhost:25565"`,
  NeoForge는 `:neoforge:runServer`, `:neoforge:runClient -PquickPlay=localhost:25565`. 게임에서 `/serverui open Dev shop`
  (개발용 서버는 `server.properties` 의 `online-mode=false`, `ops.json` 에 Dev 를 레벨 4 로 등록). 자동 시험은 닫힘 통지, 발행(권한 노드 거부·허용[Fabric: `-Dbetteruiux.perm.file` 개발용 제공자]/레벨 4/deop), 정책(local-first/server-first), https 이미지(핀/핀 없음/내부 주소/설정 on·off)까지 확인한다(Fabric 25/25, NeoForge 24/24 — NeoForge 는 개발용 권한 제공자가 없어 노드 거부 항목만 빠진다).

## 검증 기록 (2026-11-11, Minecraft 26.3 / Fabric)

개발용 서버(`:fabric:runServer`)에 접속한 클라이언트가 스스로 시험하는 `ServerSelfTest` 로 확인했다(24개 모두 통과).

- **실제 LuckPerms 5.5.85**: 서버의 `fabric/run/mods` 에 넣고 콘솔에서 `lp user Dev permission set better_uiux.publish ...` 를 바꿔 가며 확인했다.
  노드 `false` 이면 관리자(레벨 4)여도 발행 거부, `true` 이면 허용, 노드를 지우면 기본 레벨로 돌아가며, 관리자가 아닌 계정은 노드가 `true` 일 때만 발행된다.
  참고: LuckPerms 는 관리자(op)에게 `lp` 명령 권한을 주지 않으므로 클라이언트 채팅이 아니라 **서버 콘솔**에서 실행해야 한다
  (시험은 `-Dbetteruiux.selftest.luckperms=true|nonop` 으로 실행 폴더의 `lp-cmd.txt` 에 명령을 쓰고 바깥에서 콘솔에 넣어 준다). 확인 뒤 jar 는 `run/mods` 에서 지운다.
- **모드가 없는 클라이언트**: Fabric/이 모드가 전혀 없는 순정 클라이언트(마인크래프트 26.x 는 난독화가 없어 라이브러리만으로 직접 실행된다)가 접속해도 서버는 멀쩡하고
  (예외 없음, 접속 유지), `/serverui open <플레이어> <id>` 는 "클라이언트에 서버 UI 기능이 없습니다" 로 깔끔히 거절한다.
  `-Dbetteruiux.noclientsync=true` 로 같은 상황(채널도 인사도 없는 클라이언트)을 개발 클라이언트로 흉내 낼 수도 있다.
  순정 클라이언트를 새 게임 폴더에서 처음 띄우면 접근성 안내 화면이 자동 접속(quick play)을 막으므로 `options.txt` 에 `onboardAccessibility:false` 를 먼저 적는다.
- **여러 명**: 서버 동기화가 켜진 클라이언트 둘(Dev, Dev2)과 동기화 채널이 없는 클라이언트 하나(Plain)가 동시에 접속한 상태에서 전체 시험을 돌렸다.
  Dev 가 서버 UI 를 발행하면 Dev2 의 서버 캐시(`server-cache/…/files`)로 새 번들이 도착하고, 서버 로그에 예외는 없으며 세 명 모두 접속이 유지됐다.
- 서버 UI 닫힘 통지는 플레이어가 많아지면 로그가 도배되므로 의도적으로 로그를 남기지 않는다.
- 참고(시험 가정): 데모 액션 `demo:ping` 의 `clicks` 는 서버가 켜져 있는 동안 이어지는 전체 카운터라, 시험은 한 번 눌러 기준을 잡은 뒤 +1 을 확인한다.
