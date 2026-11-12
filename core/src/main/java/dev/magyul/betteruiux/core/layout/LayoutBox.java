package dev.magyul.betteruiux.core.layout;

import dev.magyul.betteruiux.core.doc.UiWidget;
import dev.magyul.betteruiux.core.style.Selector;
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
    /** {@code ::before}/{@code ::after} 로 만든 가상 상자(문서에 없다 → 선택/클릭/편집 대상이 아니다). */
    public boolean synthetic;
    /** label/button 에 {@code ::before}/{@code ::after} 가 있으면 글자 앞뒤에 붙는 내용(없으면 null). */
    public String textBefore, textAfter;
    /** 가상 상자일 때 "before" 또는 "after". */
    public String pseudo;
    /** 커스텀 태그면 그 위젯, 아니면 null. */
    public UiWidget widget;

    /** 레이아웃 기준 스타일(상태 무관). */
    public StyleMap style = new StyleMap();
    /**
     * :hover/:active/:focus 가 들어간 규칙들(우선순위 오름차순). 규칙마다 일반/!important 선언이 따로 해석돼 있다.
     * 어떤 요소가 어떤 상태인지는 그릴 때 판정하므로 부모나 형제의 상태에 따른 규칙({@code .card:hover .title})도 된다.
     * 그리기에만 영향을 주고 레이아웃은 바꾸지 않는다.
     */
    public List<DynRule> dynRules = List.of();
    private final Map<Long, StyleMap> dynCache = new java.util.HashMap<>();

    public record DynRule(Selector selector, StyleMap normal, StyleMap important, int specificity, int ordinal) {}

    LayoutBox(UiNode node) { this.node = node; }

    public String tag() { return node.tag(); }

    public String id() { return node.attr("id"); }

    public boolean contains(int px, int py) { return px >= x && px < x + w && py >= y && py < y + h; }

    /** 선택되지 않은 탭의 내용(보이지 않지만 에디터의 구조 트리에는 나온다). */
    public boolean inactive;

    public boolean visible() { return !inactive && style.bool("visible", true) && !"none".equals(style.get("display")); }

    public boolean enabled() { return style.bool("enabled", true); }

    /** 그리기(와 히트 테스트의 역순) 순서: {@code z-index} 오름차순, 같으면 문서 순서. */
    public List<LayoutBox> paintOrder() {
        boolean any = false;
        for (LayoutBox c : children) if (c.style.has("z-index")) { any = true; break; }
        if (!any) return children;
        List<LayoutBox> r = new ArrayList<>(children);
        r.sort(java.util.Comparator.comparingInt(c -> c.style.integer("z-index", 0)));
        return r;
    }

    /** 상태 규칙(:hover 등) 중 레이아웃에 영향을 주는 속성을 가진 것이 있는지(레이아웃 때 한 번 정해진다). */
    public boolean layoutDyn;
    /** 마지막 레이아웃 때 맞았던 레이아웃 상태 규칙의 비트마스크(바뀌면 다시 배치해야 한다). */
    public long layoutDynMask;

    /** 레이아웃에 영향을 주는 속성을 가진 상태 규칙 중 {@code st} 에서 맞는 것의 비트마스크. */
    public long layoutHitMask(Selector.DynState st) {
        long mask = 0;
        for (int i = 0; i < dynRules.size() && i < 62; i++) {
            DynRule r = dynRules.get(i);
            if (hasLayoutKey(r) && r.selector().matches(node, st)) mask |= 1L << i;
        }
        return mask;
    }

    static boolean hasLayoutKey(DynRule r) {
        for (String k : r.normal().entries().keySet()) if (isLayoutKey(k)) return true;
        for (String k : r.important().entries().keySet()) if (isLayoutKey(k)) return true;
        return false;
    }

    /** 값이 바뀌면 다시 배치해야 하는 속성(그리기 전용 속성이 아닌 것 중 크기/배치/글자 크기 관련). */
    public static boolean isLayoutKey(String k) {
        if (dev.magyul.betteruiux.core.anim.Interpolator.LAYOUT.contains(k)) return true;
        return switch (k) {
            case "display", "position", "top", "right", "bottom", "left", "anchor", "x", "y", "flex-direction", "flex-wrap", "flex-basis",
                 "flex-shrink", "grow", "order", "justify", "align", "align-self", "align-content", "justify-self", "columns",
                 "grid-template-columns", "grid-template-rows", "grid-column", "grid-row", "box-sizing", "aspect-ratio", "border",
                 "border-top-width", "border-right-width", "border-bottom-width", "border-left-width", "white-space", "text-transform",
                 "font-weight", "visible", "margin", "padding" -> true;
            default -> false;
        };
    }

    /** 호버/눌림 상태를 반영한 그리기용 스타일(이 요소 자신의 상태만 아는 단순 형태). 규칙이 없으면 {@link #style} 그대로. */
    public StyleMap effective(boolean hovered, boolean active) {
        if (dynRules.isEmpty()) return style;
        return effective(new Selector.DynState() {
            @Override public boolean hovered(UiNode n) { return hovered && n == node; }

            @Override public boolean pressed(UiNode n) { return active && n == node; }

            @Override public boolean focused(UiNode n) { return false; }
        });
    }

    /** 문서 전체의 상태({@code st})를 반영한 그리기용 스타일. 맞은 규칙 조합별로 결과를 캐시한다. */
    public StyleMap effective(Selector.DynState st) {
        if (dynRules.isEmpty()) return style;
        int n = dynRules.size();
        long mask = 0;
        boolean[] hit = new boolean[n];
        boolean any = false;
        for (int i = 0; i < n; i++) {
            if (dynRules.get(i).selector().matches(node, st)) {
                hit[i] = true;
                any = true;
                if (i < 62) mask |= 1L << i;
            }
        }
        if (!any) return style;
        boolean cacheable = n <= 62;
        if (cacheable) {
            StyleMap cached = dynCache.get(mask);
            if (cached != null) return cached;
        }
        StyleMap m = style.copy();
        for (int i = 0; i < n; i++) if (hit[i]) m.putAll(dynRules.get(i).normal());
        for (int i = 0; i < n; i++) if (hit[i]) m.putAll(dynRules.get(i).important());
        if (cacheable) dynCache.put(mask, m);
        return m;
    }
}
