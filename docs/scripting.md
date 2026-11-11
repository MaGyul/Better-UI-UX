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

**뷰어 연결**: 스크립트가 월드를 요소 id 로 뷰어에 붙인다.

```xml
<mc-viewer id="front" width="150" height="130"/>
<mc-viewer id="top"   width="150" height="130"/>
```
```js
var world = mcv.createWorld(9, 6, 9);
world.attach('#front');                       // '#' 는 있어도 없어도 된다. mcv.createWorld(9, 6, 9, '#front') 로 한 번에도 가능
world.attach('top');
world.getCamera('#top').setPitch(80);         // 뷰어를 지정하면 그 뷰어의 카메라 — 같은 월드를 다른 각도로 동시에 볼 수 있다
world.detach('#top');                         // 연결 해제(그 뷰어는 빈 상자가 된다)
```
- **뷰어마다 카메라와 화면 위치(뷰포트)가 따로**다. 드래그/휠도 눌린 뷰어의 카메라만 바꾼다. 월드 하나를 여러 뷰어가, 뷰어 하나는 한 번에 월드 하나를 보여 준다(`attach` 를 다시 부르면 옮겨 붙는다).
- 선택자를 생략하면(`getCamera()`, `project(x,y,z)`, `raycast(sx,sy)`) **월드에 가장 먼저 연결한 뷰어**를 가리킨다.
  뷰어가 붙기 전에 `getCamera()` 로 설정한 값은 월드의 기본 카메라에 담겼다가 뷰어가 이 월드를 처음 보여 줄 때 이어받는다(그래서 `createWorld` 직후의 설정이 그대로 먹는다).
- 문서에 `attach` 가 **하나도 없으면** 가장 먼저 만든 월드를 모든 뷰어가 보여 준다(월드 하나짜리 간단한 문서는 연결 없이 동작).
  `attach` 를 한 번이라도 썼는데 연결 안 된 뷰어가 있으면 그 뷰어는 빈 상자로 보이고 문서 경고가 뜬다(id 를 잘못 썼을 때 알 수 있다).
- id 는 영문/숫자/`_ . : -` 64자 이하, 문서당 뷰어는 최대 32개, 월드는 최대 8개다. id 가 없는 뷰어는 스크립트가 가리킬 수 없다.

월드는 **Java 가 소유**하고 스크립트는 `mcv` 로 조작만 한다(`getBlock` 같은 읽기도 즉시 정확한 값). 월드는 저장되지 않고 서버로 동기화되지 않는다.

```js
function onInit() {
  var world = mcv.createWorld(9, 6, 9, '#view');           // 크기 만큼의 빈(void) 월드(각 축 최대 512, 블록 최대 1,000,000개). '#view' = id 가 view 인 <mc-viewer> 에 연결
  world.fill(0, 0, 0, 8, 0, 8, 'white_concrete');          // 두 모서리(포함)를 채운다
  world.setBlock(3, 1, 3, 'oak_stairs[facing=east,half=bottom]');   // 블록 상태는 문자열, 블록 엔티티 NBT 는 5번째 인자(SNBT 문자열)
  world.setBlocks([[1, 1, 1, 'stone'], [2, 1, 1, 'dirt']]);         // 여러 개를 한 번에: [x, y, z, 블록, nbt?]
  var r = world.loadStructure('demo_house', 0, 0, 0);      // 구조물 파일(.nbt)을 (x,y,z) 에 놓는다 → {size:[x,y,z], blocks, droppedNbt}
  var b = world.getBlock(3, 1, 3);                         // {id, block, nbt}. 비어 있으면 id 가 minecraft:air

  var cow = world.spawnEntity(4.5, 1, 4.5, 'cow');         // 엔티티 소환
  var calf = world.spawnEntity(2.5, 1, 4.5, 'cow', '{Age:-24000}');   // 소환하면서 NBT(SNBT 글자)를 적용한다
  cow.setRotation(135, 0);                                 // 엔티티에서 바로: setPosition(x,y,z), setRotation(yaw,pitch), remove()
  cow.customName = 'Bessie';                               // 필드도 바로: customName, customNameVisible, invisible
  cow.x = 5;                                               // x y z yaw pitch 는 읽고 쓸 수 있다(type, id 는 읽기 전용)
  cow.mergeNbt('{Age:-24000}');                            // 엔티티 NBT 병합(/data merge 와 같다): 새끼 소로. 아래 "엔티티 NBT" 참고
  var nbt = cow.getNbt();                                  // 마지막으로 본 엔티티 NBT(SNBT 글자). 아직 그려지기 전이면 null

  var cam = world.getCamera();                             // 이 월드를 보여 주는 (대표) 뷰어의 카메라
  cam.setYaw(225); cam.setPitch(30);                       // 기본은 등각(225°, 30°)
  cam.setZoom(1.2);                                        // 확대(클수록 가깝다)
  world.setViewArea(0, 0, 0, 8, 5, 8);                     // 이 영역(블록 좌표, 포함) 안의 블록·엔티티만 그리고 맞힌다
  world.setTickRate(20);                                   // 초당 틱(0~100, 기본 20, 0 이면 정지)
}
```

**구조물 파일(`loadStructure`)**: 마인크래프트의 스트럭처 블록이 저장하는 `.nbt` 파일을 그대로 쓴다(압축된 것·안 된 것 모두).
`world.loadStructure('이름', x, y, z)` 는 `config/better-uiux/structures/이름.nbt` 를 먼저, 없으면 기본 제공 리소스(`demo_house`)를 읽어 그 위치(구조물의 가장 작은 모서리)에 놓는다. 하위 폴더는 `'castle/tower'` 처럼 쓴다.
- 구조물이 월드 밖으로 나가면 **아무것도 놓지 않고** 오류가 난다(`try/catch` 로 잡을 수 있다). 이미 있는 블록은 덮어쓰고, 구조물의 빈 칸은 건드리지 않는다.
- 공기·구조물 빈칸(`structure_void`)·구조물 블록·직소 블록은 놓지 않는다. 구조물 안의 **엔티티는 무시**한다. 회전/반전은 지원하지 않는다.
- 블록 엔티티 NBT(상자 내용 등)는 글자(SNBT)로 보관하되 4096자를 넘으면 버리고 `droppedNbt` 에 센다. 파일은 압축 상태 2MB, 푼 뒤 16MB, 각 축 512칸까지.
- 파일 이름은 영문/숫자/`_ . / -` 만 가능하고 `..` 와 절대경로는 거부된다.

**틱과 이벤트**: 월드는 위젯이 화면에 그려지는 동안만 흐른다(안 보이면 멈춘다). 아래 전역 함수를 정의해 두면 불린다.

| 함수 | 언제 |
|---|---|
| `onTick(tick, worldId)` | 월드 틱마다. 스크립트가 바쁘면 그 틱은 건너뛴다 |
| `onBlockHover(e)` | 마우스 아래 블록이 바뀔 때. 블록이 없으면 `e.block == null` |
| `onBlockClick(e)` | 드래그 없이(4px 이내) 눌렀다 뗐을 때 |
| `onEntityHover(e)` | 마우스 아래 엔티티가 바뀔 때. 엔티티가 없으면 `e.entity == null` |
| `onEntityClick(e)` | 드래그 없이 엔티티를 눌렀다 뗐을 때 |

블록 이벤트 `e = {world, viewer, kind:'block', x, y, z, face, block, id, button}`, 엔티티 이벤트 `e = {world, viewer, kind:'entity', entity(번호), type, x, y, z, face, button}`(`viewer` 는 이벤트가 난 뷰어의 id, id 없는 뷰어면 `null`)(`x y z` 는 맞은 지점이 있는 칸). 엔티티가 블록보다 가까우면 엔티티 이벤트가 오고 그 자리의 블록 이벤트는 "블록 없음"이다. `face` 는 `up down north south east west`, `button` 은 0=좌 1=가운데 2=우(호버는 -1). 빈 곳을 눌러도 `onBlockClick` 은 불리며 `e.block == null` 이다.

**엔티티 NBT**: `world.spawnEntity(x, y, z, type, nbt)` 의 `nbt` 와 `entity.mergeNbt('{...}')` 는 요청한 NBT 를 진짜 엔티티에 **병합**한다(`/data merge entity` 와 같다: 현재 NBT 에 덮어쓰고 다시 불러온다). 새끼로(`Age`), 이름(`CustomName`), 색(`Color`, `variant`) 같은 **엔티티 종류별 NBT** 를 바꿀 수 있다.
- 소환할 때 준 NBT 는 첫 프레임에 적용된다(올바르지 않으면 엔티티를 만들지 않고 오류를 던진다). 적용은 렌더 스레드에서 이뤄지고, 엔티티를 만들 수 없으면(알 수 없는 종류 등) 적용되지 않는다. NBT 가 올바르지 않으면 로그에 경고하고 건너뛴다.
- `entity.getNbt()` 는 **마지막으로 본 값**을 돌려준다(처음 그려진 직후와 병합할 때마다 갱신). 그려지기 전에는 `null`. 클라이언트 쪽 값이라 서버와 다를 수 있다(예: 새끼 소의 `Age` 는 `-24000` 이 아니라 `-1` 로 나온다).
- 위치와 회전은 `setPosition`/`setRotation` 이 매 프레임 이긴다(NBT 의 `Pos`/`Rotation` 은 덮어써진다). 요청은 한 번에 16개까지 밀릴 수 있고 글자는 4096자 이하의 `{...}` 여야 한다.
- 이름(`customName`)이 있고 보이게 설정된(`customNameVisible`, 이름을 주면 기본 true) 엔티티는 머리 위에 이름표가 뜬다. 이름표는 항상 화면을 향한다.

**화면 좌표 변환**(말풍선·하이라이트를 uiml 요소로 얹을 때): `world.project(x, y, z[, '#뷰어'])` → `{x, y, depth}`(uiml 요소의 `x`/`y` 와 같은 GUI 좌표, 뷰어를 생략하면 대표 뷰어 기준),
`world.raycast(screenX, screenY[, '#뷰어'])` → 가장 가까운 블록 `{kind:'block', x, y, z, face, block, id}` 또는 엔티티 `{kind:'entity', entity, type, x, y, z, face}`, 없으면 `null`. 그 뷰어가 한 번도 그려지지 않았거나 뷰어가 없다면 둘 다 `null` 이다.

**조명**: `<mc-viewer light="3d|flat|entity"/>`. 한 번의 렌더에는 조명 하나만 쓸 수 있어 블록과 엔티티가 같은 조명을 나눠 쓴다.
`3d`(기본)는 블록 면마다 밝기 차이가 뚜렷해 입체감이 좋고, `flat` 은 전체가 더 밝고 평평해서(엔티티가 약 10% 밝아진다) 엔티티가 많은 장면에 맞고, `entity` 는 바닐라가 인벤토리에서 엔티티를 그릴 때의 조명이다.
(소가 어두워 보이는 것은 대부분 소의 기본 텍스처가 검정/갈색이기 때문이다.)

**카메라(정사영 / 원근)**: 기본은 Ponder 같은 **정사영**(등각)이다. `cam.setProjection('perspective')` 로 **원근 투영**으로 바꿀 수 있다(`'ortho'` 로 되돌림).

```js
var cam = world.getCamera();
cam.setProjection('perspective');
cam.setFov(60);                // 세로 시야각(도, 10~130, 기본 70) — 원근에서만 쓰인다
cam.setYaw(200); cam.setPitch(35);
cam.setZoom(1.5);              // 원근에서는 눈이 중심에 1.5배 가까워진다
cam.setPosition(2.5, 4, -6);   // 눈 위치를 직접 정한다(1인칭): 이때 yaw/pitch 는 "보는 방향"이다
```
- **궤도 모드**(기본, `setPosition` 을 안 부름): 눈이 월드(또는 `setViewArea` 영역)의 중심을 둘러싼 구가 화면에 꼭 들어오는 거리에 놓이고 yaw/pitch 만큼 그 둘레를 돈다. 드래그는 궤도를 돌리고 휠은 거리를 줄인다.
- **1인칭 모드**(`setPosition` 을 부름): 눈이 그 좌표에 고정되고 yaw/pitch 로 방향을 돌린다(yaw 0, pitch 0 = +z 를 봄). 정사영에서는 `setPosition` 이 무시된다.
- `project(x, y, z)` 는 원근에서도 같은 GUI 좌표를 돌려주고(깊이는 눈에서 앞쪽으로의 거리), **카메라 뒤의 점은 `null`** 이다. `raycast`, 호버/클릭, 엔티티 맞히기도 원근 광선(눈에서 화면 점을 지나는 선)으로 동작한다.
- 이름표는 두 투영 모두에서 화면을 향한다.

**알아둘 점**
- 엔티티와 **블록 엔티티**(상자·엔더상자·셜커 상자·침대·표지판·배너·머리 등 전용 렌더러가 그리는 블록)는 **월드에 접속하지 않은 타이틀 화면에서도** 그려진다. 접속 중이면 진짜 클라이언트 레벨로, 아니면 **가상 레벨**(블록도 엔티티도 없는 빈 세계)로 만든다.
  가상 레벨의 레지스트리(소 변종, 데미지 종류 ...)는 바닐라 데이터팩에서 **처음 필요할 때 백그라운드로 한 번** 불러온다(약 0.2~2초, 그동안 그 프레임의 엔티티만 건너뛰고 블록은 바로 보인다).
  엔티티는 어느 쪽 레벨에도 추가되지 않아 게임에 영향이 없고, 진짜 게임의 레지스트리/태그는 읽기만 한다. 접속/종료로 레벨이 바뀌면 엔티티는 새 레벨로 다시 만들어진다.
- 블록 엔티티에 `setBlock` 의 5번째 인자(SNBT)로 데이터를 줄 수 있다(예: 표지판 글자 `'{front_text:{messages:["Hello","","",""]}}'`). 읽지 못하면 로그에 경고하고 데이터 없이 그린다.
- **블록 엔티티 동작**: 월드 틱이 흐르는 동안 바닐라의 클라이언트 틱이 그대로 돈다(상자 뚜껑, 셜커 상자 열림, 종 흔들림, 마법 부여대의 책 ...).
  스크립트가 신호를 줄 수 있다:
  - `world.setOpen(x, y, z, true|false)` — 상자·함정 상자·엔더상자의 뚜껑, 셜커 상자를 열고 닫는다(인자를 생략하면 열기).
  - `world.ringBell(x, y, z[, dir])` — 종을 울린다(`dir` 은 `down up north south west east`, 기본 `north`. 종이 흔들리는 방향).
  - `world.blockEvent(x, y, z, id, param)` — 바닐라의 블록 이벤트를 그대로 보내는 낮은 수준 API(`setOpen` = `id 1`, `param 1/0`, `ringBell` = `id 1`, `param 방향 번호`).
  블록이 없는 곳에는 보낼 수 없고(오류), 블록이 다른 블록으로 바뀌면 이전 이벤트는 사라진다. 블록 엔티티가 다시 만들어져도 최근 이벤트를 순서대로 다시 받는다(블록마다 최근 32개).
- **격리**: 블록 엔티티와 엔티티는 항상 **가상 레벨**(블록도 엔티티도 없는 빈 세계)에서 만들고 돌린다. 바닐라 틱/이벤트가 이웃 갱신·충돌 검사·소리를 불러도 그 빈 레벨에서 끝나서 진짜 월드에는 아무 영향이 없다.
  틱이나 이벤트가 예외를 던지는 블록 엔티티는 경고를 남기고 그 블록의 동작만 멈춘다(그리기는 계속). 종 소리 같은 **소리는 나지 않는다**.
- 바이옴별 색, 물/레드스톤 같은 시뮬레이션은 없다. 움직임은 `onTick` 에서 스크립트가 직접 지시한다.
- `raycast` 는 블록 칸 전체를 맞는 것으로 본다(계단도 한 칸). 엔티티는 **실제로 그려진 엔티티의 충돌 상자**로 맞힌다(아직 그려지지 않은 엔티티는 맞지 않는다).
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
