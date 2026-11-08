package dev.magyul.betteruiux.core.style;

import dev.magyul.betteruiux.core.anim.Keyframes;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import dev.magyul.betteruiux.core.layout.StyleMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 스타일 캐스케이드. 낮은 우선순위 → 높은 우선순위:
 * <ol>
 *   <li>부모에게서 상속(color, shadow, text-align, {@code --변수})</li>
 *   <li>XML 속성 (HTML 의 presentational attribute 와 같은 위치)</li>
 *   <li>스타일시트 규칙 — {@code !important} 여부, 특이도, 선언 순서 순</li>
 *   <li>인라인 {@code style="..."}</li>
 * </ol>
 * 마지막에 {@code var(--x, 기본값)} 을 치환한다. :hover/:active 규칙은 {@link LayoutBox#overlays} 로 분리한다.
 */
public final class StyleResolver {
    private record Entry(Selector selector, List<Stylesheet.Declaration> declarations, int ordinal) {}

    private record Applied(Stylesheet.Declaration decl, int specificity, int ordinal) {}

    private static final Set<String> INHERITED = Set.of("color", "shadow", "text-align");
    private static final Map<String, String> ALIASES = Map.of("background-color", "background");
    private static final int MAX_VAR_DEPTH = 8;

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Keyframes> keyframes = new HashMap<>();

    public StyleResolver(List<Stylesheet> sheets) {
        int ordinal = 0;
        for (Stylesheet sheet : sheets) {
            keyframes.putAll(sheet.keyframes());   // 같은 이름이면 나중 시트가 이긴다
            for (Stylesheet.Rule r : sheet.rules()) entries.add(new Entry(r.selector(), r.declarations(), ordinal++));
        }
    }

    /** 이름으로 {@code @keyframes} 를 찾는다(없으면 null). */
    public Keyframes keyframes(String name) { return keyframes.get(name); }

    /** 부모가 먼저 해석돼 있어야 한다(위에서 아래로 호출). */
    public void resolve(LayoutBox box) {
        StyleMap base = new StyleMap();

        LayoutBox parent = box.parent;
        if (parent != null) {
            for (Map.Entry<String, String> e : parent.style.entries().entrySet()) {
                if (INHERITED.contains(e.getKey()) || e.getKey().startsWith("--")) base.put(e.getKey(), e.getValue());
            }
        }
        for (Map.Entry<String, String> e : box.node.attributes().entrySet()) {
            if (!e.getKey().equals("style")) put(base, e.getKey().toLowerCase(), e.getValue());
        }

        List<Applied> staticRules = new ArrayList<>();
        Map<String, List<Applied>> dynamicRules = new LinkedHashMap<>();
        for (Entry e : entries) {
            if (!e.selector().matches(box)) continue;
            Set<String> dyn = e.selector().dynamicPseudos();
            List<Applied> target = dyn.isEmpty() ? staticRules
                    : dynamicRules.computeIfAbsent(String.join("+", dyn), k -> new ArrayList<>());
            for (Stylesheet.Declaration d : e.declarations()) {
                target.add(new Applied(d, e.selector().specificity(), e.ordinal()));
            }
        }
        apply(base, staticRules);

        String inline = box.node.attr("style");
        if (inline != null) {
            for (Stylesheet.Declaration d : Stylesheet.parseDeclarations(inline)) put(base, d.prop(), d.value());
        }
        substituteVars(base);
        box.style = base;

        Map<String, StyleMap> overlays = new HashMap<>();
        for (Map.Entry<String, List<Applied>> e : dynamicRules.entrySet()) {
            StyleMap overlay = new StyleMap();
            apply(overlay, e.getValue());
            substituteVars(overlay, base);
            overlays.put(e.getKey(), overlay);
        }
        box.overlays = overlays;
    }

    private static void apply(StyleMap target, List<Applied> list) {
        list.sort(Comparator
                .comparing((Applied a) -> a.decl().important())
                .thenComparingInt(Applied::specificity)
                .thenComparingInt(Applied::ordinal));
        for (Applied a : list) put(target, a.decl().prop(), a.decl().value());
    }

    private static void put(StyleMap m, String key, String value) {
        m.put(ALIASES.getOrDefault(key, key), value);
    }

    // ---- var() 치환 ----

    private static void substituteVars(StyleMap m) { substituteVars(m, m); }

    /** @param customSource {@code --변수} 를 찾을 맵(오버레이는 기준 스타일의 변수를 쓴다) */
    private static void substituteVars(StyleMap m, StyleMap customSource) {
        // 변수 값끼리의 참조를 먼저 풀어 둔다.
        Map<String, String> custom = new HashMap<>();
        for (Map.Entry<String, String> e : customSource.entries().entrySet()) {
            if (e.getKey().startsWith("--")) custom.put(e.getKey(), e.getValue());
        }
        for (int pass = 0; pass < MAX_VAR_DEPTH; pass++) {
            boolean changed = false;
            for (Map.Entry<String, String> e : custom.entrySet()) {
                String r = replaceVars(e.getValue(), custom);
                if (!r.equals(e.getValue())) {
                    e.setValue(r);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        for (Map.Entry<String, String> e : new ArrayList<>(m.entries().entrySet())) {
            String v = e.getValue();
            if (!v.contains("var(")) continue;
            m.put(e.getKey(), e.getKey().startsWith("--") ? custom.getOrDefault(e.getKey(), replaceVars(v, custom))
                    : replaceVars(v, custom));
        }
    }

    /** {@code var(--name)} / {@code var(--name, fallback)} 를 한 단계 치환한다. 정의가 없고 fallback 도 없으면 그대로 둔다. */
    static String replaceVars(String value, Map<String, String> custom) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            int at = value.indexOf("var(", i);
            if (at < 0) {
                out.append(value, i, value.length());
                break;
            }
            out.append(value, i, at);
            int depth = 0, end = -1;
            for (int k = at + 3; k < value.length(); k++) {
                char c = value.charAt(k);
                if (c == '(') depth++;
                else if (c == ')' && --depth == 0) {
                    end = k;
                    break;
                }
            }
            if (end < 0) {
                out.append(value, at, value.length());
                break;
            }
            String inner = value.substring(at + 4, end);
            String name = inner, fallback = null;
            int comma = topLevelComma(inner);
            if (comma >= 0) {
                name = inner.substring(0, comma);
                fallback = inner.substring(comma + 1).trim();
            }
            name = name.trim();
            String resolved = custom.get(name);
            if (resolved != null) out.append(resolved);
            else if (fallback != null) out.append(fallback);
            else out.append(value, at, end + 1);
            i = end + 1;
        }
        return out.toString();
    }

    private static int topLevelComma(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) return i;
        }
        return -1;
    }
}
