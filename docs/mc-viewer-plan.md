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
entity.setPosition(~) / entity.customName = ~      // 엔티티에서 바로(메서드·필드 모두), 화이트리스트만. 소환 때 NBT: spawnEntity(x,y,z,type,nbt)
let camera = world.getCamera();
camera.setYaw(~); camera.setPitch(~); camera.setPosition(~);   // (원안의 setPotition 은 setPosition)
world.attach('#view');                             // id 가 view 인 <mc-viewer> 에 연결(뷰어마다 카메라가 따로: getCamera('#view'))
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
  - ~~한계/계획 변경: PiP 투영은 정사영 고정이라 원근은 이 경로에서 불가.~~ (원근은 아래 "원근 투영" 항목에서 해결됨) 등각(ortho)만 지원(Ponder 방식). 원근이 필요하면 별도 렌더 타깃 경로가 필요하다(후속, 우선순위 낮음).
  - 알려진 미구현: 바이옴 틴트(잔디 색), 반투명 정렬, 숨은 면 컬링(사방이 솔리드로 막힌 블록만 건너뜀), 블록 엔티티/엔티티 렌더(5단계)
  - 데모: `/uiux open mc-viewer`(config.toml `[scripting] enabled = true` 필요), 게임 테스트 `McViewerClientGameTest`
- [x] 4단계: 블록 렌더(스파이크 결과: **가상 Level 없이 PiP + 블록 모델 제출로 충분**). 숨은 블록 건너뛰기, 잔디/잎 기본 틴트, 반투명 시트 선택까지.
  - 미구현: 블록 엔티티(상자 등은 아직 안 그려짐), 바이옴별 틴트, 반투명 블록 간 정렬, 면 단위 컬링
- [x] 5단계: 엔티티 (`McViewerEntities`, 게임 테스트로 소/돼지 렌더 확인)
  - 데이터 모델과 매 프레임 동기화(저널 불필요). 엔티티는 실제 월드에 추가하지 않고 만들기만 한다.
  - (이 제약은 아래 "가상 레벨" 항목에서 해결됨) ~~엔티티를 만들려면 `ClientLevel`이 필요해서 타이틀 화면에서는 안 그려진다.~~ 가상 `Level` 구현은 데이터팩 레지스트리(소 변종 등) 때문에 비용이 커서 보류.
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
- [x] 8단계: 에디터·문서·데모
  - 에디터: 팔레트("기타" 탭, 라벨 "월드뷰"), 기본 스니펫, 속성 패널(`world` 번호), 태그 색. 에디터 안에서는 스크립트가 실행되지 않아 빈 상자로 보인다.
  - 설정: `config.toml [scripting] maxCallMillis / maxStatements / maxHostCalls`(범위 밖 값은 한계로 보정) — 2단계에서 미룬 항목 완료
  - 문서: `docs/widgets.md`(태그 표), `docs/scripting.md`(mc-viewer 섹션: API·이벤트·제약)
  - 데모 `mc-viewer.uiml/.uijs`: 체커 바닥, 둘레를 도는 돼지(`onTick`), 호버 표시, 좌클릭 블록 놓기/우클릭 부수기. 게임 테스트가 경고 없이 열리고 그려지는지 확인

## 후속 완료: `loadStructure`
- core `McvNbt`(NBT 읽기 + SNBT 쓰기, 크기/깊이/요소 상한), `McvStructure`(size/palette(s)/blocks 파싱, 월드 밖이면 아무것도 놓지 않고 거부), `McvHost` 명령 `loadStructure`(이름 검증, 파일 조회는 `StructureLoader` 로 분리해 core 는 IO 없음)
- 백엔드: `ScriptBridge.readStructure` — `config/better-uiux/structures/<이름>.nbt` 먼저, 없으면 `assets/better_uiux/structures/`
- 테스트: `McvStructureTest`(gzip/비압축, 오프셋, 월드 밖 거부, 깨진 파일/폭탄, 호스트 이름 검증, SNBT), `McvScriptTest`(JS API·오류), 게임 테스트(기본 데모가 `demo_house.nbt` 를 불러와 그려짐)
- 제약: 회전/반전 없음, 구조물 엔티티 무시

## 후속 완료: 블록 엔티티 렌더
- `McViewerBlockEntities`: `EntityBlock.newBlockEntity` 로 만들어 두고(블록 상태+NBT 가 바뀌면 재생성, 지워진 블록은 정리) `BlockEntityRenderDispatcher.tryExtractRenderState`/`submit` 으로 그린다. 거리 컬링은 `prepare(블록 중심)` 으로 무력화. 블록 NBT(SNBT)는 `loadWithComponents` 로 불러 준다.
- (월드 접속 제약은 "가상 레벨"에서 해결됨) 게임 테스트: 상자/엔더상자/셜커/침대/표지판 장면이 바닥만 있는 장면과 확연히 달라짐을 확인, 스크린샷 육안 확인

## 후속 완료: 엔티티 맞히기 + 엔티티 NBT
- 맞히기: `McvEntity.setBounds` 를 백엔드가 진짜 엔티티 크기로 채우면(= 실제로 그려진 엔티티) core `McvView.raycast` 가 충돌 상자(슬랩 교차)로 맞히고 블록과 거리(`t`)를 비교해 가까운 쪽을 고른다. `Hit` 에 `entity`·`t` 추가, 결과에 `kind`(block/entity). 위젯은 `onEntityHover/onEntityClick` 을 보낸다.
- NBT: `entity.mergeNbt(snbt)`(검증 후 큐, 최대 16개) → 렌더 스레드 `McViewerEntities.applyNbt` 가 현재 NBT 저장 → 병합 → `load` → 다시 저장해 스냅샷, `entity.getNbt()` 가 스냅샷을 읽는다. 매 프레임 동기화는 스크립트가 값을 준 필드(customName/invisible 등)만 적용해 NBT 로 바꾼 값을 덮어쓰지 않는다.
- 테스트: `McvViewTest`(엔티티 크기를 알기 전엔 안 맞음, 가까운 쪽 선택, 보기 영역), `McvHostTest`(NBT 검증/한도), 게임 테스트(소 호버·클릭 이벤트, 병합한 NBT 가 `getNbt` 에 보이고 새끼 소로 화면이 바뀜 — 스크린샷 확인)
- 알려진 제약: `Invisible` 은 이 버전에서 공통 NBT 키가 아니다(종류별 키만 유효), 이름표는 뷰어에서 안정적으로 안 보인다

## 후속 변경: 엔티티 API 단순화 + 소환 시 NBT
- 스크립트의 엔티티는 `entity.methods.X()` / `entity.fields.X` 가 아니라 **`entity.X`** 로 바로 쓴다(JS `Proxy`: 메서드 이름이면 함수, 아니면 필드 읽기/쓰기, `x y z`/`yaw pitch` 대입은 `setPosition`/`setRotation` 으로 변환). `methods`/`fields` 는 제거했다.
- `world.spawnEntity(x, y, z, type, nbt)` — `nbt`(SNBT 글자)는 소환과 함께 첫 병합 요청으로 걸린다. 올바르지 않으면 엔티티를 만들지 않고 거부한다.

## 후속 완료: 이름표 + 조명 선택
- 이름표: 바닐라는 `EntityRenderDispatcher.camera`(실제 플레이어 카메라)와의 거리로 표시 여부를 정해서 가상 좌표의 엔티티는 숨겨졌다. 렌더 상태의 `nameTag`/`nameTagAttachment` 를 직접 채우고, 카메라 방향은 `Ry(-yaw)·Rx(pitch)·Ry(π)` 로 줘야 글자가 똑바로 선다(뷰어 변환 = Z 180° 뒤집기 + 카메라 회전, 바깥에 z 를 뒤집는 배율이 있음 → 곱이 X 180° 가 되어야 함).
- 조명: 측정(밝기, 돼지/소/바닥/빨간 블록): ITEMS_3D 141/70/159/36, ENTITY_IN_UI 126/76/162/25, ITEMS_FLAT 154/87/163/45. 소가 어두운 건 대부분 소 텍스처 자체. 한 렌더에 조명 하나만 가능하므로 기본(3d)은 유지하고 `light="3d|flat|entity"` 속성으로 고르게 했다(에디터 속성 패널에도 있음).
- 테스트: 게임 테스트가 이름 유무 장면의 픽셀 차이(작은 글자 상자만큼), `light="flat"` 장면의 픽셀 차이를 확인.

## 후속 변경: 뷰어 id 연결 + 뷰어별 카메라(완전안)
- 월드와 뷰어의 연결을 숫자 번호(`world="1"` 속성) 대신 **요소 id**로 한다: `world.attach('#id')`, `mcv.createWorld(x,y,z,'#id')`, `world.detach('#id')`. 속성 `world` 는 제거했다.
- **카메라·뷰포트·보이는 월드는 뷰어(`McvViewer`)가 가진다**(월드의 `camera()` 는 뷰어가 붙기 전 설정을 담는 기본값, 뷰어가 월드를 처음 보여 줄 때 이어받음). `McvView.project/raycast` 는 `(world, viewer, …)` 를 받는다.
- 호스트(`McvHost`): 뷰어 맵·연결 맵(만든/연결한 순서), `resolveWorld(id)`(연결 > 연결이 하나도 없으면 첫 월드 > 없음), `primaryViewer(world)`, 선택자를 생략하면 대표 뷰어. 스크립트의 `getCamera/project/raycast` 는 선택자 인자를 받는다.
- 위젯: id 로 뷰어를 찾고(id 없으면 위젯마다 따로), 이벤트에 `viewer` id 를 싣고, 연결 안 된 뷰어는 문서 경고(attach 가 있는 문서에서만, 같은 경고는 한 번).
- 테스트: `McvViewTest`(뷰어 두 개의 투영/맞히기 독립, 카메라 이어받기), `McvHostTest`(연결 규칙, 카메라 명령 대상, project 조건), 게임 테스트(한 월드를 뷰어 a·b 가 다른 카메라로 그림: 픽셀 차이 7012, 각 뷰어 좌표 클릭 → 이벤트에 그 id, 연결 안 된 뷰어 경고)

## 후속 완료: 블록 엔티티 동작
- core: `McvWorld.blockEvent/blockEventsAt`(블록마다 최근 32개, 순번 `seq`, 블록이 바뀌면 기록 삭제), 명령 `blockEvent`, JS `world.blockEvent(x,y,z,id,param)`, `world.setOpen(x,y,z,open)`
- 백엔드: `McViewerBlockEntities` 가 (1) 안 보낸 이벤트를 순서대로 `triggerEvent`, (2) 월드 틱이 흐른 만큼(프레임당 최대 5) 클라이언트 틱(`ChestBlockEntity.lidAnimateTick` 등), (3) 추출할 때 `partialTick` 로 보간한다. 렌더러 인스턴스가 여럿이어도 같은 상태가 되도록 이벤트는 "상태 기록"으로 두고 블록 엔티티가 재생한다.
- **허용 목록**(상자/함정 상자/엔더상자/마법 부여대)만: 일반 틱/이벤트는 가상 좌표에서 진짜 `ClientLevel` 의 이웃 갱신·충돌 검사를 부를 수 있어 위험하다(셜커 상자의 `updateAnimation` 등).
- 테스트: `McvHostTest`(기록·순서·상한·삭제), `McvScriptTest`(API), 게임 테스트(상자·엔더상자 뚜껑이 열려 화면이 달라짐: 픽셀 차이 1669, 스크린샷 확인)

## 후속 완료: 타이틀 화면 엔티티(가상 레벨)
- `McViewerFakeLevel extends Level`: 모든 칸이 공기인 청크(`EmptyLevelChunk`), 빈 엔티티 조회, 소리/폭발/블록 갱신 무시, 레시피·시계·환경 속성은 호출되면 예외(엔티티 생성/렌더 경로에서는 안 불림). `ClientLevel` 생성자 대신 추상 `Level` 을 직접 구현해 연결(`ClientPacketListener`)이 필요 없다.
- `McViewerFakeWorld`: 접속 중이면 `mc.level`, 아니면 가상 레벨. 레지스트리는 바닐라 `WorldLoader` 가 하는 것과 같은 순서(태그 읽기 → `RegistryDataLoader.load(WORLD_REGISTRIES)`)로 **바닐라 데이터팩에서 백그라운드 1회** 로드(~0.2s). 태그 `apply()` 는 진짜 정적 레지스트리를 건드리므로 일부러 안 부른다. 가상 `Level` 은 만든 스레드를 자기 스레드로 기억하므로 로드 스레드가 아니라 렌더 스레드에서 만든다.
- 엔티티/블록 엔티티 캐시는 레벨이 바뀌면(진짜 ↔ 가상) 비운다.
- 게임 테스트: 월드를 닫아 타이틀 화면으로 간 뒤 돼지(분홍 픽셀 0 → 279)·소(데이터팩 변종)·상자가 그려짐, 스크린샷 확인. 로그 `virtual level registries loaded in 181 ms`
- 알려진 한계: 가상 레벨에는 `RecipeAccess`/`ClockManager`/`EnvironmentAttributeSystem` 가 없어 이를 요구하는 엔티티/블록 엔티티는 만들지 못하고 경고 후 건너뛴다(바닐라 몹 대부분은 문제없음). 진짜 월드에서는 영향 없음.

## 후속 완료: 셜커 상자·종 (블록 엔티티 동작 일반화)
- 가상 레벨(격리)이 생겨서 "허용 목록" 제한을 없앴다. 이제 **모든 블록 엔티티**에 대해 `EntityBlock.getTicker`(클라이언트 틱)와 `triggerEvent` 를 **가상 레벨에서만** 그대로 돌린다(진짜 레벨로 물러난 폴백에서는 안 돌림). 틱/이벤트가 예외를 던지면 그 블록 엔티티만 경고 후 동작 중지.
- 엔티티·블록 엔티티는 접속 중에도 항상 가상 레벨에서 만든다(준비 전 ~0.2s 동안은 건너뜀). 가상 레벨의 게임 시간은 월드 틱에 맞춘다(`McViewerFakeLevel.setTime`).
- 스크립트: `world.setOpen`(상자류·셜커 상자), `world.ringBell(x,y,z,dir)`(종), `world.blockEvent`
- 테스트: 게임 테스트(셜커 상자 열림 diff 1228, 종 흔들림 diff 44 + 스크린샷 확인, 기존 렌더 결과는 숫자까지 동일), `McvScriptTest`(ringBell 방향/오류)
- (소리 한계는 아래 "블록 엔티티 소리 + 월드 의존 블록 엔티티"에서 해결됨)

## 후속 완료: 원근 투영
- 기본 클래스의 투영은 정사영(pixel) 고정이지만, **`renderToTexture` 안에서 `RenderSystem.setProjectionMatrix` 를 다시 부르면** 이후 렌더 패스(`renderAllFeatures`)가 그 투영을 쓴다. 기본 클래스가 준 PoseStack(정사영용 픽셀 변환)은 버리고 새 PoseStack 에 `lookAt` 뷰를 넣는다.
- core `McvView`: `eye`(1인칭이면 지정 위치, 궤도면 중심을 감싸는 구가 들어오는 거리 `radius / sin(atan(tan(fov/2)·min(1, 가로세로비))) / zoom`), 기저 `basis`(오른쪽=−X″, 위=Y″, 앞=Z″: 정사영과 같은 회전), `project`/`ray` 의 원근 분기. 카메라 뒤는 `null`.
- **조명 뒤집힘**: 바닐라 조명 방향(`ITEMS_3D` 등)은 GUI 의 y-아래 뷰 공간 기준이라 y-위 `lookAt` 을 그대로 쓰면 윗면이 어둡고 옆면이 밝았다. 정사영 경로처럼 PoseStack 에 Z 180° 를 넣고 투영 행렬에 같은 회전을 곱해(`ProjectionMatrixBuffer.getBuffer(Matrix4f)`) 화면은 그대로 두고 조명만 바로잡았다. 이름표 방향은 `lookAt` 회전의 역(Z 보정은 투영이 상쇄).
- 테스트: `McvViewTest`(원근 궤도 중심, 가까울수록 크게, 왕복 투영/레이캐스트, 1인칭 위치, 줌=거리, 엔티티 맞히기), 게임 테스트(큐브: `project` 좌표 = 화면 빨간 픽셀 무게중심 (480,270)=(480,270); **중심에서 벗어난 돼지**: (437,259) vs (436,254) → 좌우/상하 반전 없음; project 좌표 클릭 → 그 블록, 스크린샷 확인)
- 알려진 한계: 원근에서 큰 월드는 먼 블록이 작게 보이는 것 외에 별도 LOD/컬링이 없다(시야각 밖 컬링 없음)

## 후속 완료: 바이옴 틴트
- core `McvWorld`: 월드 기본 바이옴 + 영역(`BiomeBox`, 최대 64개, 나중 것이 이김, 불변 목록을 통째로 교체), `setBiome/fillBiome/biomeAt/hasBiomes`, 호스트 명령 `setBiome/fillBiome/getBiome`, JS `world.setBiome/fillBiome/getBiome`. 바이옴을 하나도 정하지 않으면 렌더러는 이전과 똑같이 기본 색(계산 없음).
- 백엔드: `McViewerTintGetter`(최소 `BlockAndTintGetter`: 위치의 바이옴으로 `ColorResolver` 호출, 나머지는 빈 값)를 프레임마다 만들고, 틴트 블록은 바닐라 `BlockTintSource.colorInWorld(state, getter, pos)` 로 위치별 틴트를 계산한다(늪/어두운 숲의 잔디 색 변형, 모드 틴트 포함). 바이옴 홀더는 가상 레벨의 레지스트리에서 조회·캐시(레지스트리가 바뀌면 재생성). 알 수 없는 id 는 한 번 경고.
- 테스트: `McvHostTest`(전체/영역/겹침/해제/검증/상한), 게임 테스트(같은 잔디 바닥: 기본 vs 사막 vs 사막|정글 영역 — 색 차이 측정 + 스크린샷 확인)
- 알려진 한계: 이웃 바이옴 블렌딩 없음(경계가 또렷)

## 조사 완료: 반투명 블록 정렬 (코드 변경 없음, 회귀 테스트만 추가)
- 가설: 블록을 해시맵 순서로 제출하므로 반투명 블록(색유리 등)이 거리와 무관한 순서로 섞일 것이다.
- 실험: 빨간 유리(앞)와 파란 유리(뒤)를 겹쳐 놓고 양쪽 방향에서 비교(뒤에는 불투명 흰 벽: 처음엔 배경이 하늘이라 유리 중앙이 거의 투명해 측정이 틀렸다). **"먼 것부터" 정렬, 정렬 없음, "가까운 것부터"(일부러 거꾸로) 세 가지가 모두 완전히 같은 픽셀 결과**(r−b: 빨강이 앞 +4.0, 파랑이 앞 −15.0; 정사영·원근 동일)였다.
- 결론: **제출 순서와 무관하게 바닐라 렌더러가 반투명 지오메트리를 거리순으로 처리**한다. 그래서 만들었던 정렬 코드는 되돌렸다(효과 없는 코드는 두지 않는다).
- 남긴 것: 게임 테스트 `glass r-b`(빨강이 앞인 뷰가 파랑이 앞인 뷰보다 6 이상 더 붉어야 함, 정사영·원근) — 나중에 렌더 경로가 바뀌어 순서가 깨지면 잡아낸다.
- 확인하지 않은 것: 한 블록 안의 앞면/뒷면 순서, 3겹 이상의 복잡한 겹침(렌더러가 같은 방식으로 처리할 것으로 보이지만 따로 검증하지 않았다).

## 후속 완료: 블록 엔티티 소리 + 월드 의존 블록 엔티티
- **소리**: 바닐라에서 종/상자 소리는 서버가 따로 재생해서 클라이언트의 블록 이벤트(애니메이션)만으로는 안 난다. 그래서 `McViewerBlockEntities.eventSound` 가 이벤트(id 1)에 맞는 소리(상자/엔더상자/셜커 상자 열기·닫기 vol 0.5·pitch 0.9~1.0, 종 vol 2.0)를 낸다. 가상 레벨의 `playSeededSound`/`playLocalSound` 도 `McViewerSounds` 로 이어 틱 중 소리(비콘 등)가 난다.
- `McViewerSounds`: 위치 없는(relative, 감쇠 없음) `SoundSource.BLOCKS` 소리. `recent()`/`clearRecent()` 는 소리를 직접 못 듣는 테스트용 기록.
- **한 번만**: `McvWorld.BlockEvent` 를 record → 클래스로 바꿔 `claimSound()`(AtomicBoolean + 2초 넘은 것은 거부)를 둠. 렌더러 인스턴스가 여럿이어도, 블록 엔티티가 다시 만들어져 이벤트를 다시 받아도 소리는 한 번. 월드별 `world.setMuted(bool)`.
- **월드 의존**: `McViewerFakeLevel.bind(world)` — `getBlockState/getFluidState/getHeight`(기둥의 가장 높은 블록 + 1)를 뷰어 월드로 답하고 `hasNearbyAlivePlayer` 는 true(스포너 몹 표시/회전). 비콘은 이를 읽어 `levels`(피라미드)와 빔 구간을 정한다. 틱마다 게임 시간을 하나씩 올려(`setTime`) "80틱마다" 하는 동작(비콘 피라미드 재계산)이 건너뛰어지지 않게 함.
- **버그 수정(원인 규명)**: 비콘 빔이 안 나오던 진짜 원인은 `tryExtractRenderState` 의 마지막 인자가 "오프스크린 패스"라 렌더러의 `shouldRenderOffScreen()` 과 같을 때만 상태를 돌려주기 때문이었다(비콘처럼 오프스크린 렌더러는 `true` 로 따로 불러야 함). 일반 → 오프스크린 순으로 두 번 시도하도록 수정. (처음엔 월드 블록 미연결 탓으로 의심해 `bind` 를 먼저 넣었는데, 둘 다 필요했다: 비콘은 피라미드를 읽어야 `levels > 0`.)
- 점검한 블록 엔티티(경고/예외 없이 그려짐, 스크린샷 확인): 비콘, 스포너, 엔드 포탈, 엔드 게이트웨이(+빔), 전도체, 책장(책), 양조대, 모닥불, 주크박스, 장식 항아리, 해골/크리퍼 머리, 배너, 시련 스포너, 금고, 마법 부여대.
- 테스트(게임 테스트): 소리 4종이 각각 정확히 1번, 음소거 시 0번 / 비콘 피라미드 있음 vs 없음(차이 3035픽셀) / 스포너 몹 있음 vs 없음(확대한 장면, 차이 65픽셀) / `McvHostTest`(claimSound 한 번, setMuted)
- 알려진 한계: 점검 목록 밖의 블록 엔티티(모드 것 포함)는 개별 확인 안 함(예외를 던지면 그 블록만 경고 후 동작 중지)

## 유체(물·용암) 렌더링 (완료)
- `FluidRenderer.tesselate` 를 `submitCustomGeometry` 안에서 호출. 콜백 정점은 변환되지 않아 `McViewerFullBrightConsumer` 가 포즈(위치·법선)를 적용하고 조명을 최대로 고정한다(가상 월드엔 조명 엔진이 없어 안 그러면 새까맣다).
- 틴트 getter 가 뷰어 월드로 `getBlockState/getFluidState` 에 답하고, 바이옴이 없으면 평원의 물색을 쓴다. `RenderShape.INVISIBLE` 블록(물 등)은 모델 부위를 건너뛴다(누락 모델 큐브 방지).
- 물은 반투명 레이어, 용암은 불투명 레이어. 흐름 시뮬레이션은 없다.
- 테스트: 물 웅덩이 파란 픽셀 0 → 3080, 용암 주황 픽셀 0 → ~900 (게임 테스트).
- 알려진 한계: 전도체는 물·틀을 읽어 활성 상태로 틱이 돌지만 화면에서 몇 픽셀 크기라 자동 단언은 못 했다(스크린샷 conduit-active/inactive 로 육안 확인용).

## 엔티티 이동 모션 (완료, 엔티티 동작 1단계)
- 코어: `McvEntity.tick()` 이 월드 시계(`McvWorld.advance`)의 틱마다 이동 계획(`moveTo`/`walkTo`)과 속도(`setVelocity`)를 진행한다. 진행 방향으로 몸을 돌리고(틱당 최대 40°, `faceMovement=false` 로 끔), 직전 틱 값을 남겨 렌더가 `entityPartial()`(시계가 멈추면 1)로 위치·회전·머리를 보간한다.
- 머리: `lookAt` 은 머리 yaw/pitch 만 바꾼다(`setRotation` 하면 머리가 다시 몸을 따른다).
- 백엔드: `McViewerEntities` 가 보간한 값을 진짜 엔티티에 넣고, 지난 틱 이동 속도로 `walkAnimation.update` 를 월드 틱마다 돌려 걷는 애니메이션이 나온다.
- 2단계(물리)는 아래, 3단계(바닐라 AI)는 아직. 팔 휘두르기(`swing`)는 바닐라가 엔티티 틱에서 진행시키는 상태라 이번에 넣지 않았다.
- 테스트: `McvEntityMotionTest`(8개), `McvScriptTest`, 게임 테스트(걸어간 소 vs 선 소, 차이 516픽셀).

## 엔티티 물리 (완료, 엔티티 동작 2단계)
- 옵트인 `entity.physics = true`. `McvPhysics`(core)가 바닐라 `Entity.move` 와 같은 순서(세로 → 가로)로 축마다 충돌 상자를 잘라 내고, 땅에서 가로로 막히면 0.6 까지 계단을 오른다. 중력은 바닐라 식 `(vy - 0.08) * 0.98`.
- 충돌 모양은 `McvCollisionShapes`(core 인터페이스)로 주입한다: 백엔드 `McViewerCollision` 이 `BlockState.getCollisionShape`(빈 블록 getter, 상태별 캐시)를 넣고, 없으면(코어 단독) 공기가 아닌 블록은 꽉 찬 큐브.
- `moveTo/walkTo` 는 물리 중에는 가로(x,z)만 쓰고 높이는 중력·충돌이 정한다. `jump()`, `onGround` 추가. 월드 아래 y=-64 에 바닥을 둔다.
- 한계: 유체 부력/헤엄, 엔티티끼리 충돌, 넉백, 블록 안에 소환됐을 때 밀어내기 없음.
- 테스트: `McvEntityMotionTest` 물리 5개(낙하·벽·슬랩 오르기 vs 1칸 벽·점프·물리 끔), 스크립트 테스트, 게임 테스트(착지한 소 vs 공중의 소, 차이 614픽셀).

## 완료 후 남은 과제 (후속, 우선순위 순)
- 면 단위 컬링(큰 월드 성능), 월드 스냅샷을 프레임마다 복사하는 비용 줄이기
- NeoForge 실행 확인(컴파일만 확인됨)
