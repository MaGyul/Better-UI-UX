# CSS3 확장 계획 (.uicss) — 2026-11-11 코드 재조사 반영판

## Context
`.uicss`는 CSS의 일부만 지원한다. 목표는 "CSS3 100%"가 아니라 **게임 GUI에 의미 있는 CSS3 부분집합을 최대한** 구현하는 것. 기준 브랜치는 `feat/editor-blank-and-lazy-new` (bdfe708 이후 152파일 변경: 위젯/grid/scroll/tabs, 이미지·GIF·mp4, script, server sync, 에디터 분리, 골든 테스트). 재조사 결과 **스타일 코어(Stylesheet/Selector/StyleResolver)는 거의 그대로**이고, 레이아웃/페인터/에디터 쪽이 커졌다.

## 확정된 결정 (2026-11-11, 미배포 모드이므로 하위호환 불필요)
- 색: CSS 표준 `#RRGGBBAA`로 전환 (`#AARRGGBB` 폐기). 이름은 CSS 표준값 사용, 기존 MC 팔레트 값은 `mc-red` 등으로 이동. 데모/번들/테스트/골든의 색 표기를 함께 마이그레이션.
- 우선순위: XML 속성 < 시트 < inline 유지, 단 `!important`는 inline보다 우선(스펙 정합).
- 기존 비표준 프로퍼티(`anchor justify align grow`)는 이번 단계에선 유지하고 Phase 3에서 표준 flex 속성과 병행/정리.
- 레이아웃은 flex + grid + position 중심, block/inline 흐름은 제외.

## 진행 상황 (작업 시 갱신)
- [x] Phase 0 기반 — 완료: 알 수 없는 속성 경고(편집 거리 기반 제안), `\s` 버그, 특이도 재설계(1000진 패킹), 시트 `!important` > inline > 시트, 괄호 인식 선택자 목록 분리(`Selector.splitList`, StyleText 반영), 값 처리기 `style/CssMath`. 프로퍼티 레지스트리(타입·상속 메타데이터 통합)는 이름 목록(`Stylesheet.KNOWN_PROPS`)으로 갈음했다.
- [x] Phase 1 선택자·캐스케이드 — 완료: `+ ~` 결합자, `:not/:is/:where`, `:nth-child/-last-child/-of-type/-last-of-type`, `:only-child/:first-of-type/:last-of-type/:only-of-type/:empty/:checked`, `[attr|=v]`·`i` 플래그, `inherit/initial/unset`. `@media`(width/height/aspect-ratio/orientation, min-/max-, 범위 문법, not/and/쉼표, 중첩), `@supports`(prop/selector()/not/and/or), `@import`(미디어 조건, 순환 방지, 가져온 시트가 앞 순서), 에디터 스캔이 조건 블록 안 규칙도 인식. `:focus`/`:focus-within`, 모든 단계의 `:hover/:active`(조상·형제 상태, `:not()` 안 포함), `::before/::after`+`content`.
- [x] Phase 2 값·단위·색 — 완료: `#RGB #RGBA #RRGGBB #RRGGBBAA`(CSS 순서로 전면 마이그레이션, 데모·테스트·문서 포함), `rgb/rgba/hsl/hsla/hwb`, CSS 색 이름 148개 + `mc-*` 팔레트, `currentColor`, 단위 `em rem pt pc in cm mm q vw vh vmin vmax`, `calc/min/max/clamp`(퍼센트 없는 식), 숏핸드 `border`, `border-width`, `margin-*`, `padding-*`. `%` 가 섞인 calc 는 크기 속성에서 레이아웃 단계에 평가(`Size` 의 percent+offset). 남음: `font`/`transition`/`background` 숏핸드
- [x] Phase 3 박스 모델·레이아웃 (퍼센트가 섞인 calc 제외) — `min-/max-*`, `box-sizing`, 테두리가 레이아웃 공간 차지(안쪽 여백에 더해짐, 골든 재생성), `margin:auto`(row/column 주축·교차축), flex 완성(`flex-wrap`, `grow`/`flex-grow`, `flex-shrink`(명시한 항목만), `flex-basis`, `flex` 숏핸드, `order`, `justify-content`/`align-items` 별칭, `space-around/evenly`, `row-gap`/`column-gap`), `display:flex`+`flex-direction`(모든 태그), `position: relative|absolute|fixed` + top/right/bottom/left(부모 상자 기준), `z-index`(그리기·히트 순서), `aspect-ratio`, grid(`grid-template-columns/rows`, `fr`, `repeat()`, `minmax()`, `grid-column/row`의 span·시작 줄, `justify-self`/`align-self`, 기존 `columns` 유지)
- [x] Phase 4 렌더링 — **`UiGfx` API 는 늘리지 않고** 기존 `fillRect`/세로 `fillGradient` 만으로 구현(`render/BoxPainter`): `border-radius`(행 단위 근사), `background: linear-gradient()`/`background-image`(다중 정지점, 가장 가까운 축), `box-shadow`(번짐 층 근사), 변별 테두리(두께·색), `border-style: dashed|dotted`, `outline`+`outline-offset`. 이유: McGfx 를 실기 검증 없이 건드리지 않고, 두 렌더러에서 동일하며 골든으로 검증 가능. 한계: 안티앨리어싱 없음, 둥근 클립 없음, inset 그림자·radial/conic 없음, 이미지 모서리 라운딩 없음
- [x] Phase 5 텍스트 — `layout/TextStyle`: `font-size`(배율), `line-height`, `letter-spacing`, `font-weight`(겹쳐 그리기), `white-space`+줄바꿈, `text-transform`, `text-overflow: ellipsis`, `text-decoration`, `vertical-align`, `text-shadow: none`. 한계: `font-family`/italic 없음, 위젯(textfield 등) 글자는 미적용, grid 칸 줄바꿈 높이 미반영
- [x] Phase 6 애니메이션 — 레이아웃 속성 애니메이션(`Interpolator.LAYOUT`): 스타일 해석 직후 훅(`LayoutEngine.setStyleAnimator`)으로 전환/키프레임/명령형을 덧입히고, 움직이는 동안 `UiDocument` 가 매 프레임 재배치. 그리기 속성에 `border-radius box-shadow outline-*` 추가, 여러 값(`margin: 4 8`)·색이 섞인 값 보간. `steps()` 이징. 남음: 다중 animation, `animation-play-state`
- [x] Phase 7 도구·문서 — 완료: `docs/uicss-reference.md`. 에디터 패널 접힌 섹션 3개(크기·배치 / 글자 / 효과, `PropertyModel`). 값 출처 툴팁(`StyleResolver.explain`), `:hover/:active/:focus` 강제 미리보기, 위젯 글자 스타일. 남음: `@media` 조건 평가 표시, 규칙 편집기의 신규 문법 안내 (에디터 `PropertyModel`/`StyleText` 신규 문법 대응 포함)

### 구현 메모
- `red` 등 색 이름은 이제 CSS 표준값(`#FF0000`). 옛 마인크래프트 팔레트는 `mc-red` 등으로 접근.
- `1em = 1rem = 8px`(마인크래프트 글꼴 높이). `vw/vh` 는 `LayoutEngine.layout(root, w, h)` 가 넘기는 뷰포트 기준.
- 이 환경의 `gradle.properties` 가 가리키는 JDK 경로가 없어, 로컬에서는 `./gradlew :core:test --offline -Dorg.gradle.java.home=<설치된 JDK 25>` 로 실행.

## 현재 상태 (검증됨)
- **At-rule**: `@keyframes`만. 나머지는 경고 후 무시 (`Stylesheet.java:47-54`). 선택자 목록은 단순 `split(",")` (`:56`) → `:not(a,b)` 불가.
- **선택자**: type/`*`/`#`/`.`/`[attr 연산자 5종]`, 결합자 descendant·child만, `:hover :active`(마지막 compound만, paint 오버레이), `:first-child :last-child :root :disabled :enabled`. 함수형 의사클래스는 `(`에서 파싱 에러 (`Selector.java:119`). 특이도는 십진 합산 (`:143-152`).
- **캐스케이드** (`StyleResolver.java:48-90`): 상속 → 태그 기본값(`separator/tab/dialog`, `:93-114`) → XML 속성 → 시트(!important/특이도/순서) → inline → `var()`. inline이 `!important` 시트를 이김(스펙 위반). 상속 목록은 `color shadow text-align cursor --*`. 숏핸드·`calc`·`inherit/initial/unset`·`display`(none만) 없음.
- **값**: `Size`(auto/fill/px/%), `Insets`(1~4값, px), `Colors`(`#RGB #RRGGBB #AARRGGBB`(알파 앞), 이름 10개, MC 팔레트값). 단위 em/rem/vw/vh 없음, 색 함수 없음.
- **레이아웃** (`LayoutEngine`): row/column 단일행 flex(wrap·min/max·shrink·basis 없음), **`grid`(columns 고정, 트랙 auto)**, **`scroll`**, **`tabs`**, stack(anchor), 리프 `label button image video spacer item separator`+위젯. `position`/`z-index`/`box-sizing` 없음. border는 레이아웃에 영향 없음. 텍스트 래핑·`font-*`·`line-height` 없음. 애니메이션은 paint 시점에만 적용 → 레이아웃 속성 애니메이션 불가.
- **렌더 프리미티브** (`UiGfx` 11개): `fillRect`, 세로 `fillGradient`, `drawTexture`, `drawText`(크기 인자 없음), `pushClip/popClip`, `pushTransform`(균일 scale), `blurBackground`, `drawItem`, `requestCursor`. 라운드/그림자/아웃라인/가로·방사 그라디언트/선 없음. 위젯 시각은 전부 하드코딩 `fillRect` (`BuiltinWidgets`), 위젯 색 키 `check-color fill-color bar-color`.
- **애니메이션**: `Interpolator.ANIMATABLE` 10개(`opacity translate-x/y scale rotate background color border-color tint border`), `steps()`·다중 animation·play-state 없음.
- **에디터**: `PropertyModel`(라벨/종류/rows 고정 목록), `StyleText`(위치보존 텍스트 편집, `<style>`·외부 `.uicss`·`@keyframes` 규칙 편집, 자체 `scan`이 keyframes만 인식, `matchingIn`이 `Selector.parse` 사용), 패널에 없는 프로퍼티는 "스타일 규칙" 자유 텍스트로만 편집. "값 출처 표시"와 `:hover` 강제 미리보기는 미구현(로드맵 P1).
- **스크립트/동기화**: `ScriptHost.setStyle`·`addClass`는 이름 정규식만 검사, 프로퍼티 화이트리스트 없음 → 새 프로퍼티는 자동으로 스크립트 사용 가능. 서버 번들에 `uicss` 포함(값 4096자 제한). 따라서 **새 값 문법이 보안 경계(값 길이, `url()` 외부 접근, `@import` 경로)에 영향**.
- **버그**: `split("\s+")`(단일 백슬래시)가 `Insets.java:14`, `Selector.java:216,239`에 남아있음. Java 25에서 `\s`는 공백 한 칸 이스케이프라 탭/개행 구분 불가(컴파일 에러는 아님). → `"\\s+"`.
- **테스트**: StylesheetTest 7, StyleCascadeTest 7, LayoutEngineTest 8, AnimationBasicsTest 7, 골든 스냅샷 6종(`core/src/test/resources/golden/*`, `-Dgolden.update=true`로 갱신), 에디터 스타일 관련 `StyleTextTest/EditorStyleRulesTest/EditorExternalStyleTest/EditorKeyframesTest`. 전체 @Test ≈457.

## 가능성 분석
| 등급 | 의미 | 항목 |
|---|---|---|
| A. 파서/캐스케이드만 | 즉시 가능 | 선택자 확장(`+ ~ :not :is :where :nth-* :only-child :empty :focus :checked ::before/after`), `@media`(뷰포트·GUI scale), `@import`, `@supports`, `initial/inherit/unset`, `calc/min/max/clamp`, 색 함수·`#RRGGBBAA`·CSS 색 이름, 단위(`em rem vw vh vmin vmax pt`), 숏핸드, 특이도 수정, inline vs `!important` |
| B. 레이아웃 확장 | 가능·큼 | `min/max-*`, `box-sizing`, border 공간 점유, `margin:auto`, flex wrap/shrink/basis/order, `position`, `z-index`, grid 확장(`fr`, `grid-template-*`, span), `aspect-ratio`, 레이아웃 속성 애니메이션 |
| C. 프리미티브 추가 | 가능 (4곳 수정) | `border-radius`, `box-shadow`, 요소 gradient(가로/대각), per-side border, `outline`, border-style, `background-image`, 비균일 scale |
| D. 텍스트 제한 | 부분 | `line-height`, `letter-spacing`, 줄바꿈/`white-space`, `text-overflow:ellipsis`, `text-decoration`, `font-weight`(MC bold), `font-size`(정수 배율) |
| E. 제외 | 불가/비효율 | `filter`, `backdrop-filter`(전체화면 blur만 존재), blend mode, skew/3D, 임의 `clip-path`, `@font-face`, `@container`, `:has()`(후순위) |

## 공통 변경 규칙 (재조사로 확인된 비용)
- **새 draw primitive** = `UiGfx` + `DrawCommand` + `DrawList` + `McGfx` 4곳 + 골든 파일 재생성. Fabric/NeoForge 모두 영향.
- **새 at-rule** = `Stylesheet.parse` + `StyleText.scan`(keyframes만 인식) 수정, 아니면 에디터가 무시/훼손할 수 있음.
- **새 선택자 문법** = `Selector.parse` + `StyleText.matchingIn`의 `,` 분리 로직 + 선택자 목록 분리를 괄호 인식으로 교체.
- **새 프로퍼티** = 프로퍼티 레지스트리 등록 + `PropertyModel`(라벨/ENUMS/NUMBERS/COLOR_KEYS/rows) + 필요 시 `Interpolator.ANIMATABLE`.
- **위젯**: 하드코딩 색·`fillRect`를 공용 박스 페인트 함수(radius/border/shadow 적용)로 통합해야 위젯에도 스타일이 먹는다.
- **보안**: `url()`/`@import`는 `UiSources` 경로 검증과 sync 번들 한도를 재사용, 원격 `url()`은 기존 `RemoteMedia` 정책을 따르고 기본 비허용.

## 단계별 로드맵
- **Phase 0 – 기반 (S/M)**: `\s` 버그, 특이도 튜플화, inline vs `!important`, 괄호 인식 콤마 분리 유틸(`Stylesheet`·`StyleText` 공용), **프로퍼티 레지스트리**(이름/타입/상속/초기값/애니메이션 가능/숏핸드 → `StyleResolver`·`PropertyModel` 공유), 토크나이저 기반 `ValueParser`(`calc`, 중첩 함수, 단위), 알 수 없는 프로퍼티 경고. 게이트: 기존 테스트·골든 무변경.
- **Phase 1 – 선택자·캐스케이드 (M)**: 결합자 `+ ~`, `:not/:is/:where`, `:nth-*`, `:only-child/:empty/:focus/:checked`, 상태 오버레이를 마지막 compound 외에도 허용, `::before/::after`(가상 노드), `inherit/initial/unset`, 상속 목록 확대, `@media`/`@import`/`@supports`, `StyleText` 동기 수정.
- **Phase 2 – 값·단위·색 (S/M)**: 색 함수, CSS 색 이름(MC 팔레트 이름은 `mc-*` 별칭 유지 또는 정책 결정), `#RRGGBBAA` 도입, 단위 확장, `calc/min/max/clamp`, 숏핸드(`border`, `margin-*`, `padding-*`, `background`, `transition`, `font`).
- **Phase 3 – 박스 모델·레이아웃 (L)**: `box-sizing`, border 공간 점유, `min/max-*`, `margin:auto`, flex 완성(wrap/shrink/basis/order), grid 확장(`fr`·`grid-template`·span), `position`+`z-index`, `aspect-ratio`. 기존 태그/속성(`anchor justify align grow columns`)은 별칭으로 유지. `LayoutEngineTest` 확장, 골든 영향 확인.
- **Phase 4 – 렌더링 (L)**: `fillRoundedRect`, 다방향 gradient, per-side border, `box-shadow`(근사), `outline`, border-style, `background-image/size/position`, 비균일 scale/`transform-origin`; 위젯 페인트를 공용 박스 페인터로 이전. 골든 갱신.
- **Phase 5 – 텍스트 (M)**: `TextMetrics`·`drawText` 확장(크기/배율, 폭 측정 기반 줄바꿈), `line-height`, `letter-spacing`, `white-space`, `text-overflow`, `text-decoration`, `font-weight/size`.
- **Phase 6 – 애니메이션 (M)**: 레이아웃 속성 애니메이션(애니메이션 값을 레이아웃 패스에 공급하도록 구조 변경 — 현재 paint 시점 전용), 신규 프로퍼티 보간, `steps()`, 다중 animation, `animation-play-state`.
- **Phase 7 – 도구·문서 (M)**: `docs/uicss-reference.md`(지원 매트릭스), 에디터 연동(패널 행 추가, 값 출처 표시, `:hover` 강제 미리보기, 신규 at-rule 인식), 스크립트/동기화 값 한도 점검, `editor-roadmap.md` 갱신.

## 위험 / 결정 필요
- `#AARRGGBB`→`#RRGGBBAA` 전환의 하위호환 정책(기존 `.uicss`/번들 깨짐).
- XML 속성이 시트보다 낮은 현재 우선순위 유지 여부(에디터의 "인라인 override" 로직과 결합).
- 레이아웃은 flex+grid+absolute 중심 권장(block/inline 흐름 제외).
- `:hover`가 레이아웃에 영향 주려면 재레이아웃 필요(현재 paint 전용).
- 위젯 하드코딩 색과의 충돌, 골든 대량 갱신.
- 원격 `url()`/`@import`와 서버 sync 보안 모델.
- Vulkan/GL 양쪽에서 rounded/shadow 근사의 외관·성능 검증.

## 핵심 파일
`core/.../style/{Stylesheet,Selector,StyleResolver}.java`, `layout/{StyleMap,LayoutEngine,LayoutBox,Size,Insets,Colors}.java`, `render/UiPainter.java`, `doc/BuiltinWidgets.java`, `anim/{Interpolator,AnimationEngine,TransitionSpec,AnimationSpec,Easing}.java`, `api/{UiGfx,DrawCommand,DrawList,TextMetrics}.java`, `backend-mc26/.../McGfx.java`, `edit/{PropertyModel,StyleText,EditorOps}.java`, `script/ScriptHost.java`, `sync/SyncLimits.java`, 테스트 `core/src/test/.../{style,layout,anim,render,edit}`, 골든 `core/src/test/resources/golden/`.

## 검증
- 단계마다 `./gradlew :core:test`(기존 통과 유지 + 신규 표 기반 테스트), 렌더 변경은 골든 diff 검토 후 `-Dgolden.update=true`.
- 에디터: `StyleTextTest`·`EditorStyleRulesTest` 회귀, 신규 문법이 규칙 편집을 깨지 않는지.
- 실기: `backend-mc26`의 `demo.uicss`/위젯 데모에 신규 기능 쇼케이스, Fabric client GameTest(`EditorClientGameTest`), Vulkan/GL 확인.
- 레이아웃 메모: `flex-shrink` 기본값은 CSS(1)와 달리 0(기존 문서 호환). `position:fixed` 는 absolute 와 동일(부모 기준). absolute 의 기준은 항상 바로 위 부모.

## 남은 일 (2026-11 기준)

구현됨(최근): **파일 확장자 변경** — 레이아웃 `.xml` → `.uiml`, 스크립트 `.js` → `.uijs`(스타일은 `.uicss` 그대로; 예전 이름은 읽지 않음), `content`/`cursor`/`font-family`/`text-decoration` 값 검증, 에디터의 **중첩 규칙 만들기**(규칙마다 `+ 중첩` 행: `&:hover` / `.title { color: red }`), **CSS 중첩**(`a { &:hover { } .title { } > .x { } @media (…) { } }` — 파서가 평평한 규칙으로 풀고, 에디터 규칙 편집도 중첩 블록 안에서 값 고치기·선언 추가를 지원, XML `<style>` 안의 단독 `&` 는 자동으로 `&amp;` 처리), `transition/animation/grid-template-*/flex` 값 검증, 규칙 추가 칸의 속성 이름·값 **자동 완성**(Tab; 앞선 선언은 유지), 값 검증을 숏핸드(`margin padding border border-radius box-shadow background font-weight outline`)까지 확대, 이미지/영상의 둥근 모서리·`inset` 그림자(번짐·오프셋·spread)·임의 각도 `linear-gradient(Ndeg | to top right …)`·`repeating-linear/radial-gradient`·`radial-gradient` 의 모양/크기/위치(`circle closest-side at 25% 50%`)·`conic-gradient(from 90deg at …)` 도 셰이더로, 반지름이 서로 다른 모서리·**그라디언트(세로/가로/방사형/원뿔 `conic-gradient`)도 같은 셰이더 파이프라인**으로(그라디언트 색은 256칸 동적 색 띠 텍스처를 샘플링; 정지점마다 하나 만들어 공유, 64개까지), **커스텀 셰이더 파이프라인**(`better_uiux:core/round`, GLSL 하나가 Vulkan·OpenGL 모두에서 컴파일됨): 둥근 사각형 채움·테두리 고리·번지는 그림자를 부드러운 가장자리(안티앨리어싱)로 한 번에 그림(`UiGfx.fillRoundRect/strokeRoundRect/shadowRoundRect`, 지원하지 않는 백엔드는 사각형 근사로 대체), 자식 둥근 클립(`overflow:hidden` + `border-radius`: 모서리 행 묶음마다 사각형 클립을 걸고 자식을 다시 그림, 묶음은 12개 이하로 합침), `font-style: italic`·`font-family`(마인크래프트 글꼴: `uniform`/`alt`/`illageralt` 또는 `ns:path`; `UiGfx.drawStyledText` 추가), 줄바꿈 글자 상자 폭을 컨테이너 폭으로 제한(그릴 때 같은 폭에서 줄바꿈), `row-reverse`/`column-reverse`, `align-content`, absolute 의 기준 조상·`fixed` 는 화면 기준, 이미지 둥근 모서리, `inset` 그림자, 여러 `animation`·`animation-play-state`, `:has()`,
줄바꿈 글자의 높이가 auto 높이 컨테이너·grid 행·scroll 내용에 반영, `textfield`/`select` 입력 글자 스타일(`text-transform` 은 편집 위치가 어긋나지 않게 textfield 에서 제외),
`radial-gradient`(가운데 기준 타원, 최대 6띠), 속성 값 타입 경고(`PropValues`: 길이/숫자/색/열거형, 명백히 잘못된 값만; 값은 그대로 저장), 에디터 규칙 머리글에 `@media` 조건의 `[적용 중]/[꺼짐]` 표시,
**레이아웃에 영향을 주는 상태 규칙**(`:hover/:active/:focus` 의 width/margin/padding/gap/font-size 등 → 상태가 바뀌면 다시 배치, `transition` 과도 함께 동작).

- 렌더링(계획하지 않음, 쓰임이 드묾): 점선 테두리·변마다 두께가 다른 테두리·이미지 일부 영역(`uv`)의 셰이더화(지금은 사각형 근사), 셰이더가 없는 백엔드에서의 각도/반복/원뿔 근사 품질
- 도구: 값 검증의 확대(지금은 길이·숫자·색·열거형·`margin padding border border-radius box-shadow background font-weight flex grid-template-* transition animation` 를 검사 `content cursor font-family text-decoration`; 그 밖의 속성은 아직)

## 실기 검증 (GameTest / 스모크)

**Fabric** — `./gradlew :fabric:runClientGameTest` (실제 창·월드·마우스, 스크린샷은 `fabric/build/run/clientGameTest/screenshots`):
- `Css3ClientGameTest`: 둥근 모서리·그라디언트·그림자·아웃라인이 실제로 그려짐, `flex-wrap`, grid `1fr 2fr`, `::before`, 진짜 마우스로 `:hover` 켜짐/꺼짐, `width` 전환(레이아웃 애니메이션)에서 옆 요소가 밀림, 경고 없음.
- `Css3AdvancedClientGameTest`: 위젯 글자 스타일(`text-transform`/`letter-spacing`/`font-weight`), `@media (max-width)` 가 GUI 배율을 바꾸면(1920→480 폭) 켜지고 꺼짐, 에디터 패널의 새 구역(크기·배치/글자/효과)과 값 칸 "출처:" 툴팁, 렌더링 비용 측정(`[PERF]` 로그).

- `Css3VisualClientGameTest`: 스크린샷의 **실제 픽셀**로 확인(창 1920x1080 — `fabric/build.gradle.kts` 의 `--width/--height` — 에서 GUI 배율 1~4) — 둥근 모서리 끝 픽셀은 비고 위쪽 변·가운데는 차며, 그라디언트 위는 빨강/아래는 파랑 쪽, 배율이 달라도 같은 모양. View 메뉴의 상태 미리보기(hover/active/focus/없음)를 마우스로 눌러 규칙 색이 바뀌는 것까지.

- `Css3BehaviorClientGameTest`: 실제 시간·입력으로 — `@keyframes` 가 시간에 따라 움직임, 호버 색 `transition` 이 중간 값을 거쳐 끝값에 도달, 진짜 클릭으로 `:focus` 켜짐/꺼짐, `z-index` 겹침 순서, 외부 `@import`, `calc(var(--w) * 2 + 10px)`, 시트 `!important` 가 인라인을 이김.
- `Css3VisualClientGameTest` 의 `newFeatures`: 이미지 둥근 모서리(픽셀), `inset` 그림자(픽셀), `:has()`, `row-reverse` 를 실제 화면(GUI 배율 2)에서 확인.
- `Css3BehaviorClientGameTest`(2): 실제 마우스 호버로 `:hover` 의 width 가 배치를 바꾸고 옆 요소를 밀며(`transition` 포함) 벗어나면 원래대로, 너비가 정해진 컨테이너 안 줄바꿈 글자가 컨테이너 높이를 늘림, `font-size` 가 입력칸 글자에 적용.
- **Vulkan / OpenGL**: `-PgfxBackend=vulkan|opengl` 가 게임의 `--graphicsBackend` 인자로 백엔드를 고정한다(로그의 "Using graphics backend" 로 확인). 같은 GameTest(픽셀 검사 포함)가 두 백엔드에서 모두 통과했고(NVIDIA: Vulkan 1.4 / OpenGL 3.3), 렌더링 비용 수치도 같았다.

**NeoForge** — `./gradlew :neoforge:runSmokeClient`: NeoForge 에는 클라이언트 GameTest 가 없어 `NeoSmoke`(`-Dbetteruiux.smoke=true` 일 때만 동작)가 타이틀 화면에서 CSS3 데모를 열어
실제 렌더링 경로로 그리고, 그리기 명령(합성된 사각형, `::before`, flex-wrap, 경고 없음)과 F2 스크린샷 파일의 실제 픽셀(둥근 모서리, 그라디언트)을 확인한 뒤 `[SMOKE] PASS/FAIL` 을 찍고 종료한다.

**셰이더 파이프라인 시험 결과(성공)**: 마인크래프트 26.x 의 `renderpearl` 은 GLSL 을 양쪽 백엔드로 컴파일한다. `RenderPipeline.builder()` 에 바인드 그룹(PROJECTION/DYNAMIC_TRANSFORMS/SAMPLER0), `POSITION_TEX_COLOR` 정점, `QUADS`, 우리 셰이더(`assets/better_uiux/shaders/core/round.vsh/.fsh`)를 주고 `GuiGraphicsExtractor.blit(pipeline, 흰 1x1 텍스처, …)` 로 그린다(UV 0..1 로 사각형 크기를 미분 비율에서 얻음). 반지름/두께/번짐은 셰이더 정의(define)라 조합마다 파이프라인이 하나(최대 32개, 처음 쓸 때 생성; 실패하면 사각형 근사로 대체).
같은 GameTest(픽셀 검사 포함)가 Vulkan·OpenGL 모두에서 통과했고, NeoForge 스모크도 통과했다. 가장자리에 부분 투명 픽셀이 생겨 부드럽게 보인다(모서리 48x48 픽셀 영역에서 28개).
효과: 240칸 격자(모든 장식)가 **4fps → 103~104fps(그라디언트까지 셰이더로 옮긴 뒤 119~120fps)**(OpenGL/Vulkan), 둥근 테두리 격자 25 → 119fps, 그림자 격자 9 → 120fps.

**처음 쓰는 파이프라인의 끊김**: 반지름·번짐·그라디언트 조합마다 파이프라인이 하나라 처음 그릴 때 컴파일이 일어난다. 처음 보는 조합 5개(반지름·그림자·그라디언트·테두리)를 차례로 열어 틱 시간을 쟀는데 가장 긴 틱이 평소 틱(50ms, 20tps)과 같아 눈에 띄는 끊김은 없었다(Vulkan·OpenGL 모두, 이 PC 기준).
편집기는 규칙에 `속성: 값` 을 추가/수정하면 적용한 뒤 상태 줄에 "주의: 알 수 없는 속성 'colour' (혹시 'color'?)" / "'opacity: red' - 숫자가 아닙니다" 를 보여 준다.

**성능 측정에서 나온 것(셰이더 이전)** (240칸 격자, 칸마다 장식): 명령 수가 곧 비용이다(명령 1,200개까지는 프레임 영향 없음, 8,400개에서 9fps).
그래서 둥근 그라디언트는 가운데 구간을 `fillGradient` 한 번으로 그리고(키가 커도 명령 수 불변), 번진 그림자는 층 수를 줄이고 층의 모서리를 거칠게 해서 상자당 명령을 56 → 약 20 으로 줄였다(`DrawCostTest` 가 지킨다).
남은 큰 비용은 둥근 테두리(상자당 ~17): 모서리 행마다 사각형이 필요해서다. 한 화면에 장식 상자를 수백 개 쓰지 않는 것이 좋다.

검증하지 못한 것(환경 한계): **다른 GPU/드라이버(AMD·Intel 등)** — 개발 PC 가 NVIDIA 한 대뿐이라 테스트할 수 없었다. 렌더링은 `fillRect`/`fillGradient` 같은 기본 프리미티브만 써서 드라이버 의존성이 낮을 것으로 예상하지만 확인된 것은 아니다.


---

# 2차 계획 — 남은 CSS3 중 쓰임새 있는 것 (2026-11-13 작성)

1차(Phase 0~7)로 "게임 UI에 의미 있는 CSS3"의 약 75~80% 를 구현했다(속성 144개, 선택자/값/flex/grid 일부/그라디언트/셰이더 렌더링/애니메이션/중첩).
이 장은 **남은 20~25% 중 실제로 쓰임새가 있는 것**만 골라 순서와 방법을 정해 둔 계획이다. 목표는 "CSS3 100%"가 아니라 마인크래프트 UI 제작자가 CSS 지식으로 기대하는 것을 먼저 채우는 것이다.

## 원칙 (1차와 같다)

- 한 기능의 완료 조건: ① core 단위 테스트 ② GameTest 픽셀/동작 검사(Vulkan·OpenGL 둘 다) + NeoForge 스모크 ③ `PropValues` 값 검증과 에디터 자동 완성 후보 ④ 필요하면 에디터 속성 패널 행 ⑤ `uicss-reference.md` 갱신.
- 새 그리기 능력은 `UiGfx` 의 `default` 메서드로 더하고(없는 백엔드는 근사로 대체), 셰이더는 `round.fsh` 의 정의(define)로 확장한다.
- 값 검증은 "명백히 잘못된 것만 경고"(값은 그대로 저장) 원칙을 유지한다.
- 규모 표기: **S** 반나절 안팎, **M** 하루~이틀, **L** 사흘 이상(조사 포함).

## 하지 않을 것 (결정)

문서용 서식 맥락과 이 프로젝트의 UI 모델이 맞지 않거나 비용 대비 쓰임이 없는 것: `float`, `display: block/inline/inline-block` 흐름, 표 레이아웃(`display: table`), 다단(`columns`), `writing-mode`/방향성, 루비, 3D 변환(`perspective`, `rotateX/Y`), 인쇄(`@page`), `@font-face`(글꼴 파일 로딩은 마인크래프트 글꼴 제공자 체계와 충돌; 대신 리소스팩 글꼴을 `font-family: 네임스페이스:경로` 로 쓴다), `scroll-snap`, `will-change`/`contain`.

## Phase 8A — 작은 것 모음 (S, 먼저)

쓰임새는 크고 비용은 작은 것들.

| 항목 | 내용 | 비고 |
|---|---|---|
| `pointer-events: none \| auto` | 히트 테스트에서 제외(장식용 겹침 요소가 클릭을 막지 않게) | `UiDocument.hitTest`, `cursor`/호버 계산 |
| `visibility: hidden` | 자리는 유지하고 그리지 않음, 입력 제외 | 지금은 `visible="false"`(자리 없음)뿐 |
| 롱핸드 | `transition-property/duration/timing-function/delay`, `animation-name/duration/timing-function/delay/iteration-count/direction/fill-mode` | 스크립트·에디터에서 한 값만 바꾸기 쉬워짐; 숏핸드와 합성 |
| `font` 숏핸드 | `font: bold italic 12px/1.5 uniform` → 롱핸드 | `Shorthands` |
| 글자 | `text-indent`, `word-break`/`overflow-wrap`, `text-decoration-color`/`-style`, `text-align: justify` 처리 | `TextStyle` |
| 단위 | `ch`, `ex`, `cm/mm/in/pc`, `svw/svh/lvw/lvh/dvw/dvh`, `env()` 는 제외 | `CssMath` |
| 색 | `color-mix()`, `hwb()`, `lab()/lch()/oklab()/oklch()`, `light-dark()` | `Colors`; 변환 후 sRGB |
| `accent-color` | 체크박스/슬라이더/진행바 기본색 | 위젯 색 키(`check-color` 등)에 매핑 |
| `@media` 확장 | `(hover: hover\|none)`, `(pointer: fine\|coarse)`, `(prefers-reduced-motion)`(게임의 "움직임 줄이기" 옵션 연동 가능하면), 게임 전용 `(min-gui-scale: N)` | `MediaQuery` |
| 선택자 | `:focus-visible`(키보드로 포커스), `:placeholder-shown`, `:read-only`/`:read-write`, `:required`/`:optional`, `:nth-child(n of S)`, `::first-line`/`::first-letter`(선택) | `Selector` |
| `scrollbar-color`, `scrollbar-width` | scroll 위젯 막대 색/굵기 | `BuiltinWidgets.Scroll` |

완료 조건: 항목별 core 테스트 + `PropValues`/자동 완성 + 문서. GameTest 는 `pointer-events`/`visibility`/`:focus-visible`(실제 입력 필요)만 추가.

## Phase 8B — transform 완성 (M)

현재 `translate-x/y`, `scale`, `rotate` 만 있고 `pushTransform(tx,ty,scale,rotate,origin)` 로 균일 확대만 된다.

- `UiGfx` 에 일반 아핀 변환 `pushMatrix(a,b,c,d,e,f)` 를 더한다(`default` 는 `pushTransform` 근사). 마인크래프트 쪽은 `GuiGraphicsExtractor.pose()` 의 `Matrix3x2fStack` 이 임의 2D 아핀을 받으므로 `McGfx` 에서 곱하면 된다.
- 속성: `transform: translate() scale() scaleX/Y() rotate() skew() skewX/Y() matrix()` (함수 목록을 왼쪽부터 합성), `transform-origin`, 개별 `translate`/`scale`/`rotate` 속성(최신 CSS)도 허용.
- 애니메이션/전환: 지금의 `translate-x` 등과 같이 보간(행렬 분해 보간은 하지 않고 함수별 보간; 함수 목록이 같을 때만).
- **히트 테스트 보정**: 변환된 상자의 마우스 입력은 역변환해서 판정한다(지금은 문서에도 "입력에는 반영 안 됨"). 이것이 이 단계에서 가장 중요한 정확성 작업이다(`UiDocument.hitTest`, `:hover`).
- 검증: core(행렬 합성, 역변환 히트 테스트) + GameTest 픽셀(기울인 상자의 모서리 위치) + 마우스 호버.

## Phase 8C — 배경과 이미지 (M~L)

- `background-image: url(...)`(문서 폴더·`file:` 이미지·텍스처 id; `UiSources` 경로 검증과 서버 번들 한도를 그대로 따른다. 원격 URL 은 허용하지 않는다), `background-size`(`cover/contain/auto/px/%`), `background-position`, `background-repeat`(`repeat/repeat-x/repeat-y/no-repeat/space/round` 중 앞 넷), `background-origin/clip`(border-box/padding-box), **여러 배경**(쉼표 목록; 그라디언트와 이미지 혼합), `background` 숏핸드 전체 파싱.
- `object-fit`(`fill/contain/cover/none/scale-down`), `object-position` — `<image>`/`<video>` 용. 둘 다 "텍스처의 어느 영역을 어디에 그릴지"를 core 에서 계산한다.
- 둥근 모서리와 함께 쓰려면 셰이더가 **모양 좌표와 텍스처 좌표를 분리**해야 한다. 지금은 UV 0..1 로 두 가지를 겸한다.
  - **조사(먼저, 시간 제한)**: 정점 형식에 UV 를 하나 더 실을 수 있는지(예: `DefaultVertexFormat` 의 UV1/UV2 를 가진 형식) 또는 기하 쿼드를 텍스처 영역보다 크게 그리고 바깥을 잘라내는 방식으로 풀 수 있는지. 안 되면 타일은 사각형 클립 + 여러 번 그리기, 모서리는 기존 행 묶음 근사로 대체.
- `border-image`(9-slice: `border-image: url(x) 4 fill`, `border-image-slice/width/repeat`): 마인크래프트식 패널·버튼 테두리에 가장 쓸모가 크다. 모서리 4 + 변 4 + 가운데 = `drawTexture` 최대 9번, 변은 stretch/repeat.
- 검증: 위젯 기본 모양(체크박스/슬라이더/탭)도 이미지 배경으로 바꿀 수 있는지 시험 화면 하나. GameTest 픽셀(타일 반복 경계, cover 의 잘림).

## Phase 8D — grid 보강 (M)

현재: 트랙(`fr/repeat/minmax/auto/%`), span, 명시 배치, `justify-self`, gap.

- `grid-template-areas` + `grid-area`(이름 → 줄 번호), `grid-auto-flow: row | column | dense`, `grid-auto-rows/columns`, `grid-row-start/end`·`grid-column-start/end` 롱핸드, 줄 이름(`[name]`), `place-items/place-content/place-self`, `justify-items/align-items`(칸 안 정렬의 기본값; CSS 기본은 stretch 이지만 지금은 start 로 둔 것을 정책으로 정할 것: `grid` 태그는 현행 유지, `display: grid` 인 일반 요소만 stretch).
- `repeat(auto-fill | auto-fit, minmax(…))`: 컨테이너 폭에서 칸 수를 정한다(반응형 격자). 에디터에서 요청이 많을 것으로 예상.
- 검증: core(`LayoutGridTest` 확장) + GameTest 위치 검사.

## Phase 8E — filter / backdrop-filter (L, 조사부터)

- 먼저 **시험(spike, 시간 제한)**: 현재 렌더 대상(프레임버퍼)의 일부 영역을 읽어 셰이더로 흐리게 한 뒤 그 영역에 다시 그리는 경로. `UiGfx.blurBackground()` 가 전체 화면 블러를 이미 쓰고 있어 마인크래프트 쪽에 블러 패스가 있다. 영역 지정(가위 `pushClip`)과 둥근 모양(`border-radius`)을 함께 쓸 수 있는지가 관건.
- 성공하면: `backdrop-filter: blur(px) brightness() saturate()`(유리 패널) → 그다음 `filter`(이미지·상자 자신): `brightness/contrast/grayscale/sepia/invert/saturate/hue-rotate/opacity/drop-shadow`. 이미지에 한정한 색 필터는 기존 이미지 파이프라인의 정의(define)와 정점 색 곱으로 먼저 구현(프레임버퍼 읽기 불필요 → S~M).
- 실패하면: `blur` 는 "지원 안 함"으로 확정하고 이미지 색 필터만 남긴다.
- 검증: Vulkan·OpenGL 모두 픽셀(블러된 경계의 중간값), 성능(프레임 시간).

## Phase 8F — 남은 후보 (결정 지점)

8A~8E 를 끝낸 뒤 사용 사례를 보고 선택한다.

| 항목 | 내용 | 규모 |
|---|---|---|
| `position: sticky` | scroll 컨테이너 안에서 `top/bottom` 에 붙기 | M |
| `clip-path` | `inset()/circle()/ellipse()` 는 셰이더 SDF 로, `polygon()` 은 사각형 행 묶음 근사 | M~L |
| `mix-blend-mode` / `background-blend-mode` | 파이프라인 블렌드 함수(`ADDITIVE`/`MULTIPLY` 등) 선택 | M |
| 점선·변마다 다른 두께 + 둥근 테두리 | 셰이더 정의 확장 | S~M |
| `@container` | 가장 가까운 컨테이너 크기 질의(레이아웃 두 번 돌리기) | L |
| `@layer`, `@property`, `@scope` | 캐스케이드 레이어, 타입이 있는 변수, 범위 | M |
| 카운터 | `counter-reset/increment`, `content: counter()` | M |
| `:target`, `:lang`, `:any-link` | 쓰임 적음 | S |

## 진행 체크리스트

- [ ] 8A 작은 것 모음 (`pointer-events`, `visibility`, 롱핸드, `font` 숏핸드, 글자, 단위, 색 함수, `accent-color`, `@media` 확장, 선택자, 스크롤바)
- [ ] 8B transform 완성 (`pushMatrix`, `transform`/`transform-origin`/`skew`, 히트 테스트 역변환)
- [ ] 8C 배경·이미지 (UV 분리 조사 → `background-*`, 여러 배경, `object-fit`, `border-image`)
- [ ] 8D grid 보강 (`areas`, `auto-flow`, `auto-fill/fit`, 칸 정렬)
- [ ] 8E filter / backdrop-filter (프레임버퍼 읽기 시험 → 이미지 색 필터 → blur)
- [ ] 8F 남은 후보 선택·구현

## 위험과 선행 조건

- **셰이더 확장은 GPU 의존성을 키운다**: 개발 PC 가 NVIDIA 하나뿐이라 다른 GPU 검증이 안 된다. 각 확장은 파이프라인 생성 실패 시 기존 근사로 대체되게 하고, 새 정의(define)마다 GameTest 로 양쪽 백엔드를 확인한다.
- **히트 테스트와 변환**(8B)은 8C(이미지 배경 위 호버)·8E(필터)보다 먼저 끝내 둔다.
- **파이프라인 수 상한**(현재 64): 배경 크기/위치가 정의로 들어가면 조합이 늘어난다. UV 변환은 정의가 아니라 정점/쿼드 쪽으로 풀고, 풀 수 없으면 상한과 대체 경로를 정한다.
- **에디터 패널**: 새 속성마다 패널 행을 모두 만들면 패널이 길어진다. 자주 쓰는 것(`background-image/size/position`, `transform`, `grid-template-areas`)만 행으로 두고 나머지는 규칙 편집기 + 자동 완성으로 둔다.
