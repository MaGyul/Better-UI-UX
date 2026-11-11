# 스크립트 (JavaScript)

UI 문서에 JavaScript 를 붙여 버튼/입력 위젯의 반응을 만들 수 있다. 스크립트는 **데이터(JSON)만** 호스트와 주고받고,
할 수 있는 일은 **XML 요소 수정 · CSS 변수 · 허용된 마인크래프트 명령어**로 한정된다. `/uiux open scripting` 으로 데모를 볼 수 있다.

## 켜기

기본은 **꺼져 있다**(신뢰할 수 있는 UI 에서만 켜세요).

```toml
# config/better-uiux/config.toml
[scripting]
enabled = true
commands = "say"        # 스크립트가 실행할 수 있는 명령어의 첫 단어(쉼표로 구분). 비우면 모든 명령 거부
```

JavaScript 런타임(GraalJS)은 **선택 모듈 `script-graal`** 이다(약 37MB). 개발 실행(`runClient`)에는 자동으로 들어가고, 배포 jar 에는 들어 있지 않다.
런타임이 없으면 `<script>` 는 무시되고 경고만 나온다.

**배포/설치**: `./gradlew :script-graal:bundleJar` 가 `script-graal/build/libs/script-graal-<버전>-bundle.jar` 를 만든다
(GraalJS 의존성 + 우리 런타임 + Fabric/NeoForge 모드 정보가 든 모드 jar 하나, 모드 id `better_uiux_script`).
Better UI/UX 와 같이 `mods` 폴더에 넣으면 된다. 스크립트가 필요 없는 모드팩은 이 jar 를 빼면 된다.

## 문서에 연결

```xml
<screen>
  <script src="shop.uijs"/>                 <!-- config/better-uiux/ui/shop.uijs (없으면 기본 제공 리소스) -->
  <slider id="volume" onChange="script:volume"/>
  <button text="Buy" onClick="script:buy"/>
</screen>
```

`onClick`/`onChange`/`onSubmit` 값이 `script:함수이름` 이면 그 함수가 불린다. `<script>` 본문에 코드를 직접 쓸 수도 있다.
에디터의 이벤트 칸은 문서가 불러오는 스크립트의 함수를 `script:이름` 으로 자동 완성한다.

## 함수와 `ui` 객체

```js
function onInit(state) { }                 // 문서가 열린 직후 한 번(선택)
function buy(event, state) {               // event: 누른 요소, state: id 가 있는 모든 요소의 현재 값
  ui.setText('#status', state.name.value + ' 구매');   // event.id, event.tag, event.text, event.value, event.checked
  ui.setVar('accent', '#55FF55FF');
  ui.command('say hi');
}
```

`state` 는 `{ 요소id: {id, tag, text, value, checked} }` 모양의 순수 데이터다.

| 호출 | 설명 |
|---|---|
| `ui.setAttr(selector, name, value)` / `ui.removeAttr(selector, name)` | 속성 설정/삭제 (`src`, `template` 은 불가. `onClick` 류는 `script:` 값만) |
| `ui.setText(selector, text)` | 글자 바꾸기 |
| `ui.addClass` / `removeClass` / `toggleClass(selector, name[, on])` | 클래스 |
| `ui.setStyle(selector, name, value)` | 인라인 스타일 (`null` 이면 삭제) |
| `ui.setVar(name, value)` | CSS 변수 (`var(--name)` 를 쓰는 모든 요소에 반영) |
| `ui.append(selector, xml)` / `ui.remove(selector)` | 요소 추가/삭제 (`<script>`, `<include>`, `<use>` 는 추가 불가) |
| `ui.command(line)` | 마인크래프트 명령어(허용 목록에 있을 때만, 플레이어 본인 권한) |
| `ui.log(...)` / `console.log(...)` | 게임 로그에 남기기 |

`selector` 는 문서 선택자(`#id`, `.class`, `tag`, `a > b`)다. 맞는 요소가 여럿이면 모두에 적용된다(최대 200개).

## 한계와 보안

> **안내문(모드 설명/README 에 그대로 쓰세요)**: 스크립트는 샌드박스 안에서 실행되어 파일·네트워크·게임 내부에 접근할 수 없지만,
> JavaScript 런타임(GraalVM Community)이 **메모리 사용량 상한을 지원하지 않습니다.** 악의적으로 만든 스크립트가 메모리를 많이 쓸 수 있으므로,
> 스크립트는 **신뢰할 수 있는 UI 팩에서만** 켜세요(기본값은 꺼짐).

시작 속도: 런타임은 처음 켜는 데 1~2초 걸려서 **백그라운드에서 준비**한다. 화면은 바로 뜨고, 준비되는 동안 누른 스크립트 버튼은 "준비 중" 경고만 낸다.


- **샌드박스**: 자바 클래스/객체, 파일, 네트워크, 프로세스, 스레드, 환경 변수에 접근할 수 없다.
- **상한**: 호출 한 번에 문장 500,000개 / 300ms, 문자열 길이 1,000,000, 요청 64개, 명령 8개(초당 20개), 한 줄 256자.
  초과하면 그 호출만 거부/중단되고 경고가 잠깐 표시된다(스크립트 상태는 초기화될 수 있다).
- **메모리**: GraalVM Community 는 힙 상한을 지원하지 않는다. 악의적인 스크립트가 메모리를 많이 쓸 수 있으므로 **신뢰할 수 없는 UI 에는 켜지 마세요.**
- 명령은 **기본 거부**다. 서버/모드팩이 허용 목록을 명시해야 한다.
- 에디터 안에서는 스크립트가 실행되지 않는다(문서 구조만 편집).
