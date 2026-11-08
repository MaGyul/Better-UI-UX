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
                case DrawCommand.PushClip p -> target.pushClip(p.x(), p.y(), p.w(), p.h());
                case DrawCommand.PopClip p -> target.popClip();
                case DrawCommand.BlurBackground b -> target.blurBackground();
            }
        }
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

    @Override public void blurBackground() { commands.add(new DrawCommand.BlurBackground()); }
}
