package dev.magyul.betteruiux.core.edit;

import dev.magyul.betteruiux.core.edit.XmlSource.Element;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import dev.magyul.betteruiux.core.layout.LayoutEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 속성 패널의 모델: 어떤 요소에 어떤 행(이름, 값, 종류, 선택지)이 보이는지. 그리기/입력과 무관한 순수 변환이다. */
final class PropertyModel {
    private PropertyModel() {}

    /** 속성 이름 → 화면에 보이는 한글 이름. */
    static final Map<String, String> PROP_LABELS = Map.ofEntries(
            Map.entry("tag", "종류"), Map.entry("text", "글자"), Map.entry("onClick", "클릭 이벤트"), Map.entry("src", "이미지"),
            Map.entry("width", "너비"), Map.entry("height", "높이"), Map.entry("margin", "바깥여백"),
            Map.entry("padding", "안쪽여백"), Map.entry("anchor", "기준점"), Map.entry("gap", "간격"),
            Map.entry("justify", "정렬"), Map.entry("align", "교차정렬"), Map.entry("background", "배경"),
            Map.entry("color", "글자색"), Map.entry("border", "테두리"), Map.entry("border-color", "테두리색"),
            Map.entry("opacity", "투명도"), Map.entry("use", "배경효과"), Map.entry("visible", "보임"),
            Map.entry("enabled", "사용"), Map.entry("checked", "체크됨"), Map.entry("align-self", "자기정렬"),
            Map.entry("onChange", "변경 이벤트"), Map.entry("onSubmit", "Enter 이벤트"), Map.entry("value", "값"), Map.entry("min", "최소"),
            Map.entry("max", "최대"), Map.entry("step", "단계"), Map.entry("maxlength", "최대길이"), Map.entry("placeholder", "안내글"),
            Map.entry("options", "선택지"), Map.entry("group", "그룹"), Map.entry("columns", "열 수"), Map.entry("title", "제목"),
            Map.entry("item", "아이템 id"), Map.entry("count", "개수"), Map.entry("size", "크기"),
            Map.entry("min-width", "최소 너비"), Map.entry("max-width", "최대 너비"), Map.entry("min-height", "최소 높이"),
            Map.entry("max-height", "최대 높이"), Map.entry("box-sizing", "크기 기준"), Map.entry("position", "위치 방식"),
            Map.entry("z-index", "겹침 순서"), Map.entry("grow", "늘어남"), Map.entry("flex-shrink", "줄어듦"), Map.entry("order", "순서"),
            Map.entry("flex-wrap", "줄바꿈"), Map.entry("grid-template-columns", "열 정의"), Map.entry("grid-template-rows", "행 정의"),
            Map.entry("grid-column", "열 위치"), Map.entry("grid-row", "행 위치"), Map.entry("font-size", "글자 크기"),
            Map.entry("font-weight", "굵기"), Map.entry("line-height", "줄 간격"), Map.entry("letter-spacing", "자간"),
            Map.entry("text-align", "가로 정렬"), Map.entry("vertical-align", "세로 정렬"), Map.entry("white-space", "공백 처리"),
            Map.entry("text-transform", "대소문자"), Map.entry("text-overflow", "넘침 표시"), Map.entry("text-decoration", "글자 장식"),
            Map.entry("border-style", "테두리 모양"), Map.entry("border-radius", "둥근 모서리"), Map.entry("box-shadow", "그림자"),
            Map.entry("outline-width", "윤곽선 두께"), Map.entry("outline-color", "윤곽선 색"), Map.entry("outline-offset", "윤곽선 간격"),
            Map.entry("bar-color", "채움색"), Map.entry("fill-color", "채움색"), Map.entry("check-color", "체크색"), Map.entry("tooltip", "툴팁"), Map.entry("modal", "모달"), Map.entry("light", "조명"));
    static final Set<String> COLOR_KEYS = java.util.Set.of("background", "color", "border-color", "bar-color", "fill-color", "check-color", "outline-color");

    static final Set<String> ATTR_KEYS = Set.of("id", "class", "onClick", "src", "text", "onChange", "onSubmit", "value", "checked",
            "min", "max", "step", "maxlength", "placeholder", "options", "group", "columns", "title", "item", "count", "size", "tooltip", "enabled", "modal", "light");

    enum Kind { TEXT, ENUM, BOOL, NUMBER, COLOR, HEADER }

    record PropRow(String label, String key, String value, boolean editable, Kind kind, List<String> options) {
        PropRow(String label, String key, String value, boolean editable) {
            this(label, key, value, editable, Kind.TEXT, List.of());
        }
    }

    static final Map<String, List<String>> ENUMS = Map.ofEntries(
            Map.entry("anchor", List.of("top-left", "top", "top-right", "left", "center", "right", "bottom-left", "bottom", "bottom-right")),
            Map.entry("justify", List.of("start", "center", "end", "space-between", "space-around", "space-evenly")),
            Map.entry("align", List.of("start", "center", "end", "stretch")),
            Map.entry("align-self", List.of("start", "center", "end", "stretch")),
            Map.entry("text-align", List.of("left", "center", "right")),
            Map.entry("overflow", List.of("visible", "hidden")),
            Map.entry("box-sizing", List.of("border-box", "content-box")),
            Map.entry("position", List.of("static", "relative", "absolute")),
            Map.entry("flex-wrap", List.of("nowrap", "wrap", "wrap-reverse")),
            Map.entry("font-weight", List.of("normal", "bold")),
            Map.entry("vertical-align", List.of("top", "middle", "bottom")),
            Map.entry("white-space", List.of("pre", "nowrap", "normal", "pre-wrap", "pre-line")),
            Map.entry("text-transform", List.of("none", "uppercase", "lowercase", "capitalize")),
            Map.entry("text-overflow", List.of("clip", "ellipsis")),
            Map.entry("text-decoration", List.of("none", "underline", "line-through", "overline")),
            Map.entry("border-style", List.of("solid", "dashed", "dotted", "none")));
    /** 값이 참/거짓인 속성과 그 기본값. */
    static final Map<String, Boolean> BOOLS = Map.of("checked", false, "enabled", true, "visible", true, "shadow", true, "modal", true);
    static final Set<String> NUMBERS = Set.of("width", "height", "gap", "border", "x", "y", "opacity", "min", "max", "step", "maxlength", "columns", "count", "size",
            "min-width", "max-width", "min-height", "max-height", "z-index", "grow", "flex-shrink", "order", "font-size",
            "letter-spacing", "outline-width", "outline-offset");

    static Kind kindOf(String key) {
        if (ENUMS.containsKey(key)) return Kind.ENUM;
        if (BOOLS.containsKey(key)) return Kind.BOOL;
        if (COLOR_KEYS.contains(key)) return Kind.COLOR;
        if (NUMBERS.contains(key)) return Kind.NUMBER;
        return Kind.TEXT;
    }

    /** 고급 속성 섹션 이름(헤더 키는 "§" + 이름). 처음에는 접혀 있다. */
    static final String SEC_BOX = "크기·배치", SEC_TEXT = "글자", SEC_FX = "효과";
    static final Set<String> DEFAULT_COLLAPSED = Set.of("§" + SEC_BOX, "§" + SEC_TEXT, "§" + SEC_FX);

    static PropRow header(String name) {
        return new PropRow(name, "§" + name, "", false, Kind.HEADER, List.of());
    }


    /** 선택한 요소의 속성 행들. {@code srcEl}/{@code srcSource} 는 원본 XML 의 그 요소(없으면 null). */
    static List<PropRow> rows(LayoutBox b, Element srcEl, XmlSource srcSource) {
        List<PropRow> rows = new ArrayList<>();
        if (srcEl != null && srcEl.tag.equals("use")) {         // 템플릿 사용: <use> 의 속성(매개변수)을 그대로 고친다
            rows.add(new PropRow(label("tag"), "", "use  #" + b.node.sourceIndex(), false));
            for (XmlSource.Attr a : srcEl.attrs()) rows.add(new PropRow(a.name, a.name, srcSource.attrValue(srcEl, a.name), true));
            return rows;
        }
        String tag = b.tag();
        boolean root = b.parent == null;
        rows.add(new PropRow(label("tag"), "", tag + "  #" + b.node.sourceIndex(), false));
        rows.add(attr(b, "id"));
        rows.add(attr(b, "class"));
        if (tag.equals("label") || tag.equals("button")) rows.add(new PropRow(label("text"), "text", LayoutEngine.rawText(b), true));
        if (tag.equals("button")) rows.add(attr(b, "onClick"));
        if (tag.equals("image") || tag.equals("video")) rows.add(attr(b, "src"));
        widgetRows(b, tag, rows);
        rows.add(header("레이아웃"));
        if (!root) {
            rows.add(style(b, "width"));
            rows.add(style(b, "height"));
            rows.add(style(b, "margin"));
            if (!LayoutQueries.isFlow(b.parent)) {
                rows.add(style(b, "anchor"));
                rows.add(style(b, "x"));
                rows.add(style(b, "y"));
            }
        }
        if (!tag.equals("label") && !tag.equals("image") && !tag.equals("spacer")) rows.add(style(b, "padding"));
        if (tag.equals("row") || tag.equals("column")) {
            rows.add(style(b, "gap"));
            rows.add(style(b, "justify"));
            rows.add(style(b, "align"));
        }
        if (!root) rows.add(style(b, "visible"));
        rows.add(header("모양"));
        rows.add(style(b, "background"));
        if (tag.equals("label") || tag.equals("button")) rows.add(style(b, "color"));
        rows.add(style(b, "border"));
        rows.add(style(b, "border-color"));
        rows.add(style(b, "opacity"));
        if (root) rows.add(style(b, "use"));
        else rows.add(attr(b, "tooltip"));

        // 고급 CSS 속성: 기본으로 접혀 있다(EditorController.collapsedSections)
        rows.add(header(SEC_BOX));
        rows.add(style(b, "min-width"));
        rows.add(style(b, "max-width"));
        rows.add(style(b, "min-height"));
        rows.add(style(b, "max-height"));
        rows.add(style(b, "box-sizing"));
        if (!root) {
            rows.add(style(b, "position"));
            rows.add(style(b, "z-index"));
            if (LayoutQueries.isFlow(b.parent) && !b.parent.tag().equals("grid")) {
                rows.add(style(b, "grow"));
                rows.add(style(b, "flex-shrink"));
                rows.add(style(b, "align-self"));
                rows.add(style(b, "order"));
            }
            if (b.parent.tag().equals("grid")) {
                rows.add(style(b, "grid-column"));
                rows.add(style(b, "grid-row"));
            }
        }
        if (tag.equals("row") || tag.equals("column")) rows.add(style(b, "flex-wrap"));
        if (tag.equals("grid")) {
            rows.add(style(b, "grid-template-columns"));
            rows.add(style(b, "grid-template-rows"));
        }
        if (tag.equals("label") || tag.equals("button")) {
            rows.add(header(SEC_TEXT));
            rows.add(style(b, "font-size"));
            rows.add(style(b, "font-weight"));
            rows.add(style(b, "line-height"));
            rows.add(style(b, "letter-spacing"));
            rows.add(style(b, "text-align"));
            rows.add(style(b, "vertical-align"));
            rows.add(style(b, "white-space"));
            rows.add(style(b, "text-transform"));
            rows.add(style(b, "text-overflow"));
            rows.add(style(b, "text-decoration"));
        }
        rows.add(header(SEC_FX));
        rows.add(style(b, "border-style"));
        rows.add(style(b, "border-radius"));
        rows.add(style(b, "box-shadow"));
        rows.add(style(b, "outline-width"));
        rows.add(style(b, "outline-color"));
        rows.add(style(b, "outline-offset"));
        return rows;
    }

    /** 새 입력/레이아웃 태그에 맞는 속성 행. */
    static void widgetRows(LayoutBox b, String tag, List<PropRow> rows) {
        switch (tag) {
            case "checkbox", "radio" -> {
                rows.add(style(b, "check-color"));
                rows.add(attr(b, "text"));
                rows.add(attr(b, "checked"));
                if (tag.equals("radio")) rows.add(attr(b, "group"));
                rows.add(attr(b, "enabled"));
                rows.add(attr(b, "onChange"));
            }
            case "slider" -> {
                rows.add(style(b, "fill-color"));
                rows.add(attr(b, "value"));
                rows.add(numRow(b, "min"));
                rows.add(numRow(b, "max"));
                rows.add(numRow(b, "step"));
                rows.add(attr(b, "text"));
                rows.add(attr(b, "enabled"));
                rows.add(attr(b, "onChange"));
            }
            case "progress" -> {
                rows.add(style(b, "bar-color"));
                rows.add(attr(b, "value"));
                rows.add(numRow(b, "min"));
                rows.add(numRow(b, "max"));
                rows.add(attr(b, "text"));
            }
            case "textfield" -> {
                rows.add(attr(b, "value"));
                rows.add(attr(b, "placeholder"));
                rows.add(numRow(b, "maxlength"));
                rows.add(attr(b, "enabled"));
                rows.add(attr(b, "onChange"));
                rows.add(attr(b, "onSubmit"));
            }
            case "select" -> {
                rows.add(attr(b, "options"));
                rows.add(attr(b, "value"));
                rows.add(attr(b, "enabled"));
                rows.add(attr(b, "onChange"));
            }
            case "item" -> {
                rows.add(attr(b, "item"));
                rows.add(numRow(b, "count"));
                rows.add(numRow(b, "size"));
            }
            case "grid" -> rows.add(numRow(b, "columns"));
            case "tab" -> rows.add(attr(b, "title"));
            case "dialog" -> rows.add(attr(b, "modal"));
            case "mc-viewer" -> rows.add(attr(b, "light"));
            default -> { }
        }
    }

    static PropRow numRow(LayoutBox b, String key) {
        String v = b.node.attr(key);
        return new PropRow(label(key), key, v == null ? "" : v, true, Kind.NUMBER, List.of());
    }

    static String label(String key) { return PROP_LABELS.getOrDefault(key, key); }

    static PropRow attr(LayoutBox b, String key) {
        String v = b.node.attr(key);
        return new PropRow(label(key), key, v == null ? "" : v, true, kindOf(key), ENUMS.getOrDefault(key, List.of()));
    }

    static PropRow style(LayoutBox b, String key) {
        String v = b.style.get(key);
        return new PropRow(label(key), key, v == null ? "" : v, true, kindOf(key), ENUMS.getOrDefault(key, List.of()));
    }

}
