package dev.magyul.betteruiux.core.anim;

import dev.magyul.betteruiux.core.style.Selector;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** {@code transition} / {@code animation} 값의 문법 검사(경고용). 문제가 없으면 null, 있으면 사람이 읽을 사유. */
public final class AnimSyntax {
    private AnimSyntax() {}

    private static final Set<String> DIRECTIONS = Set.of("normal", "reverse", "alternate", "alternate-reverse");
    private static final Set<String> FILLS = Set.of("none", "forwards", "backwards", "both");
    private static final Set<String> STATES = Set.of("running", "paused");

    /** {@code transition: 속성 지속시간 [이징] [지연], ...}. */
    public static String checkTransition(String value) {
        String low = value.trim().toLowerCase(Locale.ROOT);
        if (low.equals("none")) return null;
        for (String part : Selector.splitList(low)) {
            int times = 0, easings = 0;
            String prop = null;
            for (String tok : Timing.tokens(part.trim())) {
                if (Timing.isTime(tok)) times++;
                else if (Easing.isEasingToken(tok)) easings++;
                else if (prop == null) prop = tok;
                else return "알 수 없는 값 '" + tok + "' (전환은 '속성 지속시간 [이징] [지연]' 형식입니다)";
            }
            if (times == 0) return "지속 시간(예: 0.2s, 200ms)이 없습니다";
            if (times > 2) return "시간 값은 지속 시간과 지연, 둘까지입니다";
            if (easings > 1) return "이징은 하나만 쓸 수 있습니다";
            if (prop != null && !prop.equals("all") && !Interpolator.ANIMATABLE.contains(prop) && !Interpolator.LAYOUT.contains(prop)) {
                return "'" + prop + "' 은(는) 전환할 수 없는 속성입니다(전환 가능: all, opacity, background, width, margin …)";
            }
        }
        return null;
    }

    /** {@code animation: 이름 지속시간 [이징] [지연] [반복] [방향] [채움] [상태], ...}. */
    public static String checkAnimation(String value) {
        String low = value.trim().toLowerCase(Locale.ROOT);
        if (low.equals("none")) return null;
        for (String part : Selector.splitList(low)) {
            int times = 0, easings = 0;
            String name = null;
            for (String tok : Timing.tokens(part.trim())) {
                if (Timing.isTime(tok)) times++;
                else if (Easing.isEasingToken(tok)) easings++;
                else if (tok.equals("infinite") || Timing.isNumber(tok) || DIRECTIONS.contains(tok) || FILLS.contains(tok) || STATES.contains(tok)) continue;
                else if (name == null) name = tok;
                else return "알 수 없는 값 '" + tok + "' (애니메이션은 '이름 지속시간 [이징] [지연] [반복] [방향] [채움]' 형식입니다)";
            }
            if (name == null) return "@keyframes 이름이 없습니다";
            if (times == 0) return "지속 시간(예: 1s)이 없습니다";
            if (times > 2) return "시간 값은 지속 시간과 지연, 둘까지입니다";
            if (easings > 1) return "이징은 하나만 쓸 수 있습니다";
        }
        return null;
    }
}
