# 내장 태그 (입력 / 레이아웃)

`/uiux open widgets` 로 모든 태그가 들어 있는 데모를 볼 수 있다(`assets/better_uiux/ui/widgets.uiml`).
입력 위젯의 값은 XML 속성에 그대로 저장되므로 Java 에서 `doc.query("#volume").attr("value")` 로 읽고,
`setAttr` 로 바꿀 수 있다. 사용자가 값을 바꾸면 `onChange` 액션(`UiActions` 에 등록한 id)과
`UiEvent.Type.CHANGE` 이벤트가 발생한다.

## 입력

| 태그 | 속성 | 설명 |
|---|---|---|
| `checkbox` | `text` `checked` `enabled` `onChange` | 체크박스. 클릭 / Enter / Space |
| `radio` | `text` `checked` `group` `enabled` `onChange` | 같은 `group` 안에서 하나만 켜진다 |
| `slider` | `min` `max` `step` `value` `text` `enabled` `onChange` | 끌어서 조절. 포커스 시 ←/→(Shift ×10), Home/End, 휠 |
| `textfield` | `value` `placeholder` `maxlength` `enabled` `onChange` `onSubmit` | 한 줄 입력. 채팅창과 같은 선택/복사/붙여넣기, IME 조합 지원. `onSubmit` 은 Enter |
| `select` | `options`(쉼표로 구분) `value` `placeholder` `enabled` `onChange` | 드롭다운. ↑/↓ 로 바로 변경 |
| `progress` | `value` `min` `max` `text` | 진행 막대. `value="{health}"` 처럼 바인딩 가능 |

공통: Tab / Shift+Tab 으로 포커스 이동(버튼도 Tab 으로 고르고 Enter/Space 로 누름), Esc 로 포커스 해제. 색은 스타일로 바꾼다
(`fill-color`(slider), `bar-color`(progress), `check-color`(checkbox/radio), `border-color`, `background-color`, `color`).

## 레이아웃 / 표시

| 태그 | 속성 | 설명 |
|---|---|---|
| `scroll` | (column 처럼 `gap` `justify` `align`) | 세로 스크롤 컨테이너. 휠, 막대 드래그. 내용이 넘쳐도 커지지 않는다 |
| `grid` | `columns` `gap` | 칸 배열. 열 너비/행 높이는 그 칸의 가장 큰 자식에 맞춘다 |
| `tabs` + `tab` | `tab` 의 `title` | 머리글을 눌러 탭 전환. `tab` 은 기본으로 영역을 가득 채운다 |
| `separator` | | 구분선. column 안에서는 가로선, row 안에서는 세로선 |
| `item` | `item`(예: `minecraft:diamond`) `count` `size`/`width`/`height` | 아이템 아이콘. 상자 크기에 맞춰 커지고 가운데에 놓인다(기본 16). 2개 이상이면 개수 표시 |
| `dialog` | `modal`(기본 true) | 화면 중앙의 대화상자. 뒤를 어둡게 하고 바깥 클릭을 막는다 |

## 모든 요소

- `tooltip="설명"` — 마우스를 잠시 올리면 툴팁이 뜬다(`\n` 으로 줄바꿈).
- `enabled="false"`, `visible="false"`.

## Java API

```java
UiDocument doc = ...;
doc.on("#volume", UiEvent.Type.CHANGE, ev -> System.out.println(ev.target().attr("value")));
doc.query("#name").setAttr("value", "Steve");   // 글자 입력 칸 값 바꾸기
doc.focus(doc.query("#name"));                  // 포커스 주기
doc.setClipboardHandler(get, set);              // 백엔드가 연결(게임 클립보드)
doc.setVariable("accent", "#55FF55FF");         // CSS 변수(var(--accent)) 바꾸기
```

## 아직 없는 것

`list`(데이터 바인딩 반복), `icon`, `video`, `entity-preview`, `block-model`, 슬롯 UI(`slot`).
