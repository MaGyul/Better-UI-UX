package dev.magyul.betteruiux.mc26;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

/**
 * 둥근 사각형용 렌더 파이프라인(셰이더 {@code better_uiux:core/round}): 채움, 테두리 고리, 번지는 그림자. 반지름/두께/번짐은 셰이더 정의라서
 * 조합마다 파이프라인이 하나 필요하고 처음 쓸 때 만든다. 만들기에 실패하거나 조합이 너무 많으면 null(호출자가 사각형 여러 개로 대신 그린다).
 */
final class RoundPipelines {
    private RoundPipelines() {}

    /** 만들어 둘 수 있는 서로 다른 조합의 수(파이프라인 컴파일 비용을 제한). */
    private static final int MAX = 64;

    private static final Map<String, RenderPipeline> CACHE = new HashMap<>();
    private static boolean broken;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("better-uiux");
    private static final Identifier SHADER = Identifier.parse("better_uiux:core/round");

    /** 흰색 1x1 텍스처(샘플러 바인딩용). */
    static final Identifier WHITE = Identifier.parse("better_uiux:textures/gui/white.png");

    /** @param thick 0 이 아니면 테두리 고리, @param blur 0 이 아니면 그림자 */
    static RenderPipeline get(int tl, int tr, int br, int bl, int thick, int blur) { return get(tl, tr, br, bl, thick, blur, 0); }

    /** @param grad 0 이 아니면 그라디언트 채움: 1 세로, 2 가로, 3 방사형, 4 원뿔(색은 Sampler0 의 가로 1줄 색 띠에서 읽는다) */
    static RenderPipeline get(int tl, int tr, int br, int bl, int thick, int blur, int grad) {
        return get(tl, tr, br, bl, thick, blur, grad, 0, 0, 0, false);
    }

    /** 안쪽 그림자 파이프라인: 번짐 {@code blur}, 오프셋 (ox, oy), 줄이기 spread. */
    static RenderPipeline getInset(int tl, int tr, int br, int bl, int blur, int ox, int oy, int spread) {
        return get(tl, tr, br, bl, 0, blur, 0, ox, oy, spread, true);
    }

    private static RenderPipeline get(int tl, int tr, int br, int bl, int thick, int blur, int grad, int ox, int oy, int spread, boolean inset) {
        if (broken || thick < 0 || thick > 32 || blur < 0 || blur > 48) return null;
        for (int r : new int[] {tl, tr, br, bl}) if (r < 0 || r > 64) return null;
        if (!inset && tl + tr + br + bl == 0 && thick == 0 && blur == 0 && grad == 0) return null;   // 둥글지도 번지지도 않는 단색 채움은 사각형 하나면 된다
        if (inset && (Math.abs(ox) > 64 || Math.abs(oy) > 64 || spread < -32 || spread > 64)) return null;
        String key = tl + "," + tr + "," + br + "," + bl + "," + thick + "," + blur + "," + grad + (inset ? ",i" + ox + "," + oy + "," + spread : "");
        RenderPipeline p = CACHE.get(key);
        if (p != null) return p;
        if (CACHE.size() >= MAX) return null;
        try {
            p = RenderPipeline.builder()
                    .withLocation(Identifier.parse("better_uiux:round_" + key.replace(',', '_').replace('-', 'm')))
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withVertexShader(SHADER)
                    .withFragmentShader(SHADER)
                    .withShaderDefine("RTL", tl)
                    .withShaderDefine("RTR", tr)
                    .withShaderDefine("RBR", br)
                    .withShaderDefine("RBL", bl)
                    .withShaderDefine("THICK", thick)
                    .withShaderDefine("BLUR", inset ? 0 : blur)
                    .withShaderDefine("IBLUR", inset ? blur : 0)
                    .withShaderDefine("GRAD", grad)
                    .withShaderDefine("INSET", inset ? 1 : 0)
                    .withShaderDefine("OX", ox)
                    .withShaderDefine("OY", oy)
                    .withShaderDefine("SPREAD", spread)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .build();
        } catch (RuntimeException e) {
            broken = true;                                  // 한 번 실패하면 다시 시도하지 않는다
            LOGGER.warn("[better-uiux] 둥근 사각형 파이프라인을 만들지 못해 사각형 근사로 대신 그립니다", e);
            return null;
        }
        CACHE.put(key, p);
        LOGGER.info("[better-uiux] 둥근 사각형 파이프라인 생성: {}", key);
        return p;
    }
}
