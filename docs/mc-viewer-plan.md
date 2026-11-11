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
- [x] 2단계: 스크립트 경계 (`McvScriptTest`, `ScriptHostThreadingTest` 통과, `:fabric:compileJava` 확인)
  - `ScriptRuntime.setHostCallback`, Graal `mcv` 전역(`__host`는 전역에서 삭제, JSON 원본 함수를 잡아 위조 방지), `beginCall` 예산 리셋
  - `GraalScriptRuntime(long statements, long millis)` 생성자, `ScriptBridge`가 `<mc-viewer>` 문서에만 5,000,000 문장 / 1,500ms 적용
  - `ScriptHost.setThreading(script, main)`: 스크립트는 전용 스레드, 문서 변경은 `mc.execute`로 렌더 스레드. `tick(n)`은 바쁘면 드롭, `onTick(tick)` 메시지
  - 남은 것(후속): `config.toml [scripting]`의 `maxCallMillis/maxStatements/maxHostCalls` 노출, `UiScreen.tick()`에서 `Attached.tick` 호출(6단계 틱 시뮬레이션과 함께), 스크립트 문서 전체가 비동기 적용(한 프레임 지연)이 되어 기존 데모 인게임 확인 필요
- [x] 3단계: 위젯 골격 + 렌더 경로 (`McViewerClientGameTest`: 실제 클라이언트에서 스크린샷 픽셀로 확인, 등각 시점 육안 확인)
  - **렌더 방식 확정: 바닐라 GUI Picture-in-Picture**(`PictureInPictureRenderer`, 엔티티/책/배너를 GUI에 그리는 것과 같은 경로). 오프스크린 텍스처·깊이·라이팅은 마인크래프트가 맡고 `renderToTexture`에서 `SubmitNodeCollector`로 블록 모델(`submitBlockModel`)을 제출한다. 직접 RenderTarget을 만들 필요가 없다.
  - 로더별 등록: Fabric `PictureInPictureRendererRegistry.register`, NeoForge `RegisterPictureInPictureRenderersEvent`(각 진입점에 한 줄)
  - `McViewerWidget`(입력: 드래그 회전·휠 줌), `McViewerRenderState`, `McViewerPip`, `McViewerRegistry`(문서 → `McvHost`), `McGfx.addPictureInPicture`(비공개 필드 `guiRenderState`를 이름으로 읽음: 공개 메서드가 없음)
  - **한계/계획 변경**: PiP 투영은 정사영 고정이라 **원근(perspective)은 이 경로에서 불가**. 등각(ortho)만 지원(Ponder 방식). 원근이 필요하면 별도 렌더 타깃 경로가 필요하다(후속, 우선순위 낮음).
  - 알려진 미구현: 바이옴 틴트(잔디 색), 반투명 정렬, 숨은 면 컬링(사방이 솔리드로 막힌 블록만 건너뜀), 블록 엔티티/엔티티 렌더(5단계)
  - 데모: `/uiux open mc-viewer`(config.toml `[scripting] enabled = true` 필요), 게임 테스트 `McViewerClientGameTest`
- [x] 4단계: 블록 렌더(스파이크 결과: **가상 Level 없이 PiP + 블록 모델 제출로 충분**). 숨은 블록 건너뛰기, 잔디/잎 기본 틴트, 반투명 시트 선택까지.
  - 미구현: 블록 엔티티(상자 등은 아직 안 그려짐), 바이옴별 틴트, 반투명 블록 간 정렬, 면 단위 컬링
- [x] 5단계: 엔티티 (`McViewerEntities`, 게임 테스트로 소/돼지 렌더 확인)
  - 데이터 모델과 매 프레임 동기화(저널 불필요). 엔티티는 실제 월드에 추가하지 않고 만들기만 한다.
  - **제약: 엔티티를 만들려면 `ClientLevel`이 필요해서 월드에 접속하지 않은 상태(타이틀 화면)에서는 엔티티를 그리지 않는다.** 가상 `Level` 구현은 데이터팩 레지스트리(소 변종 등) 때문에 비용이 커서 보류.
  - 허용 필드: `customName`, `customNameVisible`, `invisible`; 메서드: `setPosition`, `setRotation`, `remove`
  - 알려진 문제: 엔티티 조명이 블록용 라이팅을 공유해 다소 어둡다(PiP 한 번에 라이팅 하나만 설정 가능)
- [x] 6단계: 틱 시뮬레이션 (`McvWorldTickTest`, 게임 테스트로 위젯→스크립트 `onTick`→문서 갱신 전체 경로 확인: 80틱 대기 후 레이블 78)
  - `McvWorld.advance(nowNanos, maxTicks)`: 위젯이 그려질 때 렌더 스레드가 시계를 돌린다. 위젯이 안 보이면 멈추고, 1초 넘게 안 불렸으면 따라잡지 않고, 한 프레임 최대 5틱. `setTickRate(0)`은 정지(0~100 tps, 기본 20).
  - 한 월드를 위젯 여러 개가 그려도 시간 기준이라 중복 진행되지 않는다.
  - 스크립트: `onTick(tick, worldId)`. 스크립트가 바쁘면 그 틱은 버려진다(쌓이지 않음).
  - 엔티티: `tickCount`와 틱 사이 보간(`partialTick`)으로 `ageInTicks` 애니메이션이 부드럽게 흐른다.
  - **범위 제한: 바닐라 `tick()`(물리/AI/블록 틱)은 돌리지 않는다.** 가상 월드는 데이터 모델이라 블록 엔티티·레드스톤·유체 시뮬레이션은 없고, 움직임은 스크립트가 `onTick`에서 직접 지시한다.
  - 버그 수정: `McvBlock` 상태 맵이 `Map.copyOf`로 순서를 잃어 문자열이 비결정적이던 문제
- [x] 7단계: 상호작용 (`McvViewTest` + 게임 테스트: 스크립트가 `project`로 알려 준 좌표를 실제로 클릭 → 맞는 블록, 호버 해제까지)
  - 변환 수식은 core `McvView`(렌더러와 같은 식)에 있고, 위젯이 그릴 때마다 월드에 마지막 뷰포트(사각형·배율)를 기록한다. 아직 그려진 적이 없으면 `project`/`raycast`는 null.
  - 스크립트: `world.project(x,y,z)` → `{x, y, depth}`(GUI 좌표, uiml 요소의 x/y와 같은 좌표계), `world.raycast(screenX, screenY)` → `{x,y,z,face,block,id}` 또는 null
  - 이벤트(전역 함수가 있을 때만): `onBlockHover(e)`(마우스 아래 블록이 바뀔 때, 없으면 `e.block == null`), `onBlockClick(e)`(드래그 없이 눌렀다 뗐을 때). `e = {world, x, y, z, face, block, id, button}`
  - 맞히기는 블록 칸 전체를 기준으로 한다(계단·판자도 한 칸). 엔티티는 아직 맞히지 않는다(후속).
  - 클릭과 드래그 구분: 4px 이상 움직이면 드래그(카메라 회전)로 보고 클릭 이벤트는 보내지 않는다.
- [ ] 8단계: 에디터·문서·데모
