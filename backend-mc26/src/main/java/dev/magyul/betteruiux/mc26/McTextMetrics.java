package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.api.TextMetrics;
import net.minecraft.client.gui.Font;

/** 게임 폰트 기반 글자 크기 측정. */
public final class McTextMetrics implements TextMetrics {
    private final Font font;

    public McTextMetrics(Font font) { this.font = font; }

    @Override
    public int width(String text) { return font.width(text); }

    @Override
    public int lineHeight() { return font.lineHeight; }
}
