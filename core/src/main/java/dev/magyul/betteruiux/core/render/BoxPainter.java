package dev.magyul.betteruiux.core.render;

import dev.magyul.betteruiux.api.UiGfx;
import dev.magyul.betteruiux.core.layout.Colors;
import dev.magyul.betteruiux.core.layout.Insets;
import dev.magyul.betteruiux.core.style.CssMath;
import dev.magyul.betteruiux.core.style.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 상자 장식(둥근 모서리, 그라디언트, 그림자, 테두리, 아웃라인)을 {@link UiGfx} 의 기본 명령(사각형, 세로 그라데이션)만으로 그린다.
 * 백엔드 API 를 늘리지 않으므로 어느 렌더러에서도 같게 보이고 {@code DrawList} 골든으로 검증할 수 있다.
 * 둥근 모서리는 행(row) 단위 사각형으로 근사하며 안티앨리어싱은 없다(픽셀 GUI 에 맞춘 선택).
 */
final class BoxPainter {
    private BoxPainter() {}

    /** 모서리 반지름(px): 좌상, 우상, 우하, 좌하. */
    record Radii(int tl, int tr, int br, int bl) {
        static final Radii NONE = new Radii(0, 0, 0, 0);

        boolean none() { return tl <= 0 && tr <= 0 && br <= 0 && bl <= 0; }

        Radii grow(int d) {
            return new Radii(tl > 0 ? tl + d : 0, tr > 0 ? tr + d : 0, br > 0 ? br + d : 0, bl > 0 ? bl + d : 0);
        }

        Radii shrink(int top, int right, int bottom, int left) {
            return new Radii(Math.max(0, tl - Math.max(top, left)), Math.max(0, tr - Math.max(top, right)),
                    Math.max(0, br - Math.max(bottom, right)), Math.max(0, bl - Math.max(bottom, left)));
        }

        /** 인접한 두 반지름의 합이 변보다 길면 비율로 줄인다(CSS 규칙). */
        Radii fit(int w, int h) {
            double f = 1;
            f = Math.min(f, ratio(w, tl + tr));
            f = Math.min(f, ratio(w, bl + br));
            f = Math.min(f, ratio(h, tl + bl));
            f = Math.min(f, ratio(h, tr + br));
            if (f >= 1) return this;
            return new Radii((int) Math.floor(tl * f), (int) Math.floor(tr * f), (int) Math.floor(br * f), (int) Math.floor(bl * f));
        }

        Radii transposed() { return new Radii(tl, bl, br, tr); }

        private static double ratio(int len, int sum) { return sum <= 0 ? 1 : (double) len / sum; }
    }

    // ---- 기본 도형 ----

    static void rect(UiGfx g, int x, int y, int w, int h, int argb) {
        if ((argb >>> 24) == 0 || w <= 0 || h <= 0) return;
        g.fillRect(x, y, w, h, argb);
    }

    /** 행별 왼쪽/오른쪽으로 파인 폭 {left[], right[]}. */
    static int[][] spans(int w, int h, Radii r) {
        int[] left = new int[Math.max(0, h)], right = new int[Math.max(0, h)];
        if (h <= 0 || w <= 0) return new int[][] {left, right};
        Radii f = r.fit(w, h);
        corner(left, f.tl(), 0, false);
        corner(right, f.tr(), 0, false);
        corner(right, f.br(), h, true);
        corner(left, f.bl(), h, true);
        return new int[][] {left, right};
    }

    private static void corner(int[] inset, int rad, int h, boolean bottom) {
        for (int i = 0; i < rad; i++) {
            double dy = rad - i - 0.5;
            int v = (int) Math.ceil(rad - Math.sqrt(Math.max(0, rad * (double) rad - dy * dy)));
            int row = bottom ? h - 1 - i : i;
            if (row >= 0 && row < inset.length) inset[row] = Math.max(inset[row], v);
        }
    }

    static void fillShape(UiGfx g, int x, int y, int w, int h, Radii r, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        if (r.none()) {
            g.fillRect(x, y, w, h, argb);
            return;
        }
        int[][] sp = spans(w, h, r);
        int start = 0;
        for (int i = 1; i <= h; i++) {
            if (i == h || sp[0][i] != sp[0][start] || sp[1][i] != sp[1][start]) {
                rect(g, x + sp[0][start], y + start, w - sp[0][start] - sp[1][start], i - start, argb);
                start = i;
            }
        }
    }

    // ---- 테두리 ----

    /**
     * 테두리 고리. 둥글지 않으면 변마다 사각형(위, 아래, 왼쪽, 오른쪽 순). 둥글면 바깥 도형에서 안쪽 도형을 뺀 모양을 행 단위로.
     * @param colors 위/오른쪽/아래/왼쪽 색(둥근 경우는 위쪽 색 하나만 쓴다)
     * @param style  solid / dashed / dotted (둥근 경우 solid 로 그린다)
     */
    static void ring(UiGfx g, int x, int y, int w, int h, Radii r, Insets bw, int[] colors, String style) {
        int t = bw.top(), rt = bw.right(), b = bw.bottom(), l = bw.left();
        if (t <= 0 && rt <= 0 && b <= 0 && l <= 0 || w <= 0 || h <= 0) return;
        if (r.none()) {
            side(g, x, y, w, t, colors[0], style, true);
            side(g, x, y + h - b, w, b, colors[2], style, true);
            side(g, x, y + t, l, h - t - b, colors[3], style, false);
            side(g, x + w - rt, y + t, rt, h - t - b, colors[1], style, false);
            return;
        }
        int color = colors[0];
        if ((color >>> 24) == 0) return;
        Radii outer = r.fit(w, h);
        int iw = w - l - rt, ih = h - t - b;
        int[][] os = spans(w, h, outer);
        int[][] is = iw > 0 && ih > 0 ? spans(iw, ih, outer.shrink(t, rt, b, l).fit(iw, ih)) : null;
        int start = 0;
        Sig cur = sig(os, is, 0, t, l, rt, ih);
        for (int i = 1; i <= h; i++) {
            Sig next = i == h ? null : sig(os, is, i, t, l, rt, ih);
            if (next == null || !next.equals(cur)) {
                int run = i - start;
                if (cur.iL() < 0) {
                    rect(g, x + cur.oL(), y + start, w - cur.oL() - cur.oR(), run, color);
                } else {
                    rect(g, x + cur.oL(), y + start, cur.iL() - cur.oL(), run, color);
                    rect(g, x + w - cur.iR(), y + start, cur.iR() - cur.oR(), run, color);
                }
                start = i;
                cur = next;
            }
        }
    }

    /** 행 하나의 고리 모양: 바깥 왼/오른쪽 여백, 안쪽 도형의 왼쪽 위치(-1 이면 이 행은 통째로 테두리)와 오른쪽 여백. */
    private record Sig(int oL, int oR, int iL, int iR) {}

    private static Sig sig(int[][] os, int[][] is, int i, int t, int l, int rt, int ih) {
        int row = i - t;
        if (is == null || row < 0 || row >= ih) return new Sig(os[0][i], os[1][i], -1, 0);
        return new Sig(os[0][i], os[1][i], l + is[0][row], rt + is[1][row]);
    }

    private static void side(UiGfx g, int x, int y, int w, int h, int color, String style, boolean horizontal) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        if (style.equals("dashed") || style.equals("dotted")) {
            int thick = horizontal ? h : w;
            int dash = style.equals("dashed") ? Math.max(2, thick * 3) : Math.max(1, thick);
            int gap = style.equals("dashed") ? Math.max(1, thick * 2) : Math.max(1, thick);
            int len = horizontal ? w : h;
            for (int p = 0; p < len; p += dash + gap) {
                int seg = Math.min(dash, len - p);
                if (horizontal) g.fillRect(x + p, y, seg, h, color);
                else g.fillRect(x, y + p, w, seg, color);
            }
            return;
        }
        g.fillRect(x, y, w, h, color);
    }

    // ---- 그라디언트 ----

    /** @param vertical 세로(위→아래)인지, @param colors/pos 정지점(pos 는 0..1, 오름차순) */
    record Gradient(boolean vertical, int[] colors, float[] pos, boolean radial) {
        Gradient(boolean vertical, int[] colors, float[] pos) { this(vertical, colors, pos, false); }

        int at(float t) {
            if (t <= pos[0]) return colors[0];
            for (int i = 1; i < pos.length; i++) {
                if (t <= pos[i]) {
                    float span = pos[i] - pos[i - 1];
                    return span <= 0 ? colors[i] : lerp(colors[i - 1], colors[i], (t - pos[i - 1]) / span);
                }
            }
            return colors[colors.length - 1];
        }
    }

    static int lerp(int a, int b, float t) {
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
            out |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }

    static boolean isGradient(String v) {
        if (v == null) return false;
        String low = v.trim().toLowerCase(Locale.ROOT);
        return low.startsWith("linear-gradient(") || low.startsWith("repeating-linear-gradient(")
                || low.startsWith("radial-gradient(") || low.startsWith("conic-gradient(");
    }

    /**
     * {@code linear-gradient([방향,] 색 [위치], ...)}. 방향은 {@code to top|bottom|left|right}, {@code Ndeg}
     * (가장 가까운 축으로 근사). radial/conic 은 첫 색 단색으로 대체한다. 해석 불가면 null.
     */
    static Gradient parseGradient(String v) {
        if (v == null) return null;
        String s = v.trim();
        String low = s.toLowerCase(Locale.ROOT);
        int open = s.indexOf('(');
        if (open < 0 || !s.endsWith(")")) return null;
        boolean linear = low.startsWith("linear-gradient(") || low.startsWith("repeating-linear-gradient(");
        boolean radial = low.startsWith("radial-gradient(");
        List<String> args = Selector.splitList(s.substring(open + 1, s.length() - 1));
        if (args.isEmpty()) return null;
        boolean vertical = true, reversed = false;
        int first = 0;
        if (radial) {                                       // 모양/크기/위치(circle, ellipse, at center ...)는 읽고 건너뛰며, 가운데 기준 타원(farthest-corner)만 쓴다
            List<String> t0 = CssMath.tokens(args.get(0));
            if (!t0.isEmpty() && Colors.tryParse(t0.get(0)) == null) first = 1;
        }
        if (linear) {
            String a = args.get(0).trim().toLowerCase(Locale.ROOT);
            if (a.startsWith("to ")) {
                first = 1;
                boolean top = a.contains("top"), bottom = a.contains("bottom"), left = a.contains("left"), right = a.contains("right");
                if (top || bottom) { vertical = true; reversed = top && !bottom; }
                else if (left || right) { vertical = false; reversed = left; }
            } else if (a.endsWith("deg") || a.endsWith("turn") || a.endsWith("rad") || a.endsWith("grad")) {
                first = 1;
                try {
                    double deg = angle(a);
                    deg = ((deg % 360) + 360) % 360;
                    if (deg < 45 || deg >= 315) { vertical = true; reversed = true; }
                    else if (deg < 135) { vertical = false; reversed = false; }
                    else if (deg < 225) { vertical = true; reversed = false; }
                    else { vertical = false; reversed = true; }
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        List<Integer> cols = new ArrayList<>();
        List<Float> pos = new ArrayList<>();
        for (int i = first; i < args.size(); i++) {
            List<String> t = CssMath.tokens(args.get(i));
            if (t.isEmpty()) return null;
            Float p = null;
            String last = t.get(t.size() - 1);
            if (t.size() > 1 && last.endsWith("%")) {
                try {
                    p = Float.parseFloat(last.substring(0, last.length() - 1)) / 100f;
                    t = t.subList(0, t.size() - 1);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            Integer c = Colors.tryParse(String.join(" ", t));
            if (c == null) return null;
            cols.add(c);
            pos.add(p);
        }
        if (cols.isEmpty()) return null;
        if (!linear && !radial) {                           // conic: 첫 색 단색으로 근사
            return new Gradient(true, new int[] {cols.get(0), cols.get(0)}, new float[] {0, 1});
        }
        if (cols.size() == 1) return new Gradient(vertical, new int[] {cols.get(0), cols.get(0)}, new float[] {0, 1}, radial);
        int n = cols.size();
        float[] p = new float[n];
        for (int i = 0; i < n; i++) p[i] = pos.get(i) == null ? Float.NaN : pos.get(i);
        if (Float.isNaN(p[0])) p[0] = 0;
        if (Float.isNaN(p[n - 1])) p[n - 1] = 1;
        for (int i = 1; i < n - 1; i++) {                   // 위치가 없는 정지점은 이웃 사이를 균등 분할
            if (!Float.isNaN(p[i])) continue;
            int j = i;
            while (Float.isNaN(p[j])) j++;
            float a = p[i - 1], b = p[j];
            for (int k = i; k < j; k++) p[k] = a + (b - a) * (k - i + 1) / (j - i + 1);
        }
        for (int i = 1; i < n; i++) p[i] = Math.max(p[i], p[i - 1]);
        int[] c = new int[n];
        for (int i = 0; i < n; i++) c[i] = cols.get(i);
        if (reversed) {                                     // 방향이 반대(top/left)면 정지점을 뒤집는다
            int[] rc = new int[n];
            float[] rp = new float[n];
            for (int i = 0; i < n; i++) {
                rc[i] = c[n - 1 - i];
                rp[i] = 1 - p[n - 1 - i];
            }
            c = rc;
            p = rp;
        }
        return new Gradient(vertical, c, p, radial);
    }

    private static double angle(String a) {
        if (a.endsWith("deg")) return Double.parseDouble(a.substring(0, a.length() - 3));
        if (a.endsWith("grad")) return Double.parseDouble(a.substring(0, a.length() - 4)) * 0.9;
        if (a.endsWith("rad")) return Math.toDegrees(Double.parseDouble(a.substring(0, a.length() - 3)));
        return Double.parseDouble(a.substring(0, a.length() - 4)) * 360;
    }

    static int scaleAlpha(int argb, float opacity) {
        if (opacity >= 1f) return argb;
        int a = Math.round((argb >>> 24) * opacity);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    static void gradient(UiGfx g, int x, int y, int w, int h, Radii r, Gradient gr, float opacity) {
        if (w <= 0 || h <= 0) return;
        int[] cs = new int[gr.colors().length];
        for (int i = 0; i < cs.length; i++) cs[i] = scaleAlpha(gr.colors()[i], opacity);
        Gradient o = new Gradient(gr.vertical(), cs, gr.pos(), gr.radial());
        if (o.radial()) {
            radialFill(g, x, y, w, h, r, o);
            return;
        }
        if (r.none()) {
            if (o.vertical()) {
                for (int i = 1; i < o.pos().length; i++) {
                    int y0 = y + Math.round(o.pos()[i - 1] * h), y1 = y + Math.round(o.pos()[i] * h);
                    if (i == 1 && o.pos()[0] > 0) rect(g, x, y, w, y0 - y, o.colors()[0]);
                    if (y1 > y0) g.fillGradient(x, y0, w, y1 - y0, o.colors()[i - 1], o.colors()[i]);
                    if (i == o.pos().length - 1 && y1 < y + h) rect(g, x, y1, w, y + h - y1, o.colors()[i]);
                }
            } else {
                strips(g, x, y, w, h, o, null);
            }
            return;
        }
        if (o.vertical()) {
            // 모서리가 깎이지 않은 가운데 구간(모든 행의 여백이 0)은 행마다 그리지 않고 fillGradient 로(색 멈춤 지점마다 끊어서) 그린다.
            // 키 큰 상자에서 그리기 명령 수가 높이에 비례해 늘어나는 것을 막는다.
            int[][] sp = spans(w, h, r);
            int a = 0;
            while (a < h && (sp[0][a] != 0 || sp[1][a] != 0)) a++;
            int b = h;
            while (b > a && (sp[0][b - 1] != 0 || sp[1][b - 1] != 0)) b--;
            if (b - a < 4) {
                solidRows(g, x, y, w, h, sp, o, 0, h);
            } else {
                solidRows(g, x, y, w, h, sp, o, 0, a);
                float[] pos = o.pos();
                int y0 = a;
                for (int k = 0; k <= pos.length; k++) {
                    int y1 = k < pos.length ? Math.max(a, Math.min(b, Math.round(pos[k] * h))) : b;
                    if (y1 > y0) {
                        g.fillGradient(x, y + y0, w, y1 - y0, o.at((float) y0 / h), o.at((float) y1 / h));
                        y0 = y1;
                    }
                }
                solidRows(g, x, y, w, h, sp, o, b, h);
            }
        } else {
            strips(g, x, y, w, h, o, spans(h, w, r.transposed()));
        }
    }

    /**
     * 방사형 그라디언트: 가운데 기준 타원(farthest-corner). 같은 가운데를 쓰는 타원 띠를 바깥에서 안쪽으로 겹쳐 칠한다(최대 6띠).
     * 명령 수가 띠 수 x 높이에 비례하므로 큰 상자에 많이 쓰면 비싸다.
     */
    private static void radialFill(UiGfx g, int x, int y, int w, int h, Radii r, Gradient o) {
        int bands = Math.max(3, Math.min(6, Math.min(w, h) / 4));
        int[][] sp = spans(w, h, r);
        double cx = w / 2.0, cy = h / 2.0, rx = cx * Math.sqrt(2), ry = cy * Math.sqrt(2);
        fillShape(g, x, y, w, h, r, o.at(1f - 0.5f / bands));
        for (int k = bands - 2; k >= 0; k--) {
            double f = (k + 1) / (double) bands, erx = rx * f, ery = ry * f;
            int color = o.at((float) (f - 0.5 / bands));
            int start = -1, sl = 0, sr = 0;
            for (int row = 0; row <= h; row++) {
                int l = 0, rr = -1;
                if (row < h) {
                    double dy = row + 0.5 - cy;
                    if (Math.abs(dy) < ery) {
                        double hw = erx * Math.sqrt(1 - (dy / ery) * (dy / ery));
                        l = Math.max(sp[0][row], (int) Math.round(cx - hw));
                        rr = Math.min(w - sp[1][row], (int) Math.round(cx + hw));
                    }
                }
                boolean has = rr > l;
                if (start >= 0 && (!has || l != sl || rr != sr)) {      // 같은 폭이 이어진 행을 묶어서 그린다
                    rect(g, x + sl, y + start, sr - sl, row - start, color);
                    start = -1;
                }
                if (has && start < 0) {
                    start = row;
                    sl = l;
                    sr = rr;
                }
            }
        }
    }

    /**
     * 둥근 상자 안쪽에 자식을 잘라 낼 때 쓰는 사각형 클립 목록 {x,y,w,h}: 같은 모양이 이어지는 행 묶음마다 하나.
     * 묶음이 {@link #MAX_CLIP_STRIPS} 보다 많으면(반지름이 큼) 인접한 묶음을 둘씩 합쳐 줄인다. 합칠 때는 두 사각형의 교집합을 써서
     * 둥근 모양 밖으로 자식이 새지 않게 하고(대신 모서리가 조금 더 각지게 잘린다) 잃는 면적이 가장 작은 쌍부터 합친다.
     */
    static List<int[]> clipStrips(int x, int y, int w, int h, Radii r) {
        int[][] sp = spans(w, h, r);
        List<int[]> out = new ArrayList<>();
        int start = 0;
        for (int i = 1; i <= h; i++) {
            if (i == h || sp[0][i] != sp[0][start] || sp[1][i] != sp[1][start]) {
                int cw = w - sp[0][start] - sp[1][start];
                if (cw > 0) out.add(new int[] {x + sp[0][start], y + start, cw, i - start});
                start = i;
            }
        }
        while (out.size() > MAX_CLIP_STRIPS) {
            int best = 0;
            long bestLoss = Long.MAX_VALUE;
            for (int i = 0; i + 1 < out.size(); i++) {
                int[] a = out.get(i), b = out.get(i + 1);
                int left = Math.max(a[0], b[0]), right = Math.min(a[0] + a[2], b[0] + b[2]);
                long loss = (long) (a[2] + b[2] - 2 * (right - left)) * 0 + (long) a[3] * (a[2] - (right - left)) + (long) b[3] * (b[2] - (right - left));
                if (loss < bestLoss) {
                    bestLoss = loss;
                    best = i;
                }
            }
            int[] a = out.get(best), b = out.get(best + 1);
            int left = Math.max(a[0], b[0]), right = Math.min(a[0] + a[2], b[0] + b[2]);
            out.set(best, new int[] {left, a[1], right - left, a[3] + b[3]});
            out.remove(best + 1);
        }
        return out;
    }

    /** 둥근 클립에서 자식을 다시 그리는 횟수의 상한. */
    static final int MAX_CLIP_STRIPS = 12;

    /** 행 [from, to) 를 행마다(같은 모양·색이 이어지면 묶어서) 단색 사각형으로 그린다. */
    private static void solidRows(UiGfx g, int x, int y, int w, int h, int[][] sp, Gradient o, int from, int to) {
        if (from >= to) return;
        int start = from, startColor = o.at((from + 0.5f) / h);
        for (int i = from + 1; i <= to; i++) {
            int c = i == to ? 0 : o.at((i + 0.5f) / h);
            if (i == to || sp[0][i] != sp[0][start] || sp[1][i] != sp[1][start] || c != startColor) {
                rect(g, x + sp[0][start], y + start, w - sp[0][start] - sp[1][start], i - start, startColor);
                start = i;
                startColor = c;
            }
        }
    }

    /** 가로 그라디언트: 열(최대 ~64 조각) 단위. {@code vInset} 이 있으면 열마다 위/아래를 둥글게 깎는다. */
    private static void strips(UiGfx g, int x, int y, int w, int h, Gradient o, int[][] vInset) {
        int step = Math.max(1, (int) Math.ceil(w / 64.0));
        int start = 0;
        int startColor = o.at(Math.min(step, w) / 2f / w);
        int startTop = vInset == null ? 0 : vInset[0][0], startBot = vInset == null ? 0 : vInset[1][0];
        for (int i = step; i <= w + step - 1; i += step) {
            int end = Math.min(i, w);
            boolean last = end >= w;
            int nextColor = last ? 0 : o.at((end + Math.min(step, w - end) / 2f) / w);
            int nt = vInset == null || last ? 0 : vInset[0][end], nb = vInset == null || last ? 0 : vInset[1][end];
            if (last || nextColor != startColor || nt != startTop || nb != startBot) {
                rect(g, x + start, y + startTop, end - start, h - startTop - startBot, startColor);
                start = end;
                startColor = nextColor;
                startTop = nt;
                startBot = nb;
            }
            if (last) break;
        }
    }

    // ---- 그림자 ----

    record Shadow(int ox, int oy, int blur, int spread, int color, boolean inset) {
        Shadow(int ox, int oy, int blur, int spread, int color) { this(ox, oy, blur, spread, color, false); }
    }

    /** {@code box-shadow: ox oy [blur [spread]] color [, ...]} (inset 은 무시). */
    static List<Shadow> parseShadows(String v) {
        List<Shadow> out = new ArrayList<>();
        if (v == null || v.isBlank() || v.trim().equalsIgnoreCase("none")) return out;
        for (String one : Selector.splitList(v)) {
            List<String> t = CssMath.tokens(one);
            boolean inset = false;
            Integer color = null;
            List<Integer> nums = new ArrayList<>();
            boolean ok = true;
            for (String tok : t) {
                String low = tok.toLowerCase(Locale.ROOT);
                if (low.equals("inset")) { inset = true; continue; }
                Integer c = Colors.tryParse(tok);
                if (c != null && !isNumber(tok)) { color = c; continue; }
                try {
                    nums.add(Math.round(Float.parseFloat(low.endsWith("px") ? low.substring(0, low.length() - 2) : low)));
                } catch (NumberFormatException e) {
                    ok = false;
                }
            }
            if (!ok || nums.size() < 2) continue;
            out.add(new Shadow(nums.get(0), nums.get(1), nums.size() > 2 ? Math.max(0, nums.get(2)) : 0,
                    nums.size() > 3 ? nums.get(3) : 0, color == null ? 0x80000000 : color, inset));
        }
        return out;
    }

    private static boolean isNumber(String s) {
        try {
            Float.parseFloat(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 박스 뒤에 그림자를 그린다(바깥 그림자). 번짐은 겹친 반투명 층으로 근사하고, 상자 안쪽은 비우지 않는다. */
    static void shadows(UiGfx g, int x, int y, int w, int h, Radii r, List<Shadow> list, float opacity) {
        for (int k = list.size() - 1; k >= 0; k--) {          // 먼저 쓴 그림자가 위에 오도록 뒤에서부터
            Shadow s = list.get(k);
            if (s.inset()) continue;
            int sx = x + s.ox() - s.spread(), sy = y + s.oy() - s.spread();
            int sw = w + 2 * s.spread(), sh = h + 2 * s.spread();
            Radii base = r.grow(s.spread());
            int color = scaleAlpha(s.color(), opacity);
            int alpha = color >>> 24;
            if (alpha == 0) continue;
            if (s.blur() <= 0) {
                fillShape(g, sx, sy, sw, sh, base, color);
                continue;
            }
            int n = Math.max(1, Math.min((s.blur() + 1) / 2, 6));      // 층 수: 번짐 2px 당 한 층(최대 6) — 명령 수를 줄이려고 번짐 1px 당 한 층이던 것을 줄였다
            double prev = 0;
            for (int layer = 1; layer <= n + 1; layer++) {
                double t = layer / (double) (n + 1);
                double target = alpha / 255.0 * (t * t * (3 - 2 * t));      // 누적 불투명도(smoothstep)
                double a = 1 - (1 - target) / (1 - prev);
                prev = target;
                int a8 = (int) Math.round(a * 255);
                if (a8 <= 0) continue;
                int e = (int) Math.round(s.blur() * (n + 1 - layer) / (double) n);   // 바깥 → 안쪽으로 줄어드는 확장량
                if (layer == n + 1) e = 0;
                Radii rr = base.grow(e);
                rr = new Radii(Math.min(rr.tl(), 3), Math.min(rr.tr(), 3), Math.min(rr.br(), 3), Math.min(rr.bl(), 3));   // 번진 층은 모서리를 거칠게(명령 수 절약)
                fillShape(g, sx - e, sy - e, sw + 2 * e, sh + 2 * e, rr, (a8 << 24) | (color & 0xFFFFFF));
            }
        }
    }

    /**
     * 안쪽 그림자({@code inset}): 배경 위, 테두리 아래에 상자 가장자리를 따라 그린다. 안쪽으로 번지는 층(겹친 고리)으로 근사하고,
     * 오프셋은 변마다 두께를 다르게 해서 반영한다(예: {@code 0 2 ...} 이면 위쪽이 두꺼움).
     */
    static void insetShadows(UiGfx g, int x, int y, int w, int h, Radii r, List<Shadow> list, float opacity) {
        for (int k = list.size() - 1; k >= 0; k--) {
            Shadow s = list.get(k);
            if (!s.inset()) continue;
            int color = scaleAlpha(s.color(), opacity), alpha = color >>> 24;
            if (alpha == 0) continue;
            int n = s.blur() <= 0 ? 0 : Math.max(1, Math.min((s.blur() + 1) / 2, 6));
            double prev = 0;
            for (int layer = 1; layer <= n + 1; layer++) {                 // 가장 두꺼운(옅은) 층부터 겹쳐서 가장자리일수록 진해진다
                double t = layer / (double) (n + 1);
                double target = alpha / 255.0 * (t * t * (3 - 2 * t));
                double a = 1 - (1 - target) / (1 - prev);
                prev = target;
                int a8 = (int) Math.round(a * 255);
                if (a8 <= 0) continue;
                int e = layer == n + 1 ? 0 : (int) Math.round(s.blur() * (n + 1 - layer) / (double) n);
                int top = Math.max(0, s.spread() + s.oy() + e), bottom = Math.max(0, s.spread() - s.oy() + e);
                int left = Math.max(0, s.spread() + s.ox() + e), right = Math.max(0, s.spread() - s.ox() + e);
                if (top + bottom >= h || left + right >= w) continue;
                int c = (a8 << 24) | (color & 0xFFFFFF);
                ring(g, x, y, w, h, r, new Insets(top, right, bottom, left), new int[] {c, c, c, c}, "solid");
            }
        }
    }

    // ---- 스타일 읽기 ----

    /** {@code border-radius} (1~4값, px 또는 %) 와 모서리별 {@code border-*-radius}. 타원 반지름({@code /})은 앞 값만 쓴다. */
    static Radii radii(dev.magyul.betteruiux.core.layout.StyleMap s, int w, int h) {
        int[] r = new int[4];
        String v = s.get("border-radius");
        if (v != null) {
            int slash = v.indexOf('/');
            List<String> t = CssMath.tokens(slash >= 0 ? v.substring(0, slash) : v);
            if (!t.isEmpty() && t.size() <= 4) {
                int[] a = new int[t.size()];
                for (int i = 0; i < a.length; i++) a[i] = radius(t.get(i), w, h);
                switch (a.length) {
                    case 1 -> r = new int[] {a[0], a[0], a[0], a[0]};
                    case 2 -> r = new int[] {a[0], a[1], a[0], a[1]};
                    case 3 -> r = new int[] {a[0], a[1], a[2], a[1]};
                    default -> r = a;
                }
            }
        }
        String[] keys = {"border-top-left-radius", "border-top-right-radius", "border-bottom-right-radius", "border-bottom-left-radius"};
        for (int i = 0; i < 4; i++) {
            String k = s.get(keys[i]);
            if (k != null) {
                List<String> t = CssMath.tokens(k);
                if (!t.isEmpty()) r[i] = radius(t.get(0), w, h);
            }
        }
        return new Radii(r[0], r[1], r[2], r[3]);
    }

    private static int radius(String tok, int w, int h) {
        String t = tok.trim().toLowerCase(Locale.ROOT);
        try {
            if (t.endsWith("%")) return Math.max(0, Math.round(Float.parseFloat(t.substring(0, t.length() - 1)) / 100f * Math.min(w, h)));
            return Math.max(0, Math.round(Float.parseFloat(t.endsWith("px") ? t.substring(0, t.length() - 2) : t)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
