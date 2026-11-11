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
# 아래는 <mc-viewer> 가 있는 문서에만 적용된다(생략하면 기본값). 범위를 벗어난 값은 가까운 한계로 맞춰진다.
maxCallMillis = 1500    # 호출 한 번의 최대 시간(ms). 100~30000
maxStatements = 5000000 # 호출 한 번의 최대 문장 수. 10000~100000000
maxHostCalls = 200000   # 호출 한 번에서 mcv 를 부를 수 있는 횟수. 100~5000000
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

## mc-viewer (가상 마인크래프트 월드)

`<mc-viewer/>` 는 스크립트가 만든 **가상 월드**를 실제 마인크래프트처럼(블록 모델, 텍스처, 라이팅, 엔티티 모델) 보여 준다. Create 의 Ponder 같은 화면을 만들 수 있다.
`/uiux open mc-viewer` 로 데모를 볼 수 있다.

```xml
<screen>
  <script src="scene.uijs"/>
  <mc-viewer id="view" width="240" height="180"/>
</screen>
```

월드는 **Java 가 소유**하고 스크립트는 `mcv` 로 조작만 한다(`getBlock` 같은 읽기도 즉시 정확한 값). 월드는 저장되지 않고 서버로 동기화되지 않는다.

```js
function onInit() {
  var world = mcv.createWorld(9, 6, 9);                    // 크기 만큼의 빈(void) 월드. 각 축 최대 512, 블록 최대 1,000,000개
  world.fill(0, 0, 0, 8, 0, 8, 'white_concrete');          // 두 모서리(포함)를 채운다
  world.setBlock(3, 1, 3, 'oak_stairs[facing=east,half=bottom]');   // 블록 상태는 문자열, 블록 엔티티 NBT 는 5번째 인자(SNBT 문자열)
  world.setBlocks([[1, 1, 1, 'stone'], [2, 1, 1, 'dirt']]);         // 여러 개를 한 번에: [x, y, z, 블록, nbt?]
  var b = world.getBlock(3, 1, 3);                         // {id, block, nbt}. 비어 있으면 id 가 minecraft:air

  var cow = world.spawnEntity(4.5, 1, 4.5, 'cow');         // 엔티티 소환
  cow.methods.setRotation(135, 0);                         // 메서드: setPosition(x,y,z), setRotation(yaw,pitch), remove()
  cow.fields.customName = 'Bessie';                        // 필드: customName, customNameVisible, invisible (+ x y z yaw pitch type 읽기)

  var cam = world.getCamera();
  cam.setYaw(225); cam.setPitch(30);                       // 기본은 등각(225°, 30°)
  cam.setZoom(1.2);
  world.setViewArea(0, 0, 0, 8, 5, 8);                     // 이 영역(블록 좌표, 포함) 안의 블록·엔티티만 그리고 맞힌다
  world.setTickRate(20);                                   // 초당 틱(0~100, 기본 20, 0 이면 정지)
}
```

**틱과 이벤트**: 월드는 위젯이 화면에 그려지는 동안만 흐른다(안 보이면 멈춘다). 아래 전역 함수를 정의해 두면 불린다.

| 함수 | 언제 |
|---|---|
| `onTick(tick, worldId)` | 월드 틱마다. 스크립트가 바쁘면 그 틱은 건너뛴다 |
| `onBlockHover(e)` | 마우스 아래 블록이 바뀔 때. 블록이 없으면 `e.block == null` |
| `onBlockClick(e)` | 드래그 없이(4px 이내) 눌렀다 뗐을 때 |

`e = {world, x, y, z, face, block, id, button}`. `face` 는 `up down north south east west`, `button` 은 0=좌 1=가운데 2=우(호버는 -1). 빈 곳을 눌러도 `onBlockClick` 은 불리며 `e.block == null` 이다.

**화면 좌표 변환**(말풍선·하이라이트를 uiml 요소로 얹을 때): `world.project(x, y, z)` → `{x, y, depth}`(uiml 요소의 `x`/`y` 와 같은 GUI 좌표),
`world.raycast(screenX, screenY)` → `{x, y, z, face, block, id}` 또는 `null`. 위젯이 한 번도 그려지지 않았다면 둘 다 `null` 이다.

**알아둘 점**
- 렌더링은 바닐라 GUI 의 Picture-in-Picture 로 한다. 그래서 **정사영(등각)만** 지원한다(원근 투영 없음).
- 엔티티는 **월드에 접속한 상태에서만** 그려진다(타이틀 화면에서는 블록만 보인다). 엔티티는 실제 월드에 추가되지 않아 게임에 영향이 없다.
- 상자 같은 블록 엔티티, 바이옴별 색, 물/레드스톤 같은 시뮬레이션은 없다. 움직임은 `onTick` 에서 스크립트가 직접 지시한다.
- `raycast` 는 블록 칸 전체를 맞는 것으로 본다(계단도 한 칸). 엔티티는 맞히지 않는다.
- 에디터 안에서는 스크립트가 실행되지 않으므로 `mc-viewer` 는 빈 상자로 보인다.
- 스크립트는 **전용 스레드**에서 돌고 문서 변경은 다음 프레임에 반영된다(한 프레임 늦게 바뀔 수 있다).
- 오류(범위 밖 좌표, 알 수 없는 블록 형식 등)는 JS 예외로 던져지니 `try/catch` 로 잡을 수 있다.

## 한계와 보안

> **안내문(모드 설명/README 에 그대로 쓰세요)**: 스크립트는 샌드박스 안에서 실행되어 파일·네트워크·게임 내부에 접근할 수 없지만,
> JavaScript 런타임(GraalVM Community)이 **메모리 사용량 상한을 지원하지 않습니다.** 악의적으로 만든 스크립트가 메모리를 많이 쓸 수 있으므로,
> 스크립트는 **신뢰할 수 있는 UI 팩에서만** 켜세요(기본값은 꺼짐).

시작 속도: 런타임은 처음 켜는 데 1~2초 걸려서 **백그라운드에서 준비**한다. 화면은 바로 뜨고, 준비되는 동안 누른 스크립트 버튼은 "준비 중" 경고만 낸다.


- **샌드박스**: 자바 클래스/객체, 파일, 네트워크, 프로세스, 스레드, 환경 변수에 접근할 수 없다.
- **상한**: 호출 한 번에 문장 500,000개 / 300ms(`<mc-viewer>` 문서는 위 설정값), 문자열 길이 1,000,000, 요청 64개, 명령 8개(초당 20개), 한 줄 256자.
  초과하면 그 호출만 거부/중단되고 경고가 잠깐 표시된다(스크립트 상태는 초기화될 수 있다).
- **메모리**: GraalVM Community 는 힙 상한을 지원하지 않는다. 악의적인 스크립트가 메모리를 많이 쓸 수 있으므로 **신뢰할 수 없는 UI 에는 켜지 마세요.**
- 명령은 **기본 거부**다. 서버/모드팩이 허용 목록을 명시해야 한다.
- 에디터 안에서는 스크립트가 실행되지 않는다(문서 구조만 편집).
