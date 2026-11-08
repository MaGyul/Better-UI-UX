package dev.magyul.betteruiux.core.layout;

import dev.magyul.betteruiux.core.xml.UiNode;
import java.util.HashMap;
import java.util.Map;

/**
 * 노드 하나의 스타일 값 모음. M1: XML 속성 + 인라인 {@code style="a: b; c: d"} (인라인이 우선).
 * M2에서 스타일시트 캐스케이드로 확장되며, 이 클래스의 조회 API는 유지된다.
 */
public final class StyleMap {
    private final Map<String, String> values = new HashMap<>();

    public static StyleMap of(UiNode node) {
        StyleMap m = new StyleMap();
        for (var e : node.attributes().entrySet()) {
            if (!e.getKey().equals("style")) m.values.put(e.getKey().toLowerCase(), e.getValue());
        }
        String inline = node.attr("style");
        if (inline != null) {
            for (String decl : inline.split(";")) {
                int i = decl.indexOf(':');
                if (i > 0) m.values.put(decl.substring(0, i).trim().toLowerCase(), decl.substring(i + 1).trim());
            }
        }
        return m;
    }

    public String get(String key) { return values.get(key); }

    public String get(String key, String def) { return values.getOrDefault(key, def); }

    public boolean has(String key) { return values.containsKey(key); }

    public Size size(String key, Size def) { return Size.parse(values.get(key), def); }

    public Insets insets(String key, Insets def) { return Insets.parse(values.get(key), def); }

    public int color(String key, int def) { return Colors.parse(values.get(key), def); }

    public int integer(String key, int def) {
        String v = values.get(key);
        if (v == null) return def;
        try {
            return Math.round(Float.parseFloat(v.trim().replace("px", "")));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean bool(String key, boolean def) {
        String v = values.get(key);
        return v == null ? def : v.equalsIgnoreCase("true") || v.equals("1");
    }
}
