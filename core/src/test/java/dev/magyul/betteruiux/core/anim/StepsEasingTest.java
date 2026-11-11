package dev.magyul.betteruiux.core.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StepsEasingTest {
    private static double e(String s, double t) { return Easing.parse(s).apply(t); }

    @Test
    void jumpEndIsTheDefault() {
        assertEquals(0.0, e("steps(4)", 0.0), 1e-9);
        assertEquals(0.0, e("steps(4)", 0.24), 1e-9);
        assertEquals(0.25, e("steps(4)", 0.25), 1e-9);
        assertEquals(0.75, e("steps(4, end)", 0.9), 1e-9);
        assertEquals(1.0, e("steps(4)", 1.0), 1e-9);
    }

    @Test
    void jumpVariants() {
        assertEquals(0.25, e("steps(4, start)", 0.0), 1e-9);
        assertEquals(0.5, e("steps(4, jump-start)", 0.3), 1e-9);
        assertEquals(0.0, e("steps(3, jump-none)", 0.1), 1e-9);
        assertEquals(0.5, e("steps(3, jump-none)", 0.4), 1e-9);
        assertEquals(1.0, e("steps(3, jump-none)", 0.7), 1e-9);
        assertEquals(0.2, e("steps(4, jump-both)", 0.0), 1e-9);
        assertEquals(1.0, e("step-end", 1.0), 1e-9);
        assertEquals(0.0, e("step-end", 0.5), 1e-9);
        assertEquals(1.0, e("step-start", 0.01), 1e-9);
    }

    @Test
    void invalidStepsFallBackToLinear() {
        assertEquals(0.3, e("steps(0)", 0.3), 1e-9);
        assertEquals(0.3, e("steps(x)", 0.3), 1e-9);
        assertEquals(0.3, e("steps(2, a, b)", 0.3), 1e-9);
    }

    @Test
    void worksInShorthands() {
        AnimationSpec a = AnimationSpec.parse("blink 1s steps(4, end) infinite");
        assertEquals(1000, a.durationMs(), 1e-9);
        assertEquals(0.25, a.easing().apply(0.3), 1e-9);
        var t = TransitionSpec.parse("width 200ms steps(2, start) 10ms");
        assertEquals(1, t.size());
        assertEquals(10, t.get(0).delayMs(), 1e-9);
        assertEquals(0.5, t.get(0).easing().apply(0.1), 1e-9);
        assertTrue(Easing.isEasingToken("steps(3)"));
        assertTrue(Easing.isEasingToken("step-start"));
    }
}
