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
    private static final int MAX = 32;

    private static final Map<Long, RenderPipeline> CACHE = new HashMap<>();
    private static boolean broken;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("better-uiux");
    private static final Identifier SHADER = Identifier.parse("better_uiux:core/round");

    /** 흰색 1x1 텍스처(샘플러 바인딩용). */
    static final Identifier WHITE = Identifier.parse("better_uiux:textures/gui/white.png");

    /** @param thick 0 이 아니면 테두리 고리, @param blur 0 이 아니면 그림자 */
    static RenderPipeline get(int radius, int thick, int blur) {
        if (broken || radius < 0 || radius > 64 || thick < 0 || thick > 32 || blur < 0 || blur > 48) return null;
        if (radius == 0 && thick == 0 && blur == 0) return null;
        long key = ((long) radius << 40) | ((long) thick << 20) | blur;
        RenderPipeline p = CACHE.get(key);
        if (p != null) return p;
        if (CACHE.size() >= MAX) return null;
        try {
            p = RenderPipeline.builder()
                    .withLocation(Identifier.parse("better_uiux:round_" + radius + "_" + thick + "_" + blur))
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withVertexShader(SHADER)
                    .withFragmentShader(SHADER)
                    .withShaderDefine("RADIUS", radius)
                    .withShaderDefine("THICK", thick)
                    .withShaderDefine("BLUR", blur)
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
        LOGGER.info("[better-uiux] 둥근 사각형 파이프라인 생성: 반지름 {} 두께 {} 번짐 {}", radius, thick, blur);
        return p;
    }
}
