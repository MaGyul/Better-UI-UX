package dev.magyul.betteruiux.core.anim;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code transition: opacity 200ms ease-out 50ms, background 150ms} 의 항목 하나.
 * 속성 이름 {@code all} 은 전환 가능한 모든 속성을 뜻한다.
 */
public record TransitionSpec(String property, double durationMs, double delayMs, Easing easing) {
    public boolean covers(String prop) { return property.equals("all") || property.equals(prop); }

    /** 파싱할 수 없는 항목은 건너뛴다. */
    public static List<TransitionSpec> parse(String value) {
        List<TransitionSpec> out = new ArrayList<>();
        if (value == null || value.isBlank() || value.trim().equalsIgnoreCase("none")) return out;
        for (String item : Timing.splitTopLevel(value)) {
            String prop = null;
            Easing easing = Easing.LINEAR;
            Double duration = null, delay = null;
            for (String tok : Timing.tokens(item)) {
                if (Timing.isTime(tok)) {
                    if (duration == null) duration = Timing.millis(tok);
                    else delay = Timing.millis(tok);
                } else if (Easing.isEasingToken(tok)) {
                    easing = Easing.parse(tok);
                } else if (prop == null) {
                    prop = tok.toLowerCase();
                }
            }
            if (prop != null && duration != null) {
                out.add(new TransitionSpec(prop, duration, delay == null ? 0 : delay, easing));
            }
        }
        return out;
    }
}
