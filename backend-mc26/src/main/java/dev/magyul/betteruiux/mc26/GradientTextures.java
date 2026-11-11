package dev.magyul.betteruiux.mc26;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * 그라디언트 색 띠(가로 {@value #WIDTH}칸, 세로 1칸 동적 텍스처). 같은 정지점은 하나를 공유하고, 오래 안 쓴 것부터 {@value #MAX} 개까지만 둔다.
 * 둥근 그라디언트 셰이더가 이 띠를 0..1 위치로 읽는다.
 */
final class GradientTextures {
    private GradientTextures() {}

    private static final int WIDTH = 256, MAX = 64;

    private record Entry(Identifier id, DynamicTexture texture) {}

    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static int serial;

    /** 정지점에 해당하는 색 띠의 텍스처 id(렌더 스레드에서 호출). 만들 수 없으면 null. */
    static Identifier get(int[] colors, float[] stops) {
        if (colors == null || stops == null || colors.length == 0 || colors.length != stops.length) return null;
        String key = Arrays.toString(colors) + Arrays.toString(stops);
        Entry e = CACHE.get(key);
        if (e != null) return e.id();
        try {
            if (CACHE.size() >= MAX) {                      // 가장 오래 안 쓴 것을 버린다
                var it = CACHE.entrySet().iterator();
                Map.Entry<String, Entry> old = it.next();
                it.remove();
                Minecraft.getInstance().getTextureManager().release(old.getValue().id());
            }
            Identifier id = Identifier.fromNamespaceAndPath("better_uiux", "gradient/" + (serial++));
            DynamicTexture tex = new DynamicTexture(() -> "better_uiux:" + id.getPath(), WIDTH, 1, false);
            int[] px = new int[WIDTH];
            for (int i = 0; i < WIDTH; i++) px[i] = at(colors, stops, (i + 0.5f) / WIDTH);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            ExternalImages.upload(tex, WIDTH, 1, px);
            e = new Entry(id, tex);
            CACHE.put(key, e);
            return id;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** 위치 t(0..1)의 색: 정지점 사이를 argb 채널별로 선형 보간(코어의 BoxPainter 와 같은 규칙). */
    static int at(int[] colors, float[] stops, float t) {
        if (t <= stops[0]) return colors[0];
        for (int i = 1; i < stops.length; i++) {
            if (t <= stops[i]) {
                float span = stops[i] - stops[i - 1];
                return span <= 0 ? colors[i] : lerp(colors[i - 1], colors[i], (t - stops[i - 1]) / span);
            }
        }
        return colors[colors.length - 1];
    }

    private static int lerp(int a, int b, float t) {
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
            out |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }
}
