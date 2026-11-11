package dev.magyul.betteruiux.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 호출을 기록만 하는 {@link UiGfx}. 코어 테스트와 "그린 뒤 한꺼번에 재생"에 쓴다. */
public final class DrawList implements UiGfx {
    private final List<DrawCommand> commands = new ArrayList<>();

    public List<DrawCommand> commands() { return Collections.unmodifiableList(commands); }

    public void clear() { commands.clear(); }

    /** 기록된 명령을 다른 구현(예: 마인크래프트 백엔드)에 순서대로 재생한다. */
    public void replay(UiGfx target) {
        for (DrawCommand c : commands) {
            switch (c) {
                case DrawCommand.FillRect f -> target.fillRect(f.x(), f.y(), f.w(), f.h(), f.argb());
                case DrawCommand.FillGradient f -> target.fillGradient(f.x(), f.y(), f.w(), f.h(), f.topArgb(), f.bottomArgb());
                case DrawCommand.DrawTexture t -> target.drawTexture(t.texture(), t.x(), t.y(), t.w(), t.h(),
                        t.u(), t.v(), t.regionW(), t.regionH(), t.texW(), t.texH(), t.argb());
                case DrawCommand.DrawText t -> target.drawText(t.text(), t.x(), t.y(), t.argb(), t.shadow());
                case DrawCommand.FillRoundRect f -> {
                    if (!target.fillRoundRect(f.x(), f.y(), f.w(), f.h(), f.tl(), f.tr(), f.br(), f.bl(), f.argb())) target.fillRect(f.x(), f.y(), f.w(), f.h(), f.argb());
                }
                case DrawCommand.StrokeRoundRect f -> target.strokeRoundRect(f.x(), f.y(), f.w(), f.h(), f.tl(), f.tr(), f.br(), f.bl(), f.thickness(), f.argb());
                case DrawCommand.ShadowRoundRect f -> target.shadowRoundRect(f.x(), f.y(), f.w(), f.h(), f.tl(), f.tr(), f.br(), f.bl(), f.blur(), f.argb());
                case DrawCommand.FillRoundGradient f ->
                        target.fillRoundGradient(f.x(), f.y(), f.w(), f.h(), f.tl(), f.tr(), f.br(), f.bl(), f.mode(), f.colors(), f.stops());
                case DrawCommand.DrawStyledText t -> target.drawStyledText(t.text(), t.x(), t.y(), t.argb(), t.shadow(), t.italic(), t.font());
                case DrawCommand.PushClip p -> target.pushClip(p.x(), p.y(), p.w(), p.h());
                case DrawCommand.PopClip p -> target.popClip();
                case DrawCommand.PushTransform t ->
                        target.pushTransform(t.tx(), t.ty(), t.scale(), t.rotateDeg(), t.originX(), t.originY());
                case DrawCommand.PopTransform t -> target.popTransform();
                case DrawCommand.BlurBackground b -> target.blurBackground();
                case DrawCommand.RequestCursor rc -> target.requestCursor(rc.cursor());
                case DrawCommand.DrawItem di -> target.drawItem(di.itemId(), di.x(), di.y(), di.count());
            }
        }
    }

    private boolean roundRects;

    /** true 면 {@link #fillRoundRect} 를 지원하는 척 기록한다(테스트용). 기본은 false: 호출자가 사각형 여러 개로 그린다. */
    public DrawList supportRoundRects(boolean on) {
        this.roundRects = on;
        return this;
    }

    @Override public boolean fillRoundGradient(int x, int y, int w, int h, int tl, int tr, int br, int bl, int mode, int[] colors, float[] stops) {
        if (!roundRects) return false;
        commands.add(new DrawCommand.FillRoundGradient(x, y, w, h, tl, tr, br, bl, mode, colors, stops));
        return true;
    }

    @Override public boolean strokeRoundRect(int x, int y, int w, int h, int tl, int tr, int br, int bl, int thickness, int argb) {
        if (!roundRects) return false;
        commands.add(new DrawCommand.StrokeRoundRect(x, y, w, h, tl, tr, br, bl, thickness, argb));
        return true;
    }

    @Override public boolean shadowRoundRect(int x, int y, int w, int h, int tl, int tr, int br, int bl, int blur, int argb) {
        if (!roundRects) return false;
        commands.add(new DrawCommand.ShadowRoundRect(x, y, w, h, tl, tr, br, bl, blur, argb));
        return true;
    }

    @Override public boolean fillRoundRect(int x, int y, int w, int h, int tl, int tr, int br, int bl, int argb) {
        if (!roundRects) return false;
        commands.add(new DrawCommand.FillRoundRect(x, y, w, h, tl, tr, br, bl, argb));
        return true;
    }

    @Override public void drawStyledText(String text, int x, int y, int argb, boolean shadow, boolean italic, String font) {
        commands.add(new DrawCommand.DrawStyledText(text, x, y, argb, shadow, italic, font));
    }

    @Override public void fillRect(int x, int y, int w, int h, int argb) {
        commands.add(new DrawCommand.FillRect(x, y, w, h, argb));
    }

    @Override public void fillGradient(int x, int y, int w, int h, int topArgb, int bottomArgb) {
        commands.add(new DrawCommand.FillGradient(x, y, w, h, topArgb, bottomArgb));
    }

    @Override public void drawTexture(String texture, int x, int y, int w, int h,
                                      float u, float v, int regionW, int regionH, int texW, int texH, int argb) {
        commands.add(new DrawCommand.DrawTexture(texture, x, y, w, h, u, v, regionW, regionH, texW, texH, argb));
    }

    @Override public void drawText(String text, int x, int y, int argb, boolean shadow) {
        commands.add(new DrawCommand.DrawText(text, x, y, argb, shadow));
    }

    @Override public void pushClip(int x, int y, int w, int h) { commands.add(new DrawCommand.PushClip(x, y, w, h)); }

    @Override public void popClip() { commands.add(new DrawCommand.PopClip()); }

    @Override public void pushTransform(float tx, float ty, float scale, float rotateDeg, float originX, float originY) {
        commands.add(new DrawCommand.PushTransform(tx, ty, scale, rotateDeg, originX, originY));
    }

    @Override public void popTransform() { commands.add(new DrawCommand.PopTransform()); }

    @Override public void drawItem(String itemId, int x, int y, int count) {
        commands.add(new DrawCommand.DrawItem(itemId, x, y, count));
    }

    @Override public void requestCursor(Cursor cursor) { commands.add(new DrawCommand.RequestCursor(cursor)); }

    @Override public void blurBackground() { commands.add(new DrawCommand.BlurBackground()); }
}
