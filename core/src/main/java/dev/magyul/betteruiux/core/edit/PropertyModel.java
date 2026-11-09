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
            Map.entry("bar-color", "채움색"), Map.entry("fill-color", "채움색"), Map.entry("check-color", "체크색"), Map.entry("tooltip", "툴팁"), Map.entry("modal", "모달"));
    static final Set<String> COLOR_KEYS = java.util.Set.of("background", "color", "border-color", "bar-color", "fill-color", "check-color");

    static final Set<String> ATTR_KEYS = Set.of("id", "class", "onClick", "src", "text", "onChange", "onSubmit", "value", "checked",
            "min", "max", "step", "maxlength", "placeholder", "options", "group", "columns", "title", "item", "count", "size", "tooltip", "enabled", "modal");

    enum Kind { TEXT, ENUM, BOOL, NUMBER, COLOR, HEADER }

    record PropRow(String label, String key, String value, boolean editable, Kind kind, List<String> options) {
        PropRow(String label, String key, String value, boolean editable) {
            this(label, key, value, editable, Kind.TEXT, List.of());
        }
    }

    static final Map<String, List<String>> ENUMS = Map.of(
            "anchor", List.of("top-left", "top", "top-right", "left", "center", "right", "bottom-left", "bottom", "bottom-right"),
            "justify", List.of("start", "center", "end", "space-between"),
            "align", List.of("start", "center", "end", "stretch"),
            "align-self", List.of("start", "center", "end", "stretch"),
            "text-align", List.of("left", "center", "right"),
            "overflow", List.of("visible", "hidden"));
    /** 값이 참/거짓인 속성과 그 기본값. */
    static final Map<String, Boolean> BOOLS = Map.of("checked", false, "enabled", true, "visible", true, "shadow", true, "modal", true);
    static final Set<String> NUMBERS = Set.of("width", "height", "gap", "border", "x", "y", "opacity", "min", "max", "step", "maxlength", "columns", "count", "size");

    static Kind kindOf(String key) {
        if (ENUMS.containsKey(key)) return Kind.ENUM;
        if (BOOLS.containsKey(key)) return Kind.BOOL;
        if (COLOR_KEYS.contains(key)) return Kind.COLOR;
        if (NUMBERS.contains(key)) return Kind.NUMBER;
        return Kind.TEXT;
    }

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
            if (!EditorController.isFlow(b.parent)) {
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
