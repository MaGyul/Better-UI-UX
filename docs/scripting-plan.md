# 스크립트 계획 (JavaScript / GraalVM)

> 상태: **S1~S4 구현됨**(사용법은 [scripting.md](scripting.md)). S5(배포 묶음, 탈출 시도 테스트)도 구현됨. 서버 정책 연동과 힙 상한(별도 프로세스)이 남아 있다.

## 목표

- UI 문서에 **스크립트**를 붙여 로직(값 계산, 이벤트 처리, 간단한 상태)을 XML/CSS 와 같은 파일 묶음 안에서 처리한다.
- 언어는 **JavaScript(GraalVM Polyglot, GraalJS)**.
- 스크립트는 게임/모드 내부 객체를 직접 만지지 못한다. **Java 와는 IPC 로, JSON 같은 "데이터 타입"만** 주고받는다.
- 스크립트가 할 수 있는 일은 딱 세 가지:
  1. **XML 요소 수정**(속성, 텍스트, 클래스, 스타일, 자식 추가/삭제) — 문서 DOM 에 대해서만
  2. **CSS 변수 조작**(`--accent` 같은 값을 바꿔 테마/상태 전환)
  3. **마인크래프트 명령어 실행**(`/say`, `/give` 같은 서버/클라이언트 명령, 권한은 플레이어 본인 것)
- 그 외(파일, 네트워크, 임의 Java 클래스, 다른 모드 API)는 불가.

## 먼저 하는 일(이미 반영됨)

- Java API 에 **CSS 변수 조작**: `UiDocument.setVariable("accent", "#FF55FF55")`, `variable("accent")`.
  스크립트 계층이 같은 기능을 그대로 노출하면 된다.
- 입력 위젯의 `onChange` 액션/`CHANGE` 이벤트 — 스크립트가 받을 이벤트 원천.

## 구조

```
 XML 문서 ──onClick/onChange="script:fn"──▶ ScriptHost (core, MC 비의존)
                                              │  JSON 메시지(IPC)
                                              ▼
                                 Script runtime (GraalJS 컨텍스트, 샌드박스)
                                              │  JSON 응답(요청 목록)
                                              ▼
                              ScriptHost 가 검증 후 실행: DOM 수정 / CSS 변수 / 명령어
```

- **샌드박스**: `Context.newBuilder("js").allowAllAccess(false)`, 호스트 클래스 접근 `HostAccess.NONE`, IO/스레드/네이티브 차단,
  `allowExperimentalOptions` 없음. 실행 시간/명령 수/메모리 상한(예: 이벤트 하나당 50ms, 프레임당 명령 64개).
- **IPC**: 스크립트 ↔ 호스트 사이는 **직렬화된 JSON 문자열**만 오간다(객체/함수/Java 참조 금지). 같은 프로세스 안의 별도 Context 라도
  메시지 경계는 JSON 으로 고정해, 나중에 별도 프로세스/스레드로 옮겨도 프로토콜이 바뀌지 않게 한다.
  - 호스트 → 스크립트: `{"type":"event","name":"change","target":"volume","value":"40"}`, `{"type":"tick","dt":16}`, `{"type":"init"}`
  - 스크립트 → 호스트: 요청 배열
    ```json
    [{"op":"setAttr","target":"#title","name":"text","value":"Hello"},
     {"op":"setVar","name":"accent","value":"#FF55FF55"},
     {"op":"addClass","target":"#btn","name":"active"},
     {"op":"command","line":"say hi"}]
    ```
- **허용 연산(화이트리스트)**: `setAttr removeAttr setText addClass removeClass toggleClass setStyle setVar append(xml) remove query(읽기) command`.
  `query` 같은 읽기는 요청-응답(비동기) 형태이며 결과도 JSON 데이터.
- **명령어**: 클라이언트에서 `/` 없는 한 줄. 허용 목록(config `scripting.commands.allow`, 기본은 빈 목록 → 서버/모드팩이 명시적으로 켜야 함),
  레이트 리밋, 한 번에 실행 가능한 개수 제한. 서버 정책으로 전체 차단 가능.

## 스크립트 작성 모양

문서에 연결:
```xml
<screen>
  <script src="shop.js"/>            <!-- 같은 폴더(또는 리소스팩) 파일, 핫리로드 대상 -->
  <slider id="volume" min="0" max="100" onChange="script:onVolume"/>
  <button text="Buy" onClick="script:buy"/>
</screen>
```
```js
// shop.js — 전역 `ui` 객체만 제공된다(데이터 타입만 주고받는 얇은 래퍼)
function onVolume(ev) {
  ui.setText('#label', '볼륨 ' + ev.value);
  ui.setVar('bar-color', ev.value > 80 ? '#FFFF5555' : '#FF55FF55');
}
function buy(ev) {
  ui.command('give @s minecraft:apple 1');
}
```

## 단계

| 단계 | 내용 | 규모 |
|---|---|---|
| S0 | `UiDocument.setVariable/variable` (완료) | S |
| S1 | `core`: ScriptHost 인터페이스 + JSON 메시지/요청 모델 + 요청 검증기(화이트리스트, 한도). 런타임 없이 **가짜 런타임으로 테스트** | M |
| S2 | `backend`/별도 모듈 `script-graal`: GraalJS 런타임(샌드박스, 시간 제한), `<script>` 태그 로드 + 핫리로드, `script:fn` 액션 연결 | M |
| S3 | 명령어 실행기(클라이언트 명령/서버 명령 전달) + 허용 목록/레이트 리밋/서버 정책 | M |
| S4 | 에디터 연동: `<script>` 요소 표시(읽기), 스크립트 오류 오버레이, 스크립트 파일 열기 | M |
| S5 | 문서/예제, 보안 점검(탈출 시도 테스트: 호스트 접근, 무한 루프, 거대 메시지) | M |

## 위험과 결정 사항

- **GraalVM 의존성 크기/호환**: GraalJS(polyglot) 는 수십 MB. Fabric/NeoForge 번들(JarJar/include)로 넣을지, **선택 모듈**(없으면 `<script>` 무시 + 경고)로
  분리할지 결정 필요 → **선택 모듈 권장**. Java 25 + 26.x 환경에서의 호환 확인이 첫 작업.
- **모드팩 보안**: 스크립트는 XML 처럼 "데이터"여야 한다. 서버 배포 UI 번들의 스크립트는 기본 비활성, 사용자가 명시적으로 허용.
- **성능**: 프레임마다 스크립트 호출은 금지(이벤트/타이머 기반). `tick` 은 선택 구독, 최소 간격 제한.
- **디버깅**: 스크립트 `console.log` → 게임 로그/에디터 오버레이, 예외는 문서 경고로 표시(문서는 계속 동작).

## 진행 상태

- **S1 완료**: `core.script` — `Json`, `ScriptRuntime`, `ScriptHost`(요청 검증/실행), `CommandPolicy`. 가짜 런타임으로 규칙 테스트.
- **S2 완료**: `script-graal`(GraalJS 25.0.5, 샌드박스, 문장 수/시간 한도, 한도 초과 시 컨텍스트 재생성). 게임에서 동작 확인(Fabric).
- **S3 완료**: 명령 실행(`ScriptBridge` → 플레이어 명령), 허용 목록/레이트 리밋. *서버가 클라이언트의 스크립트를 막는 정책은 M4.5(서버 동기화)와 함께.*
- **S4 완료**: 에디터의 `onClick`/`onChange`/`onSubmit` 칸에서 `script:함수` 자동 완성. 에디터에서는 스크립트를 실행하지 않는다.
- **S5 완료**: `:script-graal:bundleJar` 로 모드 jar 하나(약 37MB, Fabric+NeoForge 메타데이터, Truffle 서비스 파일 병합)를 만든다. 탈출 시도 테스트 추가(응답 위조, 요청 폭주, import/Java 접근, Atomics.wait, 재귀/정규식 폭탄).
- **남음**: 힙 메모리 상한(GraalVM Community 미지원 → 별도 프로세스로 옮길지 검토), 서버 정책(M4.5).
