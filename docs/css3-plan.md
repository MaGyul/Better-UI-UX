# CSS3 확장 계획 (.uicss) — 2026-11-11 코드 재조사 반영판

## Context
`.uicss`는 CSS의 일부만 지원한다. 목표는 "CSS3 100%"가 아니라 **게임 GUI에 의미 있는 CSS3 부분집합을 최대한** 구현하는 것. 기준 브랜치는 `feat/editor-blank-and-lazy-new` (bdfe708 이후 152파일 변경: 위젯/grid/scroll/tabs, 이미지·GIF·mp4, script, server sync, 에디터 분리, 골든 테스트). 재조사 결과 **스타일 코어(Stylesheet/Selector/StyleResolver)는 거의 그대로**이고, 레이아웃/페인터/에디터 쪽이 커졌다.

## 확정된 결정 (2026-11-11, 미배포 모드이므로 하위호환 불필요)
- 색: CSS 표준 `#RRGGBBAA`로 전환 (`#AARRGGBB` 폐기). 이름은 CSS 표준값 사용, 기존 MC 팔레트 값은 `mc-red` 등으로 이동. 데모/번들/테스트/골든의 색 표기를 함께 마이그레이션.
- 우선순위: XML 속성 < 시트 < inline 유지, 단 `!important`는 inline보다 우선(스펙 정합).
- 기존 비표준 프로퍼티(`anchor justify align grow`)는 이번 단계에선 유지하고 Phase 3에서 표준 flex 속성과 병행/정리.
- 레이아웃은 flex + grid + position 중심, block/inline 흐름은 제외.

## 진행 상황 (작업 시 갱신)
- [~] Phase 0 기반 — 완료: `\s` 버그, 특이도 재설계(1000진 패킹), 시트 `!important` > inline > 시트, 괄호 인식 선택자 목록 분리(`Selector.splitList`, StyleText 반영), 값 처리기 `style/CssMath`. 남음: 프로퍼티 레지스트리, 알 수 없는 프로퍼티 경고
- [~] Phase 1 선택자·캐스케이드 — 완료: `+ ~` 결합자, `:not/:is/:where`, `:nth-child/-last-child/-of-type/-last-of-type`, `:only-child/:first-of-type/:last-of-type/:only-of-type/:empty/:checked`, `[attr|=v]`·`i` 플래그, `inherit/initial/unset`. `@media`(width/height/aspect-ratio/orientation, min-/max-, 범위 문법, not/and/쉼표, 중첩), `@supports`(prop/selector()/not/and/or), `@import`(미디어 조건, 순환 방지, 가져온 시트가 앞 순서), 에디터 스캔이 조건 블록 안 규칙도 인식. 남음: `:focus`(상태 오버레이 배선 필요), `::before/::after`, 오버레이를 마지막 compound 외에도 허용
- [~] Phase 2 값·단위·색 — 완료: `#RGB #RGBA #RRGGBB #RRGGBBAA`(CSS 순서로 전면 마이그레이션, 데모·테스트·문서 포함), `rgb/rgba/hsl/hsla/hwb`, CSS 색 이름 148개 + `mc-*` 팔레트, `currentColor`, 단위 `em rem pt pc in cm mm q vw vh vmin vmax`, `calc/min/max/clamp`(퍼센트 없는 식), 숏핸드 `border`, `border-width`, `margin-*`, `padding-*`. 남음: `%` 가 섞인 calc(레이아웃 단계 평가), `font`/`transition`/`background` 숏핸드
- [ ] Phase 3 박스 모델·레이아웃
- [ ] Phase 4 렌더링
- [ ] Phase 5 텍스트
- [ ] Phase 6 애니메이션
- [ ] Phase 7 도구·문서 (에디터 `PropertyModel`/`StyleText` 신규 문법 대응 포함)

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
