package dev.magyul.betteruiux.core.layout;

import java.util.HashMap;
import java.util.Map;

/** 노드 하나의 해석된(computed) 스타일 값 모음. 값은 문자열로 보관하고 타입별 조회 헬퍼를 제공한다. */
public final class StyleMap {
    private final Map<String, String> values = new HashMap<>();

    public StyleMap copy() {
        StyleMap m = new StyleMap();
        m.values.putAll(values);
        return m;
    }

    public void put(String key, String value) { values.put(key, value); }

    public void putAll(StyleMap other) { values.putAll(other.values); }

    public Map<String, String> entries() { return values; }

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
