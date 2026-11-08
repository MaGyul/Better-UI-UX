package dev.magyul.betteruiux.core.edit;

import dev.magyul.betteruiux.core.layout.Insets;
import dev.magyul.betteruiux.core.layout.LayoutEngine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 에디터의 화면 좌표 계산. "마우스로 이렇게 움직였다" 를 "XML 에는 이 속성 값을 쓰면 된다" 로 바꾼다.
 * 배치 공식은 {@link LayoutEngine#anchorPosition} 을 그대로 써서 렌더링 결과와 어긋나지 않게 한다.
 */
public final class Geometry {
    /** {@link #propsForRect} 의 curX/curY 로 "지금 오프셋을 모른다(새 기준점이라 항상 새로 계산해서 쓴다)" 를 뜻한다. */
    public static final int UNKNOWN = Integer.MIN_VALUE;

    private Geometry() {}

    public record Rect(int x, int y, int w, int h) {
        public int right() { return x + w; }

        public int bottom() { return y + h; }

        public int centerX() { return x + w / 2; }

        public int centerY() { return y + h / 2; }

        public boolean contains(int px, int py) { return px >= x && px < x + w && py >= y && py < y + h; }

        public Rect translate(int dx, int dy) { return new Rect(x + dx, y + dy, w, h); }
    }

    /** 크기 조절 손잡이(8방향)와 몸통(이동). */
    public enum Handle { NW, N, NE, W, E, SW, S, SE, BODY }

    /** 손잡이를 (dx,dy) 만큼 끌었을 때의 새 사각형. 최소 크기 {@code min} 아래로는 줄어들지 않는다. */
    public static Rect drag(Rect start, Handle h, int dx, int dy, int min) {
        int x = start.x(), y = start.y(), w = start.w(), hh = start.h();
        if (h == Handle.BODY) return new Rect(x + dx, y + dy, w, hh);
        if (h == Handle.W || h == Handle.NW || h == Handle.SW) {
            int nw = Math.max(min, w - dx);
            x += w - nw;
            w = nw;
        }
        if (h == Handle.E || h == Handle.NE || h == Handle.SE) w = Math.max(min, w + dx);
        if (h == Handle.N || h == Handle.NW || h == Handle.NE) {
            int nh = Math.max(min, hh - dy);
            y += hh - nh;
            hh = nh;
        }
        if (h == Handle.S || h == Handle.SW || h == Handle.SE) hh = Math.max(min, hh + dy);
        return new Rect(x, y, w, hh);
    }

    /** 손잡이의 화면 위치(중심). */
    public static int[] handleCenter(Rect r, Handle h) {
        int cx = switch (h) {
            case NW, W, SW -> r.x();
            case N, S, BODY -> r.centerX();
            default -> r.right();
        };
        int cy = switch (h) {
            case NW, N, NE -> r.y();
            case W, E, BODY -> r.centerY();
            default -> r.bottom();
        };
        return new int[] {cx, cy};
    }

    /**
     * 사각형 변화 → XML 에 기록할 속성들. null 값은 "제거".
     * <ul>
     *   <li>width/height: 크기가 바뀌었을 때 px 값으로 기록</li>
     *   <li>x/y: stack 계열 부모에서만. anchor 기준 위치에서 얼마나 떨어졌는지(오프셋)로 기록. 0 이면 제거</li>
     *   <li>row/column 부모({@code flow})에서는 위치를 배치가 정하므로 x/y 를 쓰지 않는다</li>
     * </ul>
     *
     * @param parentContent 부모의 안쪽 영역(패딩 제외)
     * @param curX curY     지금 적용된 x/y 오프셋
     */
    public static Map<String, String> propsForRect(boolean flow, String anchor, Insets margin, Rect parentContent,
                                                   Rect before, Rect after, int curX, int curY) {
        Map<String, String> out = new LinkedHashMap<>();
        if (after.w() != before.w()) out.put("width", Integer.toString(after.w()));
        if (after.h() != before.h()) out.put("height", Integer.toString(after.h()));
        if (!flow) {
            int[] base = LayoutEngine.anchorPosition(parentContent.x(), parentContent.y(), parentContent.w(),
                    parentContent.h(), margin, anchor, after.w(), after.h());
            int ox = after.x() - base[0], oy = after.y() - base[1];
            // curX/curY 가 UNKNOWN 이면(기준점이 바뀐 경우) 새 기준점 기준으로 항상 다시 쓴다.
            if (curX == UNKNOWN || ox != curX) out.put("x", ox == 0 ? null : Integer.toString(ox));
            if (curY == UNKNOWN || oy != curY) out.put("y", oy == 0 ? null : Integer.toString(oy));
        }
        return out;
    }

    /**
     * row/column 안에서 드래그 중인 요소가 들어갈 위치(드래그 중인 요소를 뺀 형제들 기준 번호).
     * 마우스가 형제 중심을 지난 만큼 뒤로 간다.
     */
    public static int flowInsertIndex(List<Rect> siblings, boolean horizontal, int mouseX, int mouseY) {
        int idx = 0;
        for (Rect r : siblings) {
            int center = horizontal ? r.centerX() : r.centerY();
            if ((horizontal ? mouseX : mouseY) > center) idx++;
        }
        return idx;
    }

    // ---- 스냅 ----

    /** 정렬 보조선. {@code vertical} 이면 x=pos 의 세로선, 아니면 y=pos 의 가로선. */
    public record Guide(boolean vertical, int pos) {}

    public record Snap(int dx, int dy, List<Guide> guides) {}

    /**
     * 움직이는 사각형의 (왼쪽, 가운데, 오른쪽)/(위, 가운데, 아래)를 목표선에 달라붙게 하는 보정값.
     * 임계값 안에서 가장 가까운 선 하나씩(x, y 각각) 고른다.
     */
    public static Snap snap(Rect moving, List<Integer> xTargets, List<Integer> yTargets, int threshold) {
        List<Guide> guides = new ArrayList<>();
        int[] xs = {moving.x(), moving.centerX(), moving.right()};
        int[] ys = {moving.y(), moving.centerY(), moving.bottom()};
        int dx = 0, dy = 0;
        int bestX = threshold + 1, bestY = threshold + 1;
        Integer gx = null, gy = null;
        for (int t : xTargets) for (int a : xs) {
            int d = Math.abs(t - a);
            if (d < bestX) { bestX = d; dx = t - a; gx = t; }
        }
        for (int t : yTargets) for (int a : ys) {
            int d = Math.abs(t - a);
            if (d < bestY) { bestY = d; dy = t - a; gy = t; }
        }
        if (gx != null) guides.add(new Guide(true, gx));
        if (gy != null) guides.add(new Guide(false, gy));
        return new Snap(dx, dy, guides);
    }

    /** 다른 요소들과 부모의 가장자리/중심선(중복 제거, 정렬). */
    public static List<Integer> targets(List<Rect> others, Rect parent, boolean xAxis) {
        Set<Integer> t = new TreeSet<>();
        for (Rect r : others) {
            if (xAxis) { t.add(r.x()); t.add(r.centerX()); t.add(r.right()); }
            else { t.add(r.y()); t.add(r.centerY()); t.add(r.bottom()); }
        }
        if (parent != null) {
            if (xAxis) { t.add(parent.x()); t.add(parent.centerX()); t.add(parent.right()); }
            else { t.add(parent.y()); t.add(parent.centerY()); t.add(parent.bottom()); }
        }
        return new ArrayList<>(t);
    }

    public static int toGrid(int v, int grid) { return grid <= 1 ? v : Math.round(v / (float) grid) * grid; }

    // ---- 범위 제한 ----

    /**
     * 이동한 사각형을 부모 영역 안으로 되돌린다(크기는 그대로). 부모보다 큰 요소는 시작 쪽에 맞춘다.
     */
    public static Rect clamp(Rect r, Rect bounds) {
        int x = r.w() >= bounds.w() ? bounds.x() : Math.max(bounds.x(), Math.min(r.x(), bounds.right() - r.w()));
        int y = r.h() >= bounds.h() ? bounds.y() : Math.max(bounds.y(), Math.min(r.y(), bounds.bottom() - r.h()));
        return new Rect(x, y, r.w(), r.h());
    }

    /**
     * 크기 조절한 사각형에서 <b>끌고 있는 변만</b> 부모 영역 안으로 되돌린다(끌지 않는 변은 건드리지 않는다).
     * 최소 크기 {@code min} 아래로는 줄어들지 않는다.
     */
    public static Rect clampEdges(Rect r, Rect bounds, int min, Handle h) {
        boolean mL = h == Handle.W || h == Handle.NW || h == Handle.SW;
        boolean mR = h == Handle.E || h == Handle.NE || h == Handle.SE;
        boolean mT = h == Handle.N || h == Handle.NW || h == Handle.NE;
        boolean mB = h == Handle.S || h == Handle.SW || h == Handle.SE;
        int l = r.x(), rt = r.right(), t = r.y(), b = r.bottom();
        if (mL) l = Math.max(l, bounds.x());
        if (mR) rt = Math.min(rt, bounds.right());
        if (mT) t = Math.max(t, bounds.y());
        if (mB) b = Math.min(b, bounds.bottom());
        int w = Math.max(min, rt - l), hh = Math.max(min, b - t);
        return new Rect(mL ? rt - w : l, mT ? b - hh : t, w, hh);
    }

    // ---- 기준점(anchor) 자동 추론 ----

    /** 기준점 문자열을 (세로, 가로) 각각 0=시작(top/left) 1=가운데 2=끝(bottom/right) 로 푼다. null 은 top-left. */
    static int[] anchorAxes(String anchor) {
        if (anchor == null) return new int[] {0, 0};
        String a = anchor.toLowerCase();
        int h = a.contains("left") ? 0 : a.contains("right") ? 2 : 1;
        int v = a.contains("top") ? 0 : a.contains("bottom") ? 2 : 1;
        return new int[] {v, h};
    }

    /** (세로, 가로) → 기준점 문자열. 예: (0,2) → "top-right", (1,1) → "center", (1,2) → "right". */
    static String composeAnchor(int v, int h) {
        String vp = v == 0 ? "top" : v == 2 ? "bottom" : "";
        String hp = h == 0 ? "left" : h == 2 ? "right" : "";
        if (vp.isEmpty() && hp.isEmpty()) return "center";
        if (vp.isEmpty()) return hp;
        if (hp.isEmpty()) return vp;
        return vp + "-" + hp;
    }

    /** 같은 뜻의 기준점을 하나의 표기로 맞춘다(비교용). null → "top-left". */
    public static String normalizeAnchor(String anchor) {
        int[] a = anchorAxes(anchor);
        return composeAnchor(a[0], a[1]);
    }

    /**
     * 요소가 놓인 자리로부터 기준점을 정한다. 요소를 부모의 오른쪽/아래쪽 끝(또는 가운데)에 붙여 두면 그 끝을 기준으로 삼아,
     * 창 크기가 바뀌어도 요소가 그 끝에 붙어 있게 한다.
     *
     * <ul>
     *   <li>축마다 따로 판단한다(가로: left/가운데/right, 세로: top/가운데/bottom).</li>
     *   <li><b>끈적하게</b>: 지금 기준점이 가리키는 선에서 {@code threshold + 4} 이내면 바꾸지 않는다(조금 움직였다고 기준점이 뒤바뀌지 않게).</li>
     *   <li>벗어났을 때만: 가운데({@code allowCenter} 일 때 3px 이내), 끝(threshold 이내이고 시작보다 가까움), 시작(threshold 이내)
     *       중 맞는 쪽으로 옮기고, 어디에도 가깝지 않으면 지금 기준점을 유지한다.</li>
     * </ul>
     */
    public static String inferAnchor(Rect r, Rect bounds, String current, int threshold, boolean allowCenter) {
        int[] cur = anchorAxes(current);
        int h = inferAxis(r.x(), r.right(), bounds.x(), bounds.right(), cur[1], threshold, allowCenter);
        int v = inferAxis(r.y(), r.bottom(), bounds.y(), bounds.bottom(), cur[0], threshold, allowCenter);
        return composeAnchor(v, h);
    }

    private static int inferAxis(int lo, int hi, int blo, int bhi, int cur, int threshold, boolean allowCenter) {
        int dStart = Math.abs(lo - blo), dEnd = Math.abs(bhi - hi);
        int dCenter = Math.abs((lo + hi) - (blo + bhi)) / 2;
        int keep = cur == 0 ? dStart : cur == 1 ? dCenter : dEnd;
        if (keep <= threshold + 4) return cur;
        if (allowCenter && dCenter <= 3) return 1;
        if (dEnd <= threshold && dEnd < dStart) return 2;
        if (dStart <= threshold) return 0;
        return cur;
    }
}
