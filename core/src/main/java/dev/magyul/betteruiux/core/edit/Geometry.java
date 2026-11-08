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
            if (ox != curX) out.put("x", ox == 0 ? null : Integer.toString(ox));
            if (oy != curY) out.put("y", oy == 0 ? null : Integer.toString(oy));
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
}
