package dev.magyul.betteruiux.core.layout;

import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.core.doc.BuiltinWidgets;
import dev.magyul.betteruiux.core.doc.UiWidget;
import dev.magyul.betteruiux.core.style.StyleResolver;
import dev.magyul.betteruiux.core.xml.UiNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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

    private final Function<String, String> interpolator;
    private Function<String, int[]> imageSizer = src -> null;

    /** 이미지의 원래 크기를 알려 주는 함수(모르면 null → 기본 16x16). width/height 가 auto 일 때 이 크기를 쓴다. */
    public void setImageSizer(Function<String, int[]> sizer) { this.imageSizer = sizer == null ? src -> null : sizer; }

    private int naturalSize(LayoutBox b, int axis) {
        String src = b.style.get("src");
        int[] size = src == null ? null : imageSizer.apply(src);
        return size == null ? 16 : Math.max(1, size[axis]);
    }
    private final Function<UiNode, UiWidget> widgets;

    public LayoutEngine(TextMetrics text) { this(text, new StyleResolver(List.of())); }

    public LayoutEngine(TextMetrics text, StyleResolver resolver) { this(text, resolver, s -> s, n -> null); }

    /**
     * @param interpolator 텍스트의 {@code {이름}} 바인딩 치환
     * @param widgets      커스텀 위젯 조회(등록되지 않은 태그는 null)
     */
    public LayoutEngine(TextMetrics text, StyleResolver resolver,
                        Function<String, String> interpolator, Function<UiNode, UiWidget> widgets) {
        this.text = text;
        this.resolver = resolver;
        this.interpolator = interpolator;
        this.widgets = widgets;
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
        return tag.equals("style") || tag.equals("link") || tag.equals("template") || tag.equals("script");
    }

    private LayoutBox buildStructure(UiNode node, LayoutBox parent) {
        LayoutBox b = new LayoutBox(node);
        b.parent = parent;
        b.widget = widgets.apply(node);
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
        if (b.tag().equals("tabs") && b.widget instanceof BuiltinWidgets.Tabs tabs) {   // 선택된 탭만 보인다
            int active = tabs.active(), i = 0;
            for (LayoutBox c : b.children) {
                if (c.tag().equals("tab") && i++ != active) c.inactive = true;
            }
        }
    }

    // ---- 내재 크기 (auto) ----

    private static boolean isLeaf(LayoutBox b) {
        if (b.widget != null) return !b.widget.container();
        return switch (b.tag()) {
            case "label", "button", "image", "video", "spacer", "item", "separator" -> true;
            default -> false;
        };
    }

    /** 바인딩 치환 전의 텍스트({@code text} 속성 우선, 없으면 본문). */
    public static String rawText(LayoutBox b) {
        String t = b.style.get("text");
        return t != null ? t : b.node.text().trim();
    }

    /** 바인딩({@code {이름}})까지 치환된 표시용 텍스트. */
    public String textOf(LayoutBox b) { return interpolator.apply(rawText(b)); }

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

    /**
     * 내용이 잘리지 않는 최소 크기 {너비, 높이} (자식들의 합/최댓값 + 간격 + 안쪽 여백). fill/% 크기의 자식은 0 으로 본다.
     * 에디터가 row/column 을 내용보다 작게 줄이지 못하게 막는 데 쓴다.
     */
    public int[] minimumSize(LayoutBox b) {
        Insets pad = padding(b);
        return new int[] {contentW(b) + pad.horizontal(), contentH(b) + pad.vertical()};
    }

    private int outerW(LayoutBox b) { return prefW(b) + margin(b).horizontal(); }

    private int outerH(LayoutBox b) { return prefH(b) + margin(b).vertical(); }

    private int contentW(LayoutBox b) {
        if (b.widget != null && !b.widget.container()) return b.widget.intrinsicWidth();
        switch (b.tag()) {
            case "label", "button": return maxLineWidth(textOf(b));
            case "image", "video": return naturalSize(b, 0);
            case "item": return Math.max(1, b.style.integer("size", 16));
            case "spacer", "separator": return 0;
            case "grid": return gridMetrics(b)[0][0];
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
        if (b.widget != null && !b.widget.container()) return b.widget.intrinsicHeight();
        switch (b.tag()) {
            case "label", "button": return lineCount(textOf(b)) * text.lineHeight();
            case "image", "video": return naturalSize(b, 1);
            case "item": return Math.max(1, b.style.integer("size", 16));
            case "spacer", "separator": return 0;
            case "grid": return gridMetrics(b)[1][0];
            default: break;
        }
        List<LayoutBox> kids = visibleChildren(b);
        int gap = b.style.integer("gap", 0);
        int v = 0;
        if (b.tag().equals("column") || b.tag().equals("scroll")) {
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
        if (isLeaf(b)) return;
        Insets pad = padding(b);
        int cx = x + pad.left(), cy = y + pad.top();
        int cw = Math.max(0, b.w - pad.horizontal()), ch = Math.max(0, b.h - pad.vertical());
        switch (b.tag()) {
            case "row" -> flow(b, true, cx, cy, cw, ch);
            case "column" -> flow(b, false, cx, cy, cw, ch);
            case "grid" -> grid(b, cx, cy);
            case "scroll" -> {
                int content = contentH(b), bar = 0, offset = 0;
                if (b.widget instanceof BuiltinWidgets.Scroll sw) {
                    sw.setContent(content, ch);
                    offset = sw.scrollY();
                    if (content > ch) bar = 5;                  // 스크롤 막대 자리
                }
                flow(b, false, cx, cy - offset, Math.max(0, cw - bar), Math.max(ch, content));
            }
            case "tabs" -> {
                int header = b.widget instanceof BuiltinWidgets.Tabs t ? t.headerHeight() : 0;
                stack(b, cx, cy + header, cw, Math.max(0, ch - header));
            }
            default -> stack(b, cx, cy, cw, ch);
        }
    }

    // ---- grid ----

    /** {{열 너비들 합(0번)...}, {행 높이들 합(0번)...}} 대신 [0]=열 너비 배열, [1]=행 높이 배열의 첫 칸에 전체 크기를 담아 돌려준다. */
    private int[][] gridMetrics(LayoutBox b) {
        int cols = Math.max(1, b.style.integer("columns", 2)), gap = b.style.integer("gap", 0);
        List<LayoutBox> items = visibleChildren(b);
        int usedCols = Math.min(cols, Math.max(1, items.size())), rows = (items.size() + cols - 1) / cols;
        int[] colW = new int[usedCols], rowH = new int[Math.max(rows, 0)];
        for (int i = 0; i < items.size(); i++) {
            LayoutBox c = items.get(i);
            colW[i % cols] = Math.max(colW[i % cols], outerW(c));
            rowH[i / cols] = Math.max(rowH[i / cols], outerH(c));
        }
        int totalW = 0, totalH = 0;
        for (int w : colW) totalW += w;
        for (int h : rowH) totalH += h;
        if (items.size() > 0) {
            totalW += gap * (usedCols - 1);
            totalH += gap * (rows - 1);
        }
        return new int[][] {Arrays_prepend(totalW, colW), Arrays_prepend(totalH, rowH)};
    }

    private static int[] Arrays_prepend(int first, int[] rest) {
        int[] out = new int[rest.length + 1];
        out[0] = first;
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }

    private void grid(LayoutBox b, int cx, int cy) {
        int cols = Math.max(1, b.style.integer("columns", 2)), gap = b.style.integer("gap", 0);
        List<LayoutBox> items = visibleChildren(b);
        int[][] m = gridMetrics(b);
        int y = cy;
        for (int r = 0; r * cols < items.size(); r++) {
            int x = cx, rowH = m[1][r + 1];
            for (int k = 0; k < cols && r * cols + k < items.size(); k++) {
                LayoutBox c = items.get(r * cols + k);
                Insets mg = margin(c);
                int colW = m[0][k + 1];
                int w = resolve(c.style.size("width", Size.AUTO), colW - mg.horizontal(), prefW(c));
                int h = resolve(c.style.size("height", Size.AUTO), rowH - mg.vertical(), prefH(c));
                place(c, x + mg.left(), y + mg.top(), w, h);
                x += colW + gap;
            }
            y += rowH + gap;
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
            int[] base = anchorPosition(cx, cy, cw, ch, m, c.style.get("anchor", "top-left"), w, h);
            place(c, base[0] + c.style.integer("x", 0), base[1] + c.style.integer("y", 0), w, h);
        }
    }

    /**
     * anchor 로 정해지는 자식의 왼쪽 위 좌표(x/y 오프셋을 더하기 전). 부모 안쪽 영역 (cx,cy,cw,ch) 기준.
     * 에디터가 드래그 결과를 x/y 오프셋으로 되돌릴 때 같은 공식을 쓰도록 공개한다.
     */
    public static int[] anchorPosition(int cx, int cy, int cw, int ch, Insets m, String anchor, int w, int h) {
        String a = anchor == null ? "top-left" : anchor.toLowerCase();
        boolean left = a.contains("left"), right = a.contains("right");
        boolean top = a.contains("top"), bottom = a.contains("bottom");
        int px = left ? cx + m.left() : right ? cx + cw - w - m.right() : cx + (cw - w) / 2;
        int py = top ? cy + m.top() : bottom ? cy + ch - h - m.bottom() : cy + (ch - h) / 2;
        return new int[] {px, py};
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
