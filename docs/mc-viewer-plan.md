# `<mc-viewer>` — 가상 마인크래프트 월드 뷰어 계획

> 이 문서가 기준 계획이다. 구현 중 설계가 바뀌면 코드보다 먼저 이 문서를 고친다. 진행 상황은 맨 아래 체크리스트에 기록한다.

## 배경
uiml에서 스크립트(.uijs)로 가상 마인크래프트 월드(블록·엔티티·틱)를 만들고 Create의 Ponder처럼 보여주는 요소를 추가한다.
확정된 결정:

- 렌더링: **가상 Level + 바닐라 렌더러** (블록 엔티티·엔티티 모델·모드 렌더러까지 실제로 동작)
- 범위: **틱 시뮬레이션까지 전부** (위험이 커서 내부는 단계로 나눠 진행)
- 월드는 **Java가 단독 소유**하고 JS는 핸들로 원격 조작만 한다 (JS에 그림자 상태 없음)
- 스크립트는 **전용 스레드**에서 실행, 렌더 스레드 전용 작업은 렌더 스레드로 넘긴다
- 스크립트 실행 한도는 **상향**한다 (워치독 상한은 유지)

시작 시점의 코드베이스: 3D/블록/엔티티/오프스크린 렌더 없음(`docs/widgets.md`의 "아직 없는 것"), 스크립트는 JSON 연산 큐(`ui.*`), 스크립트 틱 훅 없음.

## 스크립트 API (목표 형태)
```js
let world = mcv.createWorld(x, y, z);              // 비어 있는(void) 월드. 저장하지 않는다
world.setBlock(x, y, z, "minecraft:oak_stairs[facing=east]", nbt); // 블록 상태는 문자열, 블록 엔티티 NBT는 별도 인자
let block = world.getBlock(x, y, z);
let entity = world.spawnEntity(x, y, z, "minecraft:cow");
entity.methods... / entity.fields...               // 화이트리스트만
let camera = world.getCamera();
camera.setYaw(~); camera.setPitch(~); camera.setPosition(~);   // (원안의 setPotition 은 setPosition)
world.setViewArea(~);                              // 영역 안의 블록·엔티티만 렌더
world.setTickRate(~);
```
추가: `fill`, `setBlocks`, `loadStructure`(일괄 API), `camera.setProjection`(ortho/perspective), `world.raycast`, `onBlockClick/onBlockHover`, `camera.project(x,y,z)`(Ponder식 말풍선 오버레이), 마우스 궤도·줌.

## 설계 원칙
- `UiDocument`는 final이라 `MCViewerDocument` 타입은 만들지 않는다. **`<mc-viewer>` = `UiWidget`** + 순수 Java **월드 모델**.
- 월드는 저장·서버 동기화(`sync/`) 대상에서 제외(일시적 상태).
- 모듈 경계 유지: `core`(MC 의존 없음)에 월드 모델·스크립트 명령·테스트, `backend-mc26`에 렌더/Level.

## 스레드 모델
- **스크립트 전용 스레드**(단일 스레드 실행기)에서 GraalJS 실행. Graal `Context`는 이 스레드에 고정. 기존 백그라운드 워밍업(`ScriptBridge`)을 이어받는다. 이벤트·`onTick` 호출은 이 큐로 들어가 순서 보장.
- **`McvWorld`(순수 Java, core)**: 스크립트 스레드가 유일한 쓰기 주체, 변경은 **저널**에 기록. `getBlock` 등 읽기는 즉시 반환.
- **렌더(클라이언트) 스레드**: 매 틱 저널을 drain해 가상 `Level`/엔티티에 반영(바닐라 객체는 이 스레드 전용). 렌더 스레드는 스크립트 스레드를 **절대 대기하지 않는다**. 스크립트 스레드가 렌더 결과가 꼭 필요하면(드문 엔티티 메서드 반환값) 타임아웃 대기, 그 외는 fire-and-forget.
- **UI 연산**(`ui.setAttr` 등)은 렌더 스레드에서 적용: 스크립트 결과를 큐로 넘겨 다음 프레임에 적용. `ScriptBridge`/`ScriptHost.call`을 비동기화하고 호출 순서 테스트를 추가.
- **워치독**: 한도 초과 시 Graal `Context.close(cancelIfExecuting)`로 스크립트만 중단(게임은 멈추지 않음) + 콘솔 경고.
- **`onTick`**: 월드 tickRate에 맞춰 스크립트 큐에 게시, 스크립트가 못 따라가면 밀린 틱은 병합/드롭.
- 저널이 한도를 넘으면(렌더 스레드가 안 비울 때) 저널을 비우고 `resync` 플래그로 전체 재동기화.

## 스크립트 경계 변경
- `ScriptRuntime`에 **동기 호스트 콜백 1개**(`host(json) -> json`)를 추가한다. 문자열만 오가고 Java 클래스는 노출하지 않으며, 기존 `ui.*` 연산 큐는 그대로 둔다. 샌드박스(클래스 접근 금지)는 유지.
- Graal에서는 `ProxyExecutable` 하나를 전역으로 바인딩한다(`HostAccess.NONE` 유지).
- BOOTSTRAP의 `mcv` 전역: `createWorld`는 월드 핸들 ID를 돌려주고, `world`/`entity`/`camera`는 ID를 들고 호스트 콜백을 호출하는 JS Proxy.
- 호스트 쪽 `McvHost`(core): 핸들 테이블, 명령 디스패치, 호출당 비용 카운트, 월드 크기/블록 수/엔티티 수 상한.
- `ScriptEscapeAttemptsTest`에 탈출 시도 케이스를 추가해 검증한다.

## 실행 한도
- 현재 호출당 300ms / 50만 구문(`GraalScriptRuntime.java` 상수, `ScriptHost.MAX_OPS` 등)을 mc-viewer 사용 시 완화한다.
- 기본값 예: 1500ms / 500만 구문. `config.toml [scripting]`에 `maxCallMillis`, `maxStatements`, `maxHostCalls`로 노출.
- 전용 스레드라 프레임 멈춤은 없지만 하드 상한(워치독)은 유지한다. 큰 월드는 `fill`/`setBlocks`/`loadStructure`(Java 측 일괄 처리)로 유도한다.
- 월드 크기·엔티티 수·호스트 호출 수 상한은 별도로 유지한다.

## 구현 단계
1. **월드 모델 + 스크립트 API (core)**: `core/.../mcv/`의 `McvBlock`, `McvWorld`, `McvEntity`, `McvCamera`, `McvHost`. 블록은 `"id[state=..]"` 문자열 + 선택적 `nbt`. 단위 테스트.
2. **스크립트 경계**: `ScriptRuntime` 호스트 콜백, `GraalScriptRuntime`의 `mcv` 전역, 한도 상향·설정, 전용 스레드·워치독, `onTick` 메시지, `ScriptHost` 비동기화.
3. **위젯 골격 (backend-mc26)**: `McViewerWidget implements UiWidget`, `UiApi.init`에서 `UiWidgets.register("mc-viewer", …)`. 오프스크린 렌더 타깃 → `DynamicTexture` → `drawTexture` (`ExternalVideos`의 선례).
4. **가상 Level + 블록 렌더 (스파이크 먼저)**: 클라이언트 전용 가상 `Level`, viewArea 안의 블록만 렌더, 조명, 등각/원근 카메라, 마우스 궤도·줌. 26.3 내부 API 의존이 크므로 작은 프로토타입으로 실현성부터 확인.
5. **엔티티**: 가상 Level에 엔티티 생성, `EntityRenderDispatcher`로 렌더, 필드/메서드는 화이트리스트 API로만 노출.
6. **틱 시뮬레이션**: `setTickRate`에 따라 Level 틱(블록 엔티티·엔티티·애니메이션). 위젯이 안 보이면 정지, 위젯당 틱 예산 상한.
7. **상호작용**: `raycast`, `onBlockClick/onBlockHover`, `camera.project`.
8. **에디터·문서·데모**: `EditorController.PALETTE_TAGS`·`EditorOps`·`PropertyModel` 등록, `docs/widgets.md`·`docs/scripting.md` 갱신, 데모 `mc-viewer.uiml/.uijs`(사진 같은 플랫폼 장면).

## 위험
- 26.3 내부 렌더/Level API 의존 → 4단계 스파이크에서 먼저 검증.
- 동기 호스트 콜백은 "JSON 전용" 경계를 한 지점 넓히므로 탈출 테스트 필수.
- 엔티티 메서드는 화이트리스트만, 월드 크기·엔티티 수·연산 수 상한 필수.

## 검증
- `./gradlew :core:test`, `:script-graal:test`: 월드 모델·명령 검증·골든(위젯은 DrawList 기록).
- 4~6단계: `fabric/src/gametest`의 클라이언트 GameTest로 렌더 결과/크래시 확인.
- 인게임 `/uiux open mc-viewer`로 데모 육안 확인.

## 진행 상황
- [x] 1단계: `McvBlock`, `McvCamera`, `McvEntity`, `McvWorld`, `McvHost` + `McvHostTest` (통과)
  - 빌드 메모: 이 PC에서 `gradle.properties`의 JDK 경로가 깨져 있으면 `-Dorg.gradle.java.home="C:/Program Files/Java/jdk-25.0.5.0.3"` 로 실행
- [ ] 2단계: 스크립트 경계(호스트 콜백, `mcv` 전역, 한도, 전용 스레드, 워치독, `onTick`)
- [ ] 3단계: 위젯 골격
- [ ] 4단계: 가상 Level + 블록 렌더(스파이크 포함)
- [ ] 5단계: 엔티티
- [ ] 6단계: 틱 시뮬레이션
- [ ] 7단계: 상호작용
- [ ] 8단계: 에디터·문서·데모
