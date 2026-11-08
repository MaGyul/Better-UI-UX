package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.api.UiGfx;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
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
    public void fillGradient(int x, int y, int w, int h, int topArgb, int bottomArgb) {
        g.fillGradient(x, y, x + w, y + h, topArgb, bottomArgb);
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

    /**
     * 이동 → (origin 기준) 회전 → 확대. GUI 의 행렬 스택(pose)을 쓰므로 이후 그리는 모든 요소와 가위(clip)에 적용된다.
     * 회전은 양수가 시계방향(GUI 좌표계는 y 가 아래로 증가).
     */
    @Override
    public void pushTransform(float tx, float ty, float scale, float rotateDeg, float originX, float originY) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(tx, ty);
        pose.translate(originX, originY);
        pose.rotate((float) Math.toRadians(rotateDeg));
        pose.scale(scale, scale);
        pose.translate(-originX, -originY);
    }

    @Override
    public void popTransform() {
        g.pose().popMatrix();
    }

    /**
     * 앞서 그린 레이어까지 블러한다. 26.x 의 블러는 "이전 stratum(레이어)" 전체에 적용되므로,
     * 이 호출 직전까지 그린 배경(예: block 타일)이 블러 대상이 되도록 먼저 stratum 을 나눈다.
     * 게임의 "메뉴 배경 블러" 옵션이 0 이면 vanilla 와 같이 블러하지 않는다.
     */
    /**
     * 26.3 이 지원하는 포인터는 화살표/I빔/십자/손가락/상하·좌우 조절/이동(전체)/금지 뿐이다.
     * 대각선 조절 포인터는 없으므로 모서리 손잡이는 호출하는 쪽에서 십자(CROSSHAIR)를 요청한다.
     */
    @Override
    public void requestCursor(dev.magyul.betteruiux.api.Cursor cursor) {
        g.requestCursor(switch (cursor) {
            case POINTER -> CursorTypes.POINTING_HAND;
            case TEXT -> CursorTypes.IBEAM;
            case CROSSHAIR -> CursorTypes.CROSSHAIR;
            case RESIZE_H -> CursorTypes.RESIZE_EW;
            case RESIZE_V -> CursorTypes.RESIZE_NS;
            case MOVE -> CursorTypes.RESIZE_ALL;
            case NOT_ALLOWED -> CursorTypes.NOT_ALLOWED;
            case DEFAULT -> CursorType.DEFAULT;
        });
    }

    @Override
    public void drawItem(String itemId, int x, int y, int count) {
        Identifier id = Identifier.tryParse(itemId == null ? "" : itemId.trim());
        if (id == null) {
            LOG.warn("[better-uiux] invalid item id '{}'", itemId);
            return;
        }
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id);
        if (item.isEmpty()) {
            LOG.warn("[better-uiux] unknown item '{}'", itemId);
            return;
        }
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item.get(), Math.max(1, count));
        g.item(stack, x, y);
        if (count > 1) g.itemDecorations(font, stack, x, y);
    }

    @Override
    public void blurBackground() {
        g.nextStratum();
        if (Minecraft.getInstance().options.getMenuBackgroundBlurriness() >= 1.0F) {
            g.blurBeforeThisStratum();
        }
    }
}
