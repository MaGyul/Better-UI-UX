package dev.magyul.betteruiux.core.mcv;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 가상 월드의 블록 한 칸: 블록 id, 블록 상태(properties), 선택적 블록 엔티티 NBT(SNBT 글자).
 * 문자열 형식은 마인크래프트와 같다: {@code minecraft:oak_stairs[facing=east,half=bottom]}. 네임스페이스를 생략하면 minecraft.
 */
public record McvBlock(String id, Map<String, String> state, String nbt) {
    public static final McvBlock AIR = new McvBlock("minecraft:air", Map.of(), null);
    public static final int MAX_NBT = 4096;

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern PROP = Pattern.compile("[a-z0-9_]+");
    private static final Pattern VALUE = Pattern.compile("[a-zA-Z0-9_.-]+");

    public McvBlock {
        state = Map.copyOf(new TreeMap<>(state));
    }

    public boolean isAir() {
        return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
    }

    /** 글자에서 블록을 읽는다. 잘못되면 {@link IllegalArgumentException}. */
    public static McvBlock parse(String text, String nbt) {
        if (text == null || text.isBlank() || text.length() > 256) throw new IllegalArgumentException("블록 id 가 없거나 너무 깁니다");
        String s = text.trim();
        String idPart = s;
        Map<String, String> state = new TreeMap<>();
        int br = s.indexOf('[');
        if (br >= 0) {
            if (!s.endsWith("]")) throw new IllegalArgumentException("블록 상태 괄호가 닫히지 않았습니다: " + text);
            idPart = s.substring(0, br);
            String inner = s.substring(br + 1, s.length() - 1);
            if (!inner.isBlank()) {
                for (String pair : inner.split(",")) {
                    int eq = pair.indexOf('=');
                    if (eq < 0) throw new IllegalArgumentException("블록 상태는 key=value 형식입니다: " + pair);
                    String k = pair.substring(0, eq).trim(), v = pair.substring(eq + 1).trim();
                    if (!PROP.matcher(k).matches() || !VALUE.matcher(v).matches()) {
                        throw new IllegalArgumentException("블록 상태가 올바르지 않습니다: " + pair);
                    }
                    state.put(k, v);
                }
            }
        }
        if (!idPart.contains(":")) idPart = "minecraft:" + idPart;
        if (!ID.matcher(idPart).matches()) throw new IllegalArgumentException("블록 id 가 올바르지 않습니다: " + idPart);
        if (nbt != null && nbt.length() > MAX_NBT) throw new IllegalArgumentException("블록 NBT 가 너무 깁니다");
        return new McvBlock(idPart, state, nbt == null || nbt.isBlank() ? null : nbt);
    }

    /** 블록 id + 상태를 마인크래프트 형식 글자로(NBT 제외). */
    public String stateString() {
        if (state.isEmpty()) return id;
        StringBuilder sb = new StringBuilder(id).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : state.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }
}
