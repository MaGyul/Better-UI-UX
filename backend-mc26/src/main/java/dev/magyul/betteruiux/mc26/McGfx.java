package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.api.UiGfx;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link UiGfx} → 마인크래프트 26.3 GUI 렌더 상태 수집기({@link GuiGraphicsExtractor}) 변환.
 * 공식 GUI 경로만 사용하므로 Vulkan/OpenGL 어느 렌더러에서도 동일하게 동작한다(직접 GL 호출 없음).
 */
public final class McGfx implements UiGfx {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");

    private final GuiGraphicsExtractor g;
    private final Font font;

    public McGfx(GuiGraphicsExtractor g, Font font) {
        this.g = g;
        this.font = font;
    }

    @Override
    public void fillRect(int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + h, argb);
    }

    @Override
    public void drawTexture(String texture, int x, int y, int w, int h,
                            float u, float v, int regionW, int regionH, int texW, int texH, int argb) {
        Identifier id;
        try {
            id = Identifier.parse(texture);
        } catch (RuntimeException e) {
            LOG.warn("[better-uiux] invalid texture id '{}'", texture);
            return;
        }
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, regionW, regionH, texW, texH, argb);
    }

    @Override
    public void drawText(String text, int x, int y, int argb, boolean shadow) {
        g.text(font, text, x, y, argb, shadow);
    }

    @Override
    public void pushClip(int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
    }

    @Override
    public void popClip() {
        g.disableScissor();
    }
}
