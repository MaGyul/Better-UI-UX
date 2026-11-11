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
        resolver.setViewport(viewW, viewH);
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

    /** 안쪽 여백 + 테두리 두께(테두리는 이제 레이아웃 공간을 차지한다). */
    private Insets padding(LayoutBox b) {
        Insets def = b.tag().equals("button") ? new Insets(4, 8, 4, 8) : Insets.ZERO;
        Insets p = b.style.insets("padding", def);
        int bw = Math.max(0, b.style.integer("border", 0));
        return bw == 0 ? p : new Insets(p.top() + bw, p.right() + bw, p.bottom() + bw, p.left() + bw);
    }

    private Insets margin(LayoutBox b) { return b.style.insets("margin", Insets.ZERO); }

    /** {@code margin} 의 각 변이 {@code auto} 인지 {top,right,bottom,left}. auto 가 없으면 null. */
    private static boolean[] autoMargins(LayoutBox c) {
        String m = c.style.get("margin");
        if (m == null || !m.toLowerCase().contains("auto")) return null;
        List<String> t = dev.magyul.betteruiux.core.style.CssMath.tokens(m);
        String[] f = switch (t.size()) {
            case 1 -> new String[] {t.get(0), t.get(0), t.get(0), t.get(0)};
            case 2 -> new String[] {t.get(0), t.get(1), t.get(0), t.get(1)};
            case 3 -> new String[] {t.get(0), t.get(1), t.get(2), t.get(1)};
            case 4 -> new String[] {t.get(0), t.get(1), t.get(2), t.get(3)};
            default -> null;
        };
        if (f == null) return null;
        boolean[] r = new boolean[4];
        boolean any = false;
        for (int i = 0; i < 4; i++) any |= r[i] = f[i].equalsIgnoreCase("auto");
        return any ? r : null;
    }

    private static boolean isAbsolute(LayoutBox c) {
        String p = c.style.get("position", "");
        return p.equalsIgnoreCase("absolute") || p.equalsIgnoreCase("fixed");
    }

    /** 흐름에 참여하는 보이는 자식(absolute 제외), {@code order} 순(안정 정렬). */
    private List<LayoutBox> visibleChildren(LayoutBox b) {
        List<LayoutBox> r = new ArrayList<>();
        boolean ordered = false;
        for (LayoutBox c : b.children) {
            if (!c.visible() || isAbsolute(c)) continue;
            r.add(c);
            ordered |= c.style.has("order");
        }
        if (ordered) r.sort(java.util.Comparator.comparingInt(c -> c.style.integer("order", 0)));
        return r;
    }

    private static List<LayoutBox> absoluteChildren(LayoutBox b) {
        List<LayoutBox> r = new ArrayList<>();
        for (LayoutBox c : b.children) if (c.visible() && isAbsolute(c)) r.add(c);
        return r;
    }

    /** 1 = 가로 흐름(row, display:flex), 2 = 세로 흐름(column, flex-direction:column), 0 = 흐름 아님. */
    private static int axisOf(LayoutBox b) {
        switch (b.tag()) {
            case "row": return 1;
            case "column": return 2;
            case "scroll": case "grid": case "tabs": return 0;
            default: break;
        }
        String d = b.style.get("display", "");
        if (!d.equalsIgnoreCase("flex") && !d.equalsIgnoreCase("inline-flex")) return 0;
        return b.style.get("flex-direction", "row").toLowerCase().startsWith("column") ? 2 : 1;
    }

    /** {@code aspect-ratio}: {@code 16/9}, {@code 1.5}, {@code auto 4/3}. 없으면 0. */
    private static double ratio(LayoutBox c) {
        String v = c.style.get("aspect-ratio");
        if (v == null) return 0;
        v = v.toLowerCase().replace("auto", "").trim();
        try {
            int slash = v.indexOf('/');
            double r = slash < 0 ? Double.parseDouble(v)
                    : Double.parseDouble(v.substring(0, slash).trim()) / Double.parseDouble(v.substring(slash + 1).trim());
            return r > 0 && Double.isFinite(r) ? r : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String norm(String v) {
        return switch (v) {
            case "flex-start", "baseline", "first baseline", "normal" -> "start";
            case "flex-end" -> "end";
            default -> v;
        };
    }

    private int maxLineWidth(String s) {
        int w = 0;
        for (String line : s.split("\n", -1)) w = Math.max(w, text.width(line));
        return w;
    }

    private int lineCount(String s) { return s.split("\n", -1).length; }

    private int prefW(LayoutBox b) {
        Size s = b.style.size("width", Size.AUTO);
        double r = ratio(b);
        Size hs = b.style.size("height", Size.AUTO);
        int v = switch (s.kind()) {
            case PX -> Math.round(s.value()) + adj(b, true);
            case AUTO -> r > 0 && hs.kind() == Size.Kind.PX ? (int) Math.round((hs.value() + adj(b, false)) * r)
                    : contentW(b) + padding(b).horizontal();
            default -> 0;
        };
        return clamp(b, true, v, 0);
    }

    private int prefH(LayoutBox b) {
        Size s = b.style.size("height", Size.AUTO);
        double r = ratio(b);
        Size ws = b.style.size("width", Size.AUTO);
        int v = switch (s.kind()) {
            case PX -> Math.round(s.value()) + adj(b, false);
            case AUTO -> r > 0 && ws.kind() == Size.Kind.PX ? (int) Math.round((ws.value() + adj(b, true)) / r)
                    : contentH(b) + padding(b).vertical();
            default -> 0;
        };
        return clamp(b, false, v, 0);
    }

    /** {@code box-sizing: content-box} 면 width/height 에 안쪽 여백을 더한다(기본은 border-box: 여백 포함). */
    private int adj(LayoutBox c, boolean horiz) {
        if (!"content-box".equalsIgnoreCase(c.style.get("box-sizing", ""))) return 0;
        Insets p = padding(c);
        return horiz ? p.horizontal() : p.vertical();
    }

    /** {@code min-/max-width/height}(px 또는 %) 적용. min 이 max 보다 우선한다. */
    private int clamp(LayoutBox c, boolean horiz, int v, int avail) {
        int a = adj(c, horiz);
        Integer max = limit(c.style.size(horiz ? "max-width" : "max-height", Size.AUTO), avail, a);
        Integer min = limit(c.style.size(horiz ? "min-width" : "min-height", Size.AUTO), avail, a);
        int r = v;
        if (max != null) r = Math.min(r, max);
        if (min != null) r = Math.max(r, min);
        return r;
    }

    private static Integer limit(Size s, int avail, int adj) {
        return switch (s.kind()) {
            case PX -> Math.round(s.value()) + adj;
            case PERCENT -> Math.round(s.value() * avail) + adj;
            default -> null;
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
        if (axisOf(b) == 1) {
            for (LayoutBox c : kids) v += outerW(c);
            if (!kids.isEmpty()) v += b.style.integer("column-gap", gap) * (kids.size() - 1);
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
        if (axisOf(b) == 2 || b.tag().equals("scroll")) {
            for (LayoutBox c : kids) v += outerH(c);
            if (!kids.isEmpty()) v += b.style.integer("row-gap", gap) * (kids.size() - 1);
        } else {
            for (LayoutBox c : kids) v = Math.max(v, outerH(c));
        }
        return v;
    }

    // ---- 배치 ----

    private void place(LayoutBox b, int x, int y, int w, int h) {
        if ("relative".equalsIgnoreCase(b.style.get("position", ""))) {          // 원래 자리 기준으로 밀기
            x += b.style.integer("left", 0) - b.style.integer("right", 0);
            y += b.style.integer("top", 0) - b.style.integer("bottom", 0);
        }
        b.x = x;
        b.y = y;
        b.w = Math.max(0, w);
        b.h = Math.max(0, h);
        if (isLeaf(b)) return;
        Insets pad = padding(b);
        int cx = x + pad.left(), cy = y + pad.top();
        int cw = Math.max(0, b.w - pad.horizontal()), ch = Math.max(0, b.h - pad.vertical());
        int axis = axisOf(b);
        if (axis != 0) {
            flow(b, axis == 1, cx, cy, cw, ch);
            placeAbsolutes(b);
            return;
        }
        switch (b.tag()) {
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
        placeAbsolutes(b);
    }

    /** {@code position:absolute|fixed}: 부모 상자를 기준으로 top/right/bottom/left 로 배치(없는 축은 anchor + x/y). */
    private void placeAbsolutes(LayoutBox b) {
        for (LayoutBox c : absoluteChildren(b)) {
            Insets m = margin(c);
            int bw = b.w, bh = b.h;
            Integer l = edge(c, "left", bw), r = edge(c, "right", bw), t = edge(c, "top", bh), bo = edge(c, "bottom", bh);
            Size ws = c.style.size("width", Size.AUTO), hs = c.style.size("height", Size.AUTO);
            int w = ws.kind() == Size.Kind.AUTO && l != null && r != null ? bw - l - r - m.horizontal()
                    : resolve(ws, bw - m.horizontal(), prefW(c), adj(c, true));
            int h = hs.kind() == Size.Kind.AUTO && t != null && bo != null ? bh - t - bo - m.vertical()
                    : resolve(hs, bh - m.vertical(), prefH(c), adj(c, false));
            w = clamp(c, true, Math.max(0, w), bw);
            h = clamp(c, false, Math.max(0, h), bh);
            int[] base = anchorPosition(b.x, b.y, bw, bh, m, c.style.get("anchor", "top-left"), w, h);
            int px = l != null ? b.x + l + m.left() : r != null ? b.x + bw - r - w - m.right() : base[0] + c.style.integer("x", 0);
            int py = t != null ? b.y + t + m.top() : bo != null ? b.y + bh - bo - h - m.bottom() : base[1] + c.style.integer("y", 0);
            place(c, px, py, w, h);
        }
    }

    private static Integer edge(LayoutBox c, String key, int full) {
        if (!c.style.has(key)) return null;
        Size s = c.style.size(key, Size.AUTO);
        return switch (s.kind()) {
            case PX -> Math.round(s.value());
            case PERCENT -> Math.round(s.value() * full);
            default -> null;
        };
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
                int w = clamp(c, true, resolve(c.style.size("width", Size.AUTO), colW - mg.horizontal(), prefW(c), adj(c, true)), colW - mg.horizontal());
                int h = clamp(c, false, resolve(c.style.size("height", Size.AUTO), rowH - mg.vertical(), prefH(c), adj(c, false)), rowH - mg.vertical());
                place(c, x + mg.left(), y + mg.top(), w, h);
                x += colW + gap;
            }
            y += rowH + gap;
        }
    }

    private void flow(LayoutBox b, boolean horiz, int cx, int cy, int cw, int ch) {
        List<LayoutBox> items = visibleChildren(b);
        if (items.isEmpty()) return;
        int gap = b.style.integer("gap", 0);
        int mainGap = b.style.integer(horiz ? "column-gap" : "row-gap", gap);
        int crossGap = b.style.integer(horiz ? "row-gap" : "column-gap", gap);
        boolean wrap = b.style.get("flex-wrap", "nowrap").toLowerCase().startsWith("wrap");
        if (!wrap) {
            flowLine(b, items, horiz, cx, cy, cw, ch, mainGap);
            return;
        }
        int mainAvail = horiz ? cw : ch;
        List<List<LayoutBox>> lines = new ArrayList<>();
        List<LayoutBox> line = new ArrayList<>();
        int used = 0;
        for (LayoutBox c : items) {
            Insets m = margin(c);
            int outer = baseMain(c, horiz, mainAvail) + (horiz ? m.horizontal() : m.vertical());
            if (!line.isEmpty() && used + mainGap + outer > mainAvail) {
                lines.add(line);
                line = new ArrayList<>();
                used = 0;
            }
            used += (line.isEmpty() ? 0 : mainGap) + outer;
            line.add(c);
        }
        if (!line.isEmpty()) lines.add(line);
        int crossAvail = horiz ? ch : cw;
        int[] lineCross = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            for (LayoutBox c : lines.get(i)) {
                Insets m = margin(c);
                Size cs = c.style.size(horiz ? "height" : "width", Size.AUTO);
                int size = switch (cs.kind()) {
                    case PX -> Math.round(cs.value()) + adj(c, !horiz);
                    case PERCENT -> Math.round(cs.value() * crossAvail) + adj(c, !horiz);
                    default -> horiz ? prefH(c) : prefW(c);
                };
                lineCross[i] = Math.max(lineCross[i], clamp(c, !horiz, size, crossAvail) + (horiz ? m.vertical() : m.horizontal()));
            }
        }
        boolean reverse = b.style.get("flex-wrap", "").equalsIgnoreCase("wrap-reverse");
        int offset = 0;
        for (int k = 0; k < lines.size(); k++) {
            int i = reverse ? lines.size() - 1 - k : k;
            if (horiz) flowLine(b, lines.get(i), true, cx, cy + offset, cw, lineCross[i], mainGap);
            else flowLine(b, lines.get(i), false, cx + offset, cy, lineCross[i], ch, mainGap);
            offset += lineCross[i] + crossGap;
        }
    }

    /** 줄바꿈 판단용 기본 주축 크기(basis/지정 크기/내용). */
    private int baseMain(LayoutBox c, boolean horiz, int mainAvail) {
        Size s = c.style.size(horiz ? "width" : "height", Size.AUTO);
        int v = switch (s.kind()) {
            case PX -> Math.round(s.value()) + adj(c, horiz);
            case PERCENT -> Math.round(s.value() * mainAvail) + adj(c, horiz);
            default -> basis(c, horiz, mainAvail, horiz ? prefW(c) : prefH(c));
        };
        return clamp(c, horiz, v, mainAvail);
    }

    private int basis(LayoutBox c, boolean horiz, int mainAvail, int fallback) {
        if (!c.style.has("flex-basis")) return fallback;
        Size bs = c.style.size("flex-basis", Size.AUTO);
        return switch (bs.kind()) {
            case PX -> Math.round(bs.value()) + adj(c, horiz);
            case PERCENT -> Math.round(bs.value() * mainAvail) + adj(c, horiz);
            default -> fallback;
        };
    }

    private static float number(LayoutBox c, String key, float def) {
        String v = c.style.get(key);
        if (v == null) return def;
        try {
            return Float.parseFloat(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 한 줄(wrap 이 없으면 전체)을 주축으로 배치한다. 사각형 (cx,cy,cw,ch) 이 이 줄의 영역. */
    private void flowLine(LayoutBox b, List<LayoutBox> items, boolean horiz, int cx, int cy, int cw, int ch, int gap) {
        int n = items.size();
        int mainAvail = horiz ? cw : ch, crossAvail = horiz ? ch : cw;
        String mainKey = horiz ? "width" : "height", crossKey = horiz ? "height" : "width";

        int[] main = new int[n];
        float[] weight = new float[n], grow = new float[n], shrink = new float[n];
        boolean[][] autos = new boolean[n][];
        int used = gap * (n - 1);
        float totalWeight = 0, totalGrow = 0, totalShrink = 0;
        int autoMainCount = 0;
        for (int i = 0; i < n; i++) {
            LayoutBox c = items.get(i);
            Insets m = margin(c);
            int mm = horiz ? m.horizontal() : m.vertical();
            autos[i] = autoMargins(c);
            if (autos[i] != null) {
                boolean[] a = autos[i];
                autoMainCount += horiz ? (a[3] ? 1 : 0) + (a[1] ? 1 : 0) : (a[0] ? 1 : 0) + (a[2] ? 1 : 0);
            }
            Size s = c.style.size(mainKey, Size.AUTO);
            switch (s.kind()) {
                case PX -> main[i] = Math.round(s.value()) + adj(c, horiz);
                case PERCENT -> main[i] = Math.round(s.value() * mainAvail) + adj(c, horiz);
                case AUTO -> main[i] = basis(c, horiz, mainAvail, horiz ? prefW(c) : prefH(c));
                case FILL -> {
                    float g = s.value();
                    if (c.style.has("grow")) g = number(c, "grow", g);
                    weight[i] = Math.max(0.0001f, g);
                    totalWeight += weight[i];
                }
            }
            if (s.kind() != Size.Kind.FILL) {
                main[i] = clamp(c, horiz, main[i], mainAvail);
                grow[i] = Math.max(0, number(c, "grow", 0));
                shrink[i] = Math.max(0, number(c, "flex-shrink", 0));
                totalGrow += grow[i];
                totalShrink += shrink[i] * main[i];
            }
            used += main[i] + mm;
        }
        int free = mainAvail - used;
        if (totalWeight > 0) {
            free = Math.max(0, free);
            int given = 0, lastFill = -1;
            for (int i = 0; i < n; i++) if (weight[i] > 0) lastFill = i;
            for (int i = 0; i < n; i++) {
                if (weight[i] <= 0) continue;
                int share = i == lastFill ? free - given : Math.round(free * weight[i] / totalWeight);
                main[i] = clamp(items.get(i), horiz, Math.max(0, share), mainAvail);
                given += main[i];
            }
            free = 0;
        } else if (free > 0 && totalGrow > 0) {
            int left = free;
            for (int i = 0; i < n; i++) {
                if (grow[i] <= 0) continue;
                int extra = Math.round(free * grow[i] / totalGrow);
                int before = main[i];
                main[i] = clamp(items.get(i), horiz, before + Math.min(extra, left), mainAvail);
                left -= main[i] - before;
            }
            free = Math.max(0, left);
        } else if (free < 0 && totalShrink > 0) {
            int overflow = -free;
            for (int i = 0; i < n; i++) {
                if (shrink[i] <= 0) continue;
                int dec = Math.round(overflow * shrink[i] * main[i] / totalShrink);
                int before = main[i];
                main[i] = clamp(items.get(i), horiz, Math.max(0, before - dec), mainAvail);
                free += before - main[i];
            }
            free = Math.max(0, free);
        } else {
            free = Math.max(0, free);
        }

        String justify = norm(b.style.get("justify", "start"));
        int cursor = 0, betweenExtra = 0, autoShare = 0;
        if (autoMainCount > 0 && free > 0) {
            autoShare = free / autoMainCount;                 // auto 여백이 남는 공간을 나눠 가진다
        } else {
            switch (justify) {
                case "center" -> cursor = free / 2;
                case "end" -> cursor = free;
                case "space-between" -> {
                    if (n > 1) betweenExtra = free / (n - 1);
                }
                case "space-around" -> {
                    betweenExtra = free / n;
                    cursor = betweenExtra / 2;
                }
                case "space-evenly" -> {
                    betweenExtra = free / (n + 1);
                    cursor = betweenExtra;
                }
                default -> { }
            }
        }
        String align = norm(b.style.get("align", "start"));
        for (int i = 0; i < n; i++) {
            LayoutBox c = items.get(i);
            Insets m = margin(c);
            boolean[] au = autos[i];
            int mLead = horiz ? m.left() : m.top(), mTrail = horiz ? m.right() : m.bottom();
            if (au != null) {
                if (horiz ? au[3] : au[0]) mLead += autoShare;
                if (horiz ? au[1] : au[2]) mTrail += autoShare;
            }
            int mCrossLead = horiz ? m.top() : m.left(), mCrossTrail = horiz ? m.bottom() : m.right();
            boolean autoCrossLead = au != null && (horiz ? au[0] : au[3]);
            boolean autoCrossTrail = au != null && (horiz ? au[2] : au[1]);
            String a = norm(c.style.get("align-self", align));

            Size cs = c.style.size(crossKey, Size.AUTO);
            int crossSize = switch (cs.kind()) {
                case PX -> Math.round(cs.value()) + adj(c, !horiz);
                case PERCENT -> Math.round(cs.value() * crossAvail) + adj(c, !horiz);
                case FILL -> Math.max(0, crossAvail - mCrossLead - mCrossTrail);
                case AUTO -> a.equals("stretch") && !autoCrossLead && !autoCrossTrail
                        ? Math.max(0, crossAvail - mCrossLead - mCrossTrail)
                        : (horiz ? prefH(c) : prefW(c));
            };
            double ar = ratio(c);
            if (ar > 0 && cs.kind() == Size.Kind.AUTO) {
                crossSize = (int) Math.round(horiz ? main[i] / ar : main[i] * ar);
            }
            crossSize = clamp(c, !horiz, crossSize, crossAvail);
            int crossOuter = crossSize + mCrossLead + mCrossTrail;
            int crossFree = crossAvail - crossOuter;
            int crossPos;
            if (autoCrossLead && autoCrossTrail) crossPos = crossFree / 2;
            else if (autoCrossLead) crossPos = crossFree;
            else if (autoCrossTrail) crossPos = 0;
            else crossPos = switch (a) {
                case "center" -> crossFree / 2;
                case "end" -> crossFree;
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
            int w = clamp(c, true, resolve(c.style.size("width", Size.AUTO), cw - m.horizontal(), prefW(c), adj(c, true)), cw - m.horizontal());
            int h = clamp(c, false, resolve(c.style.size("height", Size.AUTO), ch - m.vertical(), prefH(c), adj(c, false)), ch - m.vertical());
            double ar = ratio(c);
            if (ar > 0) {
                boolean wAuto = c.style.size("width", Size.AUTO).kind() == Size.Kind.AUTO;
                boolean hAuto = c.style.size("height", Size.AUTO).kind() == Size.Kind.AUTO;
                if (hAuto && !wAuto) h = clamp(c, false, (int) Math.round(w / ar), ch - m.vertical());
                else if (wAuto && !hAuto) w = clamp(c, true, (int) Math.round(h * ar), cw - m.horizontal());
            }
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

    private static int resolve(Size s, int avail, int auto, int adj) {
        return switch (s.kind()) {
            case PX -> Math.round(s.value()) + adj;
            case PERCENT -> Math.round(s.value() * avail) + adj;
            case FILL -> Math.max(0, avail);
            case AUTO -> auto;
        };
    }
}
