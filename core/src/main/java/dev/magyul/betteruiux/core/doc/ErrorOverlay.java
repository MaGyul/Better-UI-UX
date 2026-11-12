package dev.magyul.betteruiux.core.doc;

import dev.magyul.betteruiux.api.TextMetrics;
import dev.magyul.betteruiux.api.UiGfx;
import java.util.List;

/** 로드 오류/경고를 화면 위쪽에 띠로 표시한다(핫리로드 중 깨진 XML/CSS 를 바로 알 수 있게). */
public final class ErrorOverlay {
    private ErrorOverlay() {}

    public record Message(boolean error, String text) {}

    private static final int MAX_LINES = 6;

    public static void paint(UiGfx g, TextMetrics tm, int viewW, List<Message> messages) {
        int y = 0;
        int lineH = tm.lineHeight() + 4;
        int shown = Math.min(messages.size(), MAX_LINES);
        for (int i = 0; i < shown; i++) {
            Message m = messages.get(i);
            String text = (m.error() ? "[오류] " : "[경고] ") + m.text().replace('\n', ' ');
            g.fillRect(0, y, viewW, lineH, m.error() ? 0xE0801010 : 0xE0806010);
            g.drawText(fit(tm, text, viewW - 8), 4, y + 2, 0xFFFFFFFF, false);
            y += lineH;
        }
        if (messages.size() > shown) {
            g.fillRect(0, y, viewW, lineH, 0xE0303030);
            g.drawText("... +" + (messages.size() - shown), 4, y + 2, 0xFFFFFFFF, false);
        }
    }

    private static String fit(TextMetrics tm, String text, int maxWidth) {
        if (tm.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && tm.width(text.substring(0, end) + ellipsis) > maxWidth) end--;
        return text.substring(0, end) + ellipsis;
    }
}
