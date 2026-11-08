package dev.magyul.betteruiux.core.layout;

import dev.magyul.betteruiux.core.xml.UiNode;
import java.util.ArrayList;
import java.util.List;

/** 레이아웃 결과: 노드 하나의 최종 사각형(화면 절대 좌표, GUI 픽셀)과 해석된 스타일. */
public final class LayoutBox {
    public final UiNode node;
    public final StyleMap style;
    public final List<LayoutBox> children = new ArrayList<>();
    public LayoutBox parent;
    public int x, y, w, h;

    LayoutBox(UiNode node, StyleMap style) {
        this.node = node;
        this.style = style;
    }

    public String tag() { return node.tag(); }

    public String id() { return node.attr("id"); }

    public boolean contains(int px, int py) { return px >= x && px < x + w && py >= y && py < y + h; }

    public boolean visible() { return style.bool("visible", true) && !"none".equals(style.get("display")); }

    public boolean enabled() { return style.bool("enabled", true); }
}
