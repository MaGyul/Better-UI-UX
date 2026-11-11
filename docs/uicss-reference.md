# .uicss 스타일 레퍼런스

CSS3 의 게임 UI 에 의미 있는 부분집합. 구현 계획과 진행 상황은 [css3-plan.md](css3-plan.md).
좌표 단위는 GUI 픽셀이며 `1em = 1rem = 8px`(마인크래프트 글꼴 높이).

## 문법

| 기능 | 지원 |
|---|---|
| 규칙 | `선택자, 선택자 { 속성: 값; }`, 주석 `/* */`, `!important` |
| 변수 | `--name: 값`, `var(--name, 대체값)` (상속됨) |
| at-rule | `@keyframes`, `@media`, `@supports`, `@import` (`@charset`/`@namespace` 는 무시) |
| 값 | 단위 `px em rem pt pc in cm mm q vw vh vmin vmax %`, `calc() min() max() clamp()`(퍼센트가 없는 식), `inherit initial unset`, `currentColor` |
| 색 | `#RGB #RGBA #RRGGBB #RRGGBBAA`(알파가 **뒤**), `rgb() rgba() hsl() hsla() hwb()`, CSS 색 이름 148개, `transparent`, 마인크래프트 팔레트 `mc-red mc-gold …` |

`@media`: `width height aspect-ratio orientation`(+`min-`/`max-`, 범위 문법 `(width >= 300px)`), `and`/`not`/쉼표. 뷰포트는 GUI 픽셀 크기.
`@supports`: `(속성: 값)`, `selector(...)`, `not`/`and`/`or`.
`@import "파일.uicss" [미디어쿼리];` — 가져온 시트가 먼저(낮은 우선순위) 적용된다.

## 선택자

`type`, `*`, `#id`, `.class`, `[attr]` `[a=v]` `[a~=v]` `[a|=v]` `[a^=v]` `[a$=v]` `[a*=v]`(끝에 ` i` 로 대소문자 무시),
결합자 ` ` `>` `+` `~`,
`:hover` `:active`(대상 요소 최상위에서만), `:first-child :last-child :only-child :first-of-type :last-of-type :only-of-type :root :empty :disabled :enabled :checked`,
`:nth-child() :nth-last-child() :nth-of-type() :nth-last-of-type()`(`odd even an+b`), `:not() :is() :where()`.
동적 상태: `:hover :active :focus :focus-within` — 선택자의 **어느 단계에서든**(`.card:hover .title`, `#a:hover + #b`, `:not(:hover)`) 쓸 수 있고 그릴 때 판정한다. 그리기에만 영향을 주며 레이아웃은 바꾸지 않는다.
가상 요소: `::before` / `::after`(옛 `:before` 도 허용) + `content: "문자열" attr(이름)`(`\201C` 이스케이프, `none`/`normal` 은 만들지 않음). label/button 에서는 글자 앞뒤에 붙고, 컨테이너에서는 첫/마지막 자식 가상 label 이 생긴다(클릭·선택·편집 대상 아님, 이미지·위젯 같은 리프에는 없음). 가상 요소 규칙은 주인 요소에 적용되지 않는다.
미지원: `:has()`, 그 밖의 가상 요소(`::first-line` 등).

우선순위: 상속 < 태그 기본값 < XML 속성 < 시트 규칙(특이도·순서) < inline `style=""`. 시트의 `!important` 는 inline 일반 값을 이기고, inline `!important` 가 최종 승자.

## 박스 모델·레이아웃

- 크기: `width height min-width max-width min-height max-height`(px, %, `auto`, `fill`), `box-sizing: border-box(기본)|content-box`, `aspect-ratio`.
- 여백: `margin`/`padding`(1~4값, `-top/-right/-bottom/-left` 롱핸드), `margin: auto`(row/column 안에서 주축·교차축).
- 테두리는 레이아웃 공간을 차지한다(`padding` 에 더해짐).
- **flex**(`row`, `column`, 또는 `display: flex` + `flex-direction`): `gap row-gap column-gap`, `justify`(=`justify-content`) `start|center|end|space-between|space-around|space-evenly`, `align`(=`align-items`)/`align-self` `start|center|end|stretch`, `flex-wrap: wrap|wrap-reverse`, `grow`(=`flex-grow`), `flex-shrink`(**기본 0**, 명시한 항목만 줄어듦), `flex-basis`, `flex` 숏핸드, `order`.
- **grid**: `grid-template-columns/rows`(`px % fr auto repeat() minmax()`), 기존 `columns="N"`, `gap`, 항목의 `grid-column`/`grid-row`(`span N`, `시작 / span N`, `시작 / 끝`), `justify-self`, `align-self`.
- 위치: `position: relative`(원래 자리 기준 이동), `absolute|fixed`(바로 위 부모 상자 기준 `top right bottom left`), `z-index`(그리기·클릭 순서).
- 기존 방식도 유지: stack/panel 의 `anchor` + `x`/`y`, `fill` 크기.

## 그리기

- `background`(색 또는 그라디언트), `background-image: linear-gradient(...)`.
  그라디언트는 방향 `to top|bottom|left|right` / `Ndeg` 을 **가장 가까운 축**(세로·가로)으로 근사하고, 다중 정지점과 `%` 위치를 지원한다. `radial-gradient([circle|ellipse] [closest-side|farthest-side|closest-corner|farthest-corner] [at 위치], …)`, `conic-gradient([from 각도] [at 위치], …)`, `repeating-linear/radial-gradient`, 임의 각도 `linear-gradient(30deg | to top right, …)` 는 셰이더가 있을 때 정확히 그려지고, 없는 백엔드에서는 가장 가까운 축/가운데 타원 띠/단색으로 근사된다(반복은 1주기만).
- `border`(두께 또는 `1px solid red`), `border-width`(1~4값), `border-color`(1~4값), `border-top|right|bottom|left`, `border-style: solid|dashed|dotted|none`
  (dashed/dotted 는 둥글지 않은 테두리에서만, 둥글면 solid).
- `border-radius`(1~4값, px/%, 타원 `/` 는 앞 값만), `border-top-left-radius` 등 모서리별.
  행 단위 사각형으로 근사하며 안티앨리어싱은 없다. `overflow: hidden` 의 클립은 사각형이다.
- `box-shadow: ox oy [blur [spread]] color [, ...]` — 바깥 그림자만(`inset` 은 무시). 번짐은 겹친 반투명 층(최대 10)으로 근사. 그림자는 상자 뒤에 그려지므로 반투명 배경에서는 비쳐 보인다.
- `outline` / `outline-width|color|style|offset` — 레이아웃에 영향 없음.
- **글자**(label/button): `font-size`(px/em/rem/%/키워드, 부모 기준, 균등 배율), `line-height`(무단위 배수 또는 길이), `letter-spacing`,
  `font-weight: bold|600+`(1px 겹쳐 그리기), `white-space: pre(기본)|nowrap|normal|pre-wrap|pre-line`(normal/pre-wrap/pre-line 은 박스 폭에서 줄바꿈),
  `text-transform`, `text-align: left|center|right|start|end`, `vertical-align: top|middle|bottom`, `text-overflow: ellipsis`,
  `text-decoration: underline|line-through|overline`, `text-shadow: none`. `font-style: italic`/`font-family` 는 게임 글꼴이 하나라 미지원.
  줄바꿈 높이는 stack·flow(가로/세로)에서 반영되며, grid 칸·컨테이너 auto 높이 계산에는 한 줄 기준으로 들어간다.
- `opacity`(서브트리 전체), `translate-x translate-y scale rotate`(중심 기준), `color shadow text-align cursor`.

## 애니메이션

`transition`, `animation` + `@keyframes`, Java API(`element.animate().to(...)`).

- **그리기 속성**(레이아웃 불변): `opacity translate-x translate-y scale rotate background color border-color tint border border-radius box-shadow outline-color outline-width outline-offset`
- **레이아웃 속성**: `width height min/max-width/height margin padding gap row-gap column-gap x y font-size letter-spacing line-height`.
  움직이는 동안 매 프레임 다시 배치하므로 옆 요소도 함께 밀린다(움직임이 끝나면 멈춘다). `margin: 4 8` 같은 여러 값과 `%`, 색이 섞인 `box-shadow` 도 항목별로 보간한다.
  `auto`/`fill` ↔ 숫자처럼 보간할 수 없는 쌍은 중간에서 값이 바뀐다.
- 전환은 "처음 본 스타일"에서는 시작하지 않는다 → `transition` 선언은 기본 규칙(`button { transition: ... }`)에 둔다.

## 경고와 진단

- 시트의 **알 수 없는 속성**은 경고로 남는다(`스타일: 알 수 없는 속성 'colour' (혹시 'color'?)`). 값은 그대로 저장되므로 동작은 막지 않는다.
  `--변수` 와 `-벤더` 접두 속성은 경고하지 않는다.
- 위젯(`checkbox radio slider progress tabs`)의 글자는 `font-size font-weight letter-spacing text-transform color` 를 따른다.
  그리기에만 반영되고 위젯의 기본 크기는 그대로다. `textfield`/`select` 의 입력 글자는 캐럿 계산 때문에 적용하지 않는다.
- 애니메이션 이징: `linear ease ease-in ease-out ease-in-out cubic-bezier() steps(n[, jump-*]) step-start step-end spring`.
- 크기 속성은 `calc(100% - 20px)` 처럼 퍼센트가 섞인 식도 받는다(`width height min/max-* flex-basis top/right/bottom/left`; 일차식만, `%` 끼리 곱셈 불가).

## 에디터 연동

속성 패널의 값 칸에 마우스를 올리면 "출처: …" 툴팁이 뜬다 — 인라인인지, 어느 시트 규칙인지(숏핸드 포함), XML 속성인지, 태그 기본값인지, 상속인지, 상태 규칙이 덮어쓰는지.
View 메뉴의 상태 미리보기로 `:hover :active :focus` 를 선택한 요소에 강제로 보여 줄 수 있다. 자세한 내용은 [editor.md](editor.md).

## 최근 추가

- 레이아웃: `flex-direction: row-reverse | column-reverse`, `align-content`(wrap 한 줄들의 교차축 분배), `position: absolute` 는 가장 가까운 `position` 지정 조상 기준(없으면 부모), `fixed` 는 화면 기준.
- 이미지 `border-radius` 와 `border`: 이미지는 테두리 안쪽에만 그려지고 모서리가 둥글게 잘린다.
- `box-shadow: inset ...` (안쪽 그림자; 오프셋은 변마다 두께로 반영).
- `animation: a 1s, b 2s` (여러 개; 뒤의 것이 같은 속성을 덮어씀), `animation-play-state: paused | running`.
- `:has(.x)`, `:has(> .x)`, `:has(+ .x)`, `:has(~ .x)`, `:has(a, b)`.

- **상태 규칙의 레이아웃 속성**: `:hover/:active/:focus` 규칙에 `width`, `margin`, `padding`, `gap`, `font-size`, `display` 등을 써도 된다(상태가 바뀌면 다시 배치). 그리기 속성만 바뀌면 다시 배치하지 않는다.
- 줄바꿈 글자: 너비가 정해진 컨테이너·grid 칸·scroll 안에서 줄 수만큼 높이가 늘어난다.
- `textfield`/`select` 의 글자가 `font-size/font-weight/letter-spacing/color`(select 는 `text-transform` 도)를 따른다.

- **값 검사**: 알려진 속성의 값이 명백히 잘못되면(`width: abc`, `opacity: red`, `display: flexx` …) 경고가 나온다(값은 그대로 저장). `var()`/`calc()` 등은 검사하지 않는다.
- 에디터: `@media` 안의 규칙은 패널 머리글에 그 조건이 지금 화면 크기에서 `[적용 중]` 인지 `[꺼짐]` 인지 표시한다.

- `radial-gradient([모양,] 색, ...)`: 가운데 기준 타원(farthest-corner)을 최대 6개 띠로 근사한다. 모양/크기/위치 인자는 읽고 무시한다. 띠마다 행 단위로 그려 명령 수가 많아 큰 상자에 여럿 쓰는 것은 피한다.

- **둥근 클립**: `overflow: hidden` 과 `border-radius` 를 같이 쓰면 자식이 둥근 모양으로 잘린다. 모서리 행 묶음(최대 12개)마다 자식을 다시 그리므로 자식이 많고 무거우면 비용이 든다.
- **글꼴**: `font-style: italic`, `font-family: uniform | alt | illageralt | 네임스페이스:경로`(목록의 첫 알려진 값; serif 같은 이름은 건너뜀). 글꼴별 폭으로 배치된다. 둘 다 상속된다.

- **부드러운 가장자리**: 둥근 모서리의 채움·테두리(실선, 네 변 두께 같음)·번지는 `box-shadow`·그라디언트(`linear/radial/conic`)는 셰이더로 안티앨리어싱되어 한 번에 그려진다(반지름이 서로 달라도 된다). `inset` 그림자와 둥근 이미지/영상(텍스처 전체)도 셰이더로 그려진다. 점선 테두리, 네 변 두께가 다른 테두리, 이미지의 일부 영역(`uv`)은 사각형 근사로 그려진다.

## 아직 없는 것

`filter`/`backdrop-filter`, 3D 변환,
모든 속성에 대한 값 타입 검증(길이/숫자/색/열거형 속성만 검사한다).
