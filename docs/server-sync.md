# 서버 UI 동기화 (M4.5)

서버가 클라이언트에 XML 화면을 내려보내고, 값을 바꾸고, 버튼 동작을 받는 기능. 설계 배경은 [server-sync-plan.md](server-sync-plan.md).

- 이 모드가 **양쪽에 모두** 있으면 서버 기능이 켜지고, 한쪽에만 있으면 서버 기능만 꺼진다(접속과 로컬 기능은 그대로).
- 채널: `better_uiux:sync`(NeoForge는 optional 채널이라 모드 없는 클라이언트도 접속 가능). 프로토콜 버전이 다르면 서버 기능을 끈다.

## 서버: 화면 배포

`config/better-uiux/server-ui/` 에 `*.xml`, `*.uicss`, 이미지 등을 둔다(서버 시작 시 읽음). 이 폴더가 **번들**이다.
번들은 매니페스트(경로·sha256·크기)로 관리되고, 클라이언트는 **바뀐 파일만** 받아 `config/better-uiux/server-cache/<서버>/files/` 에 저장한다.
허용 확장자: xml, uicss, png, jpg, jpeg, gif, mp4, m4v, mov, js. 경로에 `..`, 절대경로, 역슬래시는 불가.

## 서버: 화면 열기와 값

```
/serverui open <플레이어> <화면id>      # 화면 id = server-ui/<id>.xml (권한 레벨 2)
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

## 알려진 한계

- 서버 번들 안의 이미지/영상은 아직 `file:` 로 쓸 수 없다(클라이언트 `images/` 폴더만 읽는다).
- 클라이언트가 화면을 직접 닫아도 서버는 열린 것으로 기억한다(서버 동작은 계속 허용된다).
- 정책(server-first/local-first)은 프로토콜에 있으나, 서버 화면은 항상 서버 캐시에서 열리므로 아직 구분이 없다.
- 에디터에서 서버로 발행하는 기능은 아직 없다.

## 테스트

- 자동 시험(사람 조작 없이): 서버를 띄운 뒤 클라이언트를 `JAVA_TOOL_OPTIONS=-Dbetteruiux.serverselftest=true` 로 실행하면, 접속→`/serverui open`→버튼 클릭→연타→닫기를 스스로 수행하고 `[selftest] PASS/FAIL` 을 로그에 남긴 뒤 종료한다(`ServerSelfTest`, 개발용).

- 코어: `SyncProtocolTest`, `SyncIntegrationTest`(클라이언트↔서버를 실제 코덱으로 연결).
- 실제: `./gradlew :fabric:runServer` 후 `:fabric:runClient --args="--username Dev --quickPlayMultiplayer localhost:25565"`,
  NeoForge는 `:neoforge:runServer`, `:neoforge:runClient -PquickPlay=localhost:25565`. 게임에서 `/serverui open Dev shop`
  (개발용 서버는 `server.properties` 의 `online-mode=false`, `ops.json` 에 Dev 등록).
