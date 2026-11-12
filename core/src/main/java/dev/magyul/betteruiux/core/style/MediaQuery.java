package dev.magyul.betteruiux.core.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code @media} 조건 목록(쉼표 = OR). 뷰포트는 GUI 픽셀 크기다.
 * 지원: 미디어 유형 {@code all screen}(print/speech 는 항상 거짓), {@code not}/{@code only}, {@code and},
 * 특성 {@code width height aspect-ratio orientation} 과 {@code min-/max-} 접두, 범위 문법 {@code (width >= 300px)}.
 * 모르는 특성은 거짓으로 평가한다(CSS 규정).
 */
public final class MediaQuery {
    private record Feature(String name, String op, String value) {
        boolean eval(int w, int h) {
            String n = name;
            String o = op;
            if (n.startsWith("min-")) { n = n.substring(4); o = ">="; }
            else if (n.startsWith("max-")) { n = n.substring(4); o = "<="; }
            switch (n) {
                case "width": return cmp(w, length(value, w, h), o);
                case "height": return cmp(h, length(value, w, h), o);
                case "aspect-ratio": return cmp((double) w / Math.max(1, h), ratio(value), o);
                case "orientation":
                    return value.equals(w >= h ? "landscape" : "portrait");
                default: throw new IllegalStateException("unknown feature");
            }
        }
    }

    private record Alt(boolean negate, boolean typeOk, List<Feature> features, boolean knownFeatures) {
        boolean eval(int w, int h) {
            boolean r = typeOk && knownFeatures;
            if (r) {
                for (Feature f : features) {
                    try {
                        if (!f.eval(w, h)) { r = false; break; }
                    } catch (RuntimeException e) {
                        r = false;
                        break;
                    }
                }
            }
            return negate != r;
        }
    }

    private final List<Alt> alts;
    private final String source;

    private MediaQuery(List<Alt> alts, String source) {
        this.alts = alts;
        this.source = source;
    }

    @Override public String toString() { return source; }

    public boolean matches(int viewW, int viewH) {
        for (Alt a : alts) if (a.eval(viewW, viewH)) return true;
        return false;
    }

    public static MediaQuery parse(String text) {
        String src = text.trim();
        if (src.isEmpty()) return new MediaQuery(List.of(new Alt(false, true, List.of(), true)), "all");
        List<Alt> alts = new ArrayList<>();
        for (String one : Selector.splitList(src)) alts.add(parseAlt(one, src));
        return new MediaQuery(alts, src);
    }

    private static Alt parseAlt(String one, String whole) {
        List<String> t = CssMath.tokens(one.trim());
        if (t.isEmpty()) throw new IllegalArgumentException("빈 미디어 쿼리: " + whole);
        int i = 0;
        boolean negate = false;
        String first = t.get(0).toLowerCase(Locale.ROOT);
        if (first.equals("not")) { negate = true; i++; }
        else if (first.equals("only")) i++;
        boolean typeOk = true;
        List<Feature> features = new ArrayList<>();
        boolean known = true;
        boolean expectCond = false;
        for (; i < t.size(); i++) {
            String tok = t.get(i);
            String low = tok.toLowerCase(Locale.ROOT);
            if (low.equals("and")) { expectCond = true; continue; }
            if (low.startsWith("(")) {
                Feature f = parseFeature(tok.substring(1, tok.length() - (tok.endsWith(")") ? 1 : 0)).trim(), whole);
                if (f == null) known = false;
                else features.add(f);
                expectCond = false;
            } else if (i == (negate || first.equals("only") ? 1 : 0)) {
                typeOk = low.equals("all") || low.equals("screen");
            } else {
                throw new IllegalArgumentException("잘못된 미디어 쿼리: " + whole);
            }
        }
        if (expectCond) throw new IllegalArgumentException("'and' 뒤에 조건이 없습니다: " + whole);
        return new Alt(negate, typeOk, features, known);
    }

    /** {@code min-width: 300px} / {@code width >= 300px} / {@code orientation: landscape}. 모르는 특성이면 null. */
    private static Feature parseFeature(String body, String whole) {
        String name, op = "=", value;
        int colon = body.indexOf(':');
        if (colon > 0) {
            name = body.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            value = body.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
        } else {
            String[] ops = {">=", "<=", ">", "<", "="};
            int at = -1;
            String found = null;
            for (String o : ops) {
                int k = body.indexOf(o);
                if (k > 0) { at = k; found = o; break; }
            }
            if (found == null) {
                String bare = body.toLowerCase(Locale.ROOT);
                if (bare.equals("width") || bare.equals("height")) return new Feature(bare, ">", "0");
                return null;
            }
            name = body.substring(0, at).trim().toLowerCase(Locale.ROOT);
            op = found;
            value = body.substring(at + found.length()).trim().toLowerCase(Locale.ROOT);
        }
        String base = name.startsWith("min-") || name.startsWith("max-") ? name.substring(4) : name;
        if (!(base.equals("width") || base.equals("height") || base.equals("aspect-ratio") || base.equals("orientation"))) {
            return null;
        }
        return new Feature(name, op, value);
    }

    private static boolean cmp(double actual, double target, String op) {
        return switch (op) {
            case ">=" -> actual >= target;
            case "<=" -> actual <= target;
            case ">" -> actual > target;
            case "<" -> actual < target;
            default -> Math.abs(actual - target) < 1e-9;
        };
    }

    private static double length(String v, int w, int h) {
        String r = CssMath.resolve(v.trim(), new CssMath.Env(w, h));
        if (r.endsWith("px")) r = r.substring(0, r.length() - 2);
        return Double.parseDouble(r.trim());
    }

    private static double ratio(String v) {
        int slash = v.indexOf('/');
        if (slash < 0) return Double.parseDouble(v.trim());
        return Double.parseDouble(v.substring(0, slash).trim()) / Double.parseDouble(v.substring(slash + 1).trim());
    }
}
