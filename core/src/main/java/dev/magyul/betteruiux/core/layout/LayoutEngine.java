package dev.magyul.betteruiux.core.layout;

import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.core.style.StyleResolver;
import dev.magyul.betteruiux.core.xml.UiNode;
import java.util.ArrayList;
import java.util.List;

/**
 * XML 노드 트리 → {@link LayoutBox} 트리. flex 유사 모델:
 * <ul>
 *   <li>{@code row}/{@code column}: 주축 배치. width/height = px | % | auto | fill, gap, justify, align</li>
 *   <li>{@code stack}/{@code panel}/{@code screen}/기타: 자식을 anchor + x,y 오프셋으로 배치</li>
 *   <li>리프: {@code label button image spacer}</li>
 * </ul>
 * 좌표는 숫자 대신 anchor/margin/padding 으로 표현하는 것이 기본이다.
 */
public final class LayoutEngine {
    private final TextMetrics text;

    private final StyleResolver resolver;

    public LayoutEngine(TextMetrics text) { this(text, new StyleResolver(List.of())); }

    public LayoutEngine(TextMetrics text, StyleResolver resolver) {
        this.text = text;
        this.resolver = resolver;
    }

    public LayoutBox layout(UiNode root, int viewW, int viewH) {
        LayoutBox box = build(root, null);
        place(box, 0, 0, viewW, viewH);
        return box;
    }

    // ---- 트리 구성 ----

    /** 구조(박스 트리)를 먼저 모두 만든 뒤, 위에서 아래로 스타일을 해석한다(형제/조상 선택자가 구조를 참조하므로). */
    private LayoutBox build(UiNode node, LayoutBox parent) {
        LayoutBox root = buildStructure(node, parent);
        resolveStyles(root);
        return root;
    }

    /** 레이아웃 대상이 아닌 메타 요소(스타일 정의 등). */
    private static boolean isMeta(String tag) {
        return tag.equals("style") || tag.equals("link");
    }

    private LayoutBox buildStructure(UiNode node, LayoutBox parent) {
        LayoutBox b = new LayoutBox(node);
        b.parent = parent;
        for (UiNode c : node.children()) {
            if (isMeta(c.tag())) continue;
            LayoutBox child = buildStructure(c, b);
            child.index = b.children.size();
            b.children.add(child);
        }
        return b;
    }

    private void resolveStyles(LayoutBox b) {
        resolver.resolve(b);
        for (LayoutBox c : b.children) resolveStyles(c);
    }

    // ---- 내재 크기 (auto) ----

    private static boolean isLeaf(String tag) {
        return switch (tag) {
            case "label", "button", "image", "spacer" -> true;
            default -> false;
        };
    }

    public static String textOf(LayoutBox b) {
        String t = b.style.get("text");
        return t != null ? t : b.node.text().trim();
    }

    private Insets padding(LayoutBox b) {
        Insets def = b.tag().equals("button") ? new Insets(4, 8, 4, 8) : Insets.ZERO;
        return b.style.insets("padding", def);
    }

    private Insets margin(LayoutBox b) { return b.style.insets("margin", Insets.ZERO); }

    private List<LayoutBox> visibleChildren(LayoutBox b) {
        List<LayoutBox> r = new ArrayList<>();
        for (LayoutBox c : b.children) if (c.visible()) r.add(c);
        return r;
    }

    private int maxLineWidth(String s) {
        int w = 0;
        for (String line : s.split("\n", -1)) w = Math.max(w, text.width(line));
        return w;
    }

    private int lineCount(String s) { return s.split("\n", -1).length; }

    private int prefW(LayoutBox b) {
        Size s = b.style.size("width", Size.AUTO);
        return switch (s.kind()) {
            case PX -> Math.round(s.value());
            case AUTO -> contentW(b) + padding(b).horizontal();
            default -> 0;
        };
    }

    private int prefH(LayoutBox b) {
        Size s = b.style.size("height", Size.AUTO);
        return switch (s.kind()) {
            case PX -> Math.round(s.value());
            case AUTO -> contentH(b) + padding(b).vertical();
            default -> 0;
        };
    }

    private int outerW(LayoutBox b) { return prefW(b) + margin(b).horizontal(); }

    private int outerH(LayoutBox b) { return prefH(b) + margin(b).vertical(); }

    private int contentW(LayoutBox b) {
        switch (b.tag()) {
            case "label", "button": return maxLineWidth(textOf(b));
            case "image": return 16;
            case "spacer": return 0;
            default: break;
        }
        List<LayoutBox> kids = visibleChildren(b);
        int gap = b.style.integer("gap", 0);
        int v = 0;
        if (b.tag().equals("row")) {
            for (LayoutBox c : kids) v += outerW(c);
            if (!kids.isEmpty()) v += gap * (kids.size() - 1);
        } else {
            for (LayoutBox c : kids) v = Math.max(v, outerW(c));
        }
        return v;
    }

    private int contentH(LayoutBox b) {
        switch (b.tag()) {
            case "label", "button": return lineCount(textOf(b)) * text.lineHeight();
            case "image": return 16;
            case "spacer": return 0;
            default: break;
        }
        List<LayoutBox> kids = visibleChildren(b);
        int gap = b.style.integer("gap", 0);
        int v = 0;
        if (b.tag().equals("column")) {
            for (LayoutBox c : kids) v += outerH(c);
            if (!kids.isEmpty()) v += gap * (kids.size() - 1);
        } else {
            for (LayoutBox c : kids) v = Math.max(v, outerH(c));
        }
        return v;
    }

    // ---- 배치 ----

    private void place(LayoutBox b, int x, int y, int w, int h) {
        b.x = x;
        b.y = y;
        b.w = Math.max(0, w);
        b.h = Math.max(0, h);
        if (isLeaf(b.tag())) return;
        Insets pad = padding(b);
        int cx = x + pad.left(), cy = y + pad.top();
        int cw = Math.max(0, b.w - pad.horizontal()), ch = Math.max(0, b.h - pad.vertical());
        switch (b.tag()) {
            case "row" -> flow(b, true, cx, cy, cw, ch);
            case "column" -> flow(b, false, cx, cy, cw, ch);
            default -> stack(b, cx, cy, cw, ch);
        }
    }

    private void flow(LayoutBox b, boolean horiz, int cx, int cy, int cw, int ch) {
        List<LayoutBox> items = visibleChildren(b);
        int n = items.size();
        if (n == 0) return;
        int gap = b.style.integer("gap", 0);
        int mainAvail = horiz ? cw : ch, crossAvail = horiz ? ch : cw;
        String mainKey = horiz ? "width" : "height", crossKey = horiz ? "height" : "width";

        int[] main = new int[n];
        float[] weight = new float[n];
        int used = gap * (n - 1);
        float totalWeight = 0;
        for (int i = 0; i < n; i++) {
            LayoutBox c = items.get(i);
            Insets m = margin(c);
            int mm = horiz ? m.horizontal() : m.vertical();
            Size s = c.style.size(mainKey, Size.AUTO);
            switch (s.kind()) {
                case PX -> main[i] = Math.round(s.value());
                case PERCENT -> main[i] = Math.round(s.value() * mainAvail);
                case AUTO -> main[i] = horiz ? prefW(c) : prefH(c);
                case FILL -> {
                    float g = s.value();
                    if (c.style.has("grow")) {
                        try {
                            g = Float.parseFloat(c.style.get("grow"));
                        } catch (NumberFormatException ignored) {
                            // 잘못된 grow 값은 기본 가중치 사용
                        }
                    }
                    weight[i] = Math.max(0.0001f, g);
                    totalWeight += weight[i];
                }
            }
            used += main[i] + mm;
        }
        int free = Math.max(0, mainAvail - used);
        if (totalWeight > 0) {
            int given = 0, lastFill = -1;
            for (int i = 0; i < n; i++) if (weight[i] > 0) lastFill = i;
            for (int i = 0; i < n; i++) {
                if (weight[i] <= 0) continue;
                int share = i == lastFill ? free - given : Math.round(free * weight[i] / totalWeight);
                main[i] = Math.max(0, share);
                given += main[i];
            }
            free = 0;
        }

        String justify = b.style.get("justify", "start");
        int cursor = 0;
        int betweenExtra = 0;
        switch (justify) {
            case "center" -> cursor = free / 2;
            case "end" -> cursor = free;
            case "space-between" -> {
                if (n > 1) betweenExtra = free / (n - 1);
            }
            default -> { }
        }
        String align = b.style.get("align", "start");
        for (int i = 0; i < n; i++) {
            LayoutBox c = items.get(i);
            Insets m = margin(c);
            int mLead = horiz ? m.left() : m.top(), mTrail = horiz ? m.right() : m.bottom();
            int mCrossLead = horiz ? m.top() : m.left(), mCrossTrail = horiz ? m.bottom() : m.right();
            String a = c.style.get("align-self", align);

            Size cs = c.style.size(crossKey, Size.AUTO);
            int crossSize = switch (cs.kind()) {
                case PX -> Math.round(cs.value());
                case PERCENT -> Math.round(cs.value() * crossAvail);
                case FILL -> Math.max(0, crossAvail - mCrossLead - mCrossTrail);
                case AUTO -> a.equals("stretch") ? Math.max(0, crossAvail - mCrossLead - mCrossTrail)
                        : (horiz ? prefH(c) : prefW(c));
            };
            int crossOuter = crossSize + mCrossLead + mCrossTrail;
            int crossPos = switch (a) {
                case "center" -> (crossAvail - crossOuter) / 2;
                case "end" -> crossAvail - crossOuter;
                default -> 0;
            };
            int mainPos = cursor + mLead;
            int px = horiz ? cx + mainPos : cx + crossPos + mCrossLead;
            int py = horiz ? cy + crossPos + mCrossLead : cy + mainPos;
            place(c, px, py, horiz ? main[i] : crossSize, horiz ? crossSize : main[i]);
            cursor += mLead + main[i] + mTrail + gap + betweenExtra;
        }
    }

    private void stack(LayoutBox b, int cx, int cy, int cw, int ch) {
        for (LayoutBox c : visibleChildren(b)) {
            Insets m = margin(c);
            int w = resolve(c.style.size("width", Size.AUTO), cw - m.horizontal(), prefW(c));
            int h = resolve(c.style.size("height", Size.AUTO), ch - m.vertical(), prefH(c));
            String anchor = c.style.get("anchor", "top-left").toLowerCase();
            boolean left = anchor.contains("left"), right = anchor.contains("right");
            boolean top = anchor.contains("top"), bottom = anchor.contains("bottom");
            int px = left ? cx + m.left() : right ? cx + cw - w - m.right() : cx + (cw - w) / 2;
            int py = top ? cy + m.top() : bottom ? cy + ch - h - m.bottom() : cy + (ch - h) / 2;
            px += c.style.integer("x", 0);
            py += c.style.integer("y", 0);
            place(c, px, py, w, h);
        }
    }

    private static int resolve(Size s, int avail, int auto) {
        return switch (s.kind()) {
            case PX -> Math.round(s.value());
            case PERCENT -> Math.round(s.value() * avail);
            case FILL -> Math.max(0, avail);
            case AUTO -> auto;
        };
    }
}
