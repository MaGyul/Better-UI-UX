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
미지원: `:focus`, `::before/::after`, `:has()`.

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
  그라디언트는 방향 `to top|bottom|left|right` / `Ndeg` 을 **가장 가까운 축**(세로·가로)으로 근사하고, 다중 정지점과 `%` 위치를 지원한다. `radial-gradient`/`conic-gradient` 는 첫 색 단색.
- `border`(두께 또는 `1px solid red`), `border-width`(1~4값), `border-color`(1~4값), `border-top|right|bottom|left`, `border-style: solid|dashed|dotted|none`
  (dashed/dotted 는 둥글지 않은 테두리에서만, 둥글면 solid).
- `border-radius`(1~4값, px/%, 타원 `/` 는 앞 값만), `border-top-left-radius` 등 모서리별.
  행 단위 사각형으로 근사하며 안티앨리어싱은 없다. `overflow: hidden` 의 클립은 사각형이다.
- `box-shadow: ox oy [blur [spread]] color [, ...]` — 바깥 그림자만(`inset` 은 무시). 번짐은 겹친 반투명 층(최대 10)으로 근사. 그림자는 상자 뒤에 그려지므로 반투명 배경에서는 비쳐 보인다.
- `outline` / `outline-width|color|style|offset` — 레이아웃에 영향 없음.
- `opacity`(서브트리 전체), `translate-x translate-y scale rotate`(중심 기준), `color shadow text-align cursor`.

## 애니메이션

`transition`, `animation` + `@keyframes`. 보간 가능: `opacity translate-x translate-y scale rotate background color border-color tint border`.
레이아웃 속성(`width` 등)은 아직 애니메이션되지 않는다.

## 아직 없는 것

`:focus`, `::before/::after`, `font-*`/`line-height`/`letter-spacing`/줄바꿈/`text-overflow`, 퍼센트가 섞인 `calc()`, `inset` 그림자, `radial/conic` 그라디언트, 둥근 클립, `filter`/`backdrop-filter`, 3D 변환.
