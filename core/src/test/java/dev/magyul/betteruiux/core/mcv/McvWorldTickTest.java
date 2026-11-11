package dev.magyul.betteruiux.core.mcv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 월드 시계: 시간(나노초)을 받아 틱 수로 바꾼다. */
class McvWorldTickTest {
    private static final long MS = 1_000_000L;

    @Test
    void ticksFollowTheRateAndKeepTheFraction() {
        McvWorld w = new McvWorld(2, 2, 2);                           // 기본 20 tps = 50ms 마다 1틱
        assertEquals(0, w.advance(0, 5), "처음 호출은 기준만 잡는다");
        assertEquals(0, w.advance(30 * MS, 5));
        assertEquals(1, w.advance(60 * MS, 5), "60ms 에 1틱, 10ms 남음");
        assertTrue(Math.abs(w.partialTick() - 0.2f) < 0.01f, "남은 10ms = 0.2틱: " + w.partialTick());
        assertEquals(1, w.advance(100 * MS, 5));
        assertEquals(2, w.tickCount());
    }

    @Test
    void rateChangesAndPause() {
        McvWorld w = new McvWorld(2, 2, 2);
        w.advance(0, 100);
        w.setTickRate(100f);                                           // 10ms 마다
        assertEquals(5, w.advance(50 * MS, 100));
        w.setTickRate(0f);
        assertEquals(0, w.advance(500 * MS, 100), "0 이면 멈춘다");
        w.setTickRate(20f);
        assertEquals(0, w.advance(520 * MS, 100), "멈췄던 시간은 따라잡지 않는다");
        assertEquals(1, w.advance(570 * MS, 100));
    }

    @Test
    void doesNotCatchUpAfterBeingHiddenOrBeyondTheFrameBudget() {
        McvWorld w = new McvWorld(2, 2, 2);
        w.advance(0, 5);
        assertEquals(0, w.advance(5_000 * MS, 5), "5초 안 그렸다(숨겨져 있었다): 건너뛴다");
        assertEquals(5, w.advance(5_000 * MS + 900 * MS, 5), "한 번에 최대 5틱");
    }

    @Test
    void tickRateIsValidated() {
        McvWorld w = new McvWorld(2, 2, 2);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> w.setTickRate(-1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> w.setTickRate(1000));
    }
}
