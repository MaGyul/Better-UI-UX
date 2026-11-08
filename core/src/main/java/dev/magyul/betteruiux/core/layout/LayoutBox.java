package dev.magyul.betteruiux.core.layout;

import dev.magyul.betteruiux.core.xml.UiNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 레이아웃 결과: 노드 하나의 최종 사각형(화면 절대 좌표, GUI 픽셀)과 해석된 스타일. */
public final class LayoutBox {
    public final UiNode node;
    public final List<LayoutBox> children = new ArrayList<>();
    public LayoutBox parent;
    /** 렌더되는 형제들 사이에서의 순서(0부터). */
    public int index;
    public int x, y, w, h;

    /** 레이아웃 기준 스타일(상태 무관). */
    public StyleMap style = new StyleMap();
    /** 동적 의사 클래스별 추가 스타일: 키는 "hover", "active", "active+hover". 그리기에만 영향(레이아웃 불변). */
    public Map<String, StyleMap> overlays = Map.of();
    private final StyleMap[] effectiveCache = new StyleMap[4];

    LayoutBox(UiNode node) { this.node = node; }

    public String tag() { return node.tag(); }

    public String id() { return node.attr("id"); }

    public boolean contains(int px, int py) { return px >= x && px < x + w && py >= y && py < y + h; }

    public boolean visible() { return style.bool("visible", true) && !"none".equals(style.get("display")); }

    public boolean enabled() { return style.bool("enabled", true); }

    /** 호버/눌림 상태를 반영한 그리기용 스타일. 오버레이가 없으면 {@link #style} 그대로. */
    public StyleMap effective(boolean hovered, boolean active) {
        if (overlays.isEmpty() || (!hovered && !active)) return style;
        int k = (hovered ? 1 : 0) | (active ? 2 : 0);
        StyleMap cached = effectiveCache[k];
        if (cached != null) return cached;
        StyleMap m = style.copy();
        if (hovered && overlays.containsKey("hover")) m.putAll(overlays.get("hover"));
        if (active && overlays.containsKey("active")) m.putAll(overlays.get("active"));
        if (hovered && active && overlays.containsKey("active+hover")) m.putAll(overlays.get("active+hover"));
        effectiveCache[k] = m;
        return m;
    }
}
