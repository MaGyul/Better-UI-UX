package dev.magyul.betteruiux.core.render;

import dev.magyul.betteruiux.api.UiGfx;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code <screen use="...">} 의 배경 레이어. 쉼표/공백으로 구분한 토큰을 <b>적은 순서대로</b> 쌓는다.
 * <ul>
 *   <li>{@code blur} — 지금까지 그려진 것(월드, 앞선 레이어)을 블러</li>
 *   <li>{@code dim} — 어둡게(게임 기본 인게임 메뉴 배경과 같은 그라데이션)</li>
 *   <li>{@code block:<id>} — 블록 텍스처를 타일로 깐다. {@code block:minecraft:dirt}, {@code block:dirt}(= minecraft)</li>
 *   <li>{@code none} — 아무것도 그리지 않음(단독으로만 사용)</li>
 * </ul>
 * 예: {@code use="block:minecraft:dirt,blur"} 는 흙 타일을 그린 뒤 그 위에 블러를 적용한다.
 * {@code use} 가 없으면 {@code blur}. 블록 타일 크기/색조는 {@code block-size}(기본 32), {@code block-tint}(기본 #FF404040).
 */
public final class ScreenBackground {
    public sealed interface Layer {
        record Blur() implements Layer {}

        record Dim() implements Layer {}

        /** @param texture 타일로 깔 텍스처 식별자(예: {@code minecraft:textures/block/dirt.png}) */
        record Block(String texture) implements Layer {}
    }

    /** vanilla 인게임 메뉴 배경과 같은 그라데이션 색(0xC0101010 → 0xD0101010). */
    public static final int DIM_TOP = 0xC0101010;
    public static final int DIM_BOTTOM = 0xD0101010;
    public static final int DEFAULT_BLOCK_SIZE = 32;
    public static final int DEFAULT_BLOCK_TINT = 0xFF404040;

    private final List<Layer> layers;
    private final boolean none;
    private final List<String> warnings;

    private ScreenBackground(List<Layer> layers, boolean none, List<String> warnings) {
        this.layers = List.copyOf(layers);
        this.none = none;
        this.warnings = List.copyOf(warnings);
    }

    public List<Layer> layers() { return layers; }

    /** {@code use="none"} 으로 명시했는지. 이때는 백엔드가 파노라마 같은 기본 배경도 깔지 않는다. */
    public boolean isNone() { return none; }

    public List<String> warnings() { return warnings; }

    public static ScreenBackground defaults() { return parse(null); }

    public static ScreenBackground parse(String use) {
        List<Layer> layers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (use == null || use.isBlank()) {
            layers.add(new Layer.Blur());
            return new ScreenBackground(layers, false, warnings);
        }
        boolean sawNone = false;
        for (String raw : use.trim().split("[,\\s]+")) {
            if (raw.isEmpty()) continue;
            String token = raw.toLowerCase();
            switch (token) {
                case "blur" -> layers.add(new Layer.Blur());
                case "dim" -> layers.add(new Layer.Dim());
                case "none" -> sawNone = true;
                default -> {
                    if (token.startsWith("block:")) {
                        String tex = blockTexture(raw.substring("block:".length()));
                        if (tex != null) layers.add(new Layer.Block(tex));
                        else warnings.add("use: 잘못된 블록 id '" + raw + "'");
                    } else {
                        warnings.add("use: 알 수 없는 값 '" + raw + "' (blur, dim, block:<id>, none)");
                    }
                }
            }
        }
        if (sawNone && !layers.isEmpty()) {
            warnings.add("use: 'none' 은 다른 값과 함께 쓸 수 없어 무시합니다");
            sawNone = false;
        }
        return new ScreenBackground(layers, sawNone, warnings);
    }

    /** {@code minecraft:dirt} / {@code dirt} → {@code minecraft:textures/block/dirt.png}. 잘못된 id 면 null. */
    static String blockTexture(String id) {
        String ns = "minecraft", path = id.toLowerCase();
        int colon = path.indexOf(':');
        if (colon >= 0) {
            ns = path.substring(0, colon);
            path = path.substring(colon + 1);
        }
        if (!ns.matches("[a-z0-9_.-]+") || !path.matches("[a-z0-9_./-]+") || path.contains("..")) return null;
        return ns + ":textures/block/" + path + ".png";
    }

    /** 레이어를 순서대로 그린다. */
    public void paint(UiGfx g, int viewW, int viewH, int blockSize, int blockTint) {
        int tile = Math.max(1, blockSize);
        for (Layer layer : layers) {
            switch (layer) {
                case Layer.Blur b -> g.blurBackground();
                case Layer.Dim d -> g.fillGradient(0, 0, viewW, viewH, DIM_TOP, DIM_BOTTOM);
                // 텍스처 한 장을 화면 전체 크기의 영역으로 요청해 반복(타일) 샘플링한다(vanilla 메뉴 배경과 같은 방식).
                case Layer.Block b -> g.drawTexture(b.texture(), 0, 0, viewW, viewH, 0, 0, viewW, viewH, tile, tile, blockTint);
            }
        }
    }
}
