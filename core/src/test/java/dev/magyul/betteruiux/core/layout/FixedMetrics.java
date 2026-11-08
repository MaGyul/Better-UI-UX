package dev.magyul.betteruiux.core.layout;

import dev.magyul.betteruiux.api.TextMetrics;

/** 테스트용 고정폭 글꼴: 글자당 6px, 줄 높이 9px (마인크래프트 기본 폰트와 같은 높이). */
public final class FixedMetrics implements TextMetrics {
    @Override public int width(String text) { return text.length() * 6; }

    @Override public int lineHeight() { return 9; }
}
