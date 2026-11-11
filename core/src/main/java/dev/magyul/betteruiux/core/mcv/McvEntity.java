package dev.magyul.betteruiux.core.mcv;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** 가상 월드에 소환된 엔티티의 데이터 모델(렌더 스레드의 실제 엔티티 객체와는 저널로 동기화된다). */
public final class McvEntity {
    public static final int MAX_FIELDS = 32, MAX_STRING = 256;
    private static final Pattern TYPE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern FIELD = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,31}");

    private final int id;
    private final String type;
    private volatile double x, y, z;
    private volatile float yaw, pitch;
    private final Map<String, Object> fields = new ConcurrentHashMap<>();

    McvEntity(int id, String type, double x, double y, double z) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    static String normalizeType(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 128) throw new IllegalArgumentException("엔티티 타입이 없거나 너무 깁니다");
        String t = raw.trim();
        if (!t.contains(":")) t = "minecraft:" + t;
        if (!TYPE.matcher(t).matches()) throw new IllegalArgumentException("엔티티 타입이 올바르지 않습니다: " + raw);
        return t;
    }

    public int id() { return id; }

    public String type() { return type; }

    public double x() { return x; }

    public double y() { return y; }

    public double z() { return z; }

    public float yaw() { return yaw; }

    public float pitch() { return pitch; }

    public Map<String, Object> fields() { return Map.copyOf(fields); }

    void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /** 이름이 올바르고 값이 숫자/불리언/짧은 글자일 때만 저장한다. 위치·회전은 필드가 아니라 전용 메서드로 바꾼다. */
    void setField(String name, Object value) {
        if (name == null || !FIELD.matcher(name).matches()) throw new IllegalArgumentException("필드 이름이 올바르지 않습니다");
        if (value == null) {
            fields.remove(name);
            return;
        }
        if (!(value instanceof Number || value instanceof Boolean
                || value instanceof String s && s.length() <= MAX_STRING)) {
            throw new IllegalArgumentException("필드 값은 숫자/불리언/짧은 글자만 가능합니다");
        }
        if (!fields.containsKey(name) && fields.size() >= MAX_FIELDS) throw new IllegalArgumentException("필드가 너무 많습니다");
        fields.put(name, value);
    }

    Object getField(String name) {
        return switch (name) {
            case "x" -> x;
            case "y" -> y;
            case "z" -> z;
            case "yaw" -> (double) yaw;
            case "pitch" -> (double) pitch;
            case "type" -> type;
            default -> fields.get(name);
        };
    }
}
