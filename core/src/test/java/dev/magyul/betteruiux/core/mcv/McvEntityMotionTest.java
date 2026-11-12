package dev.magyul.betteruiux.core.mcv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class McvEntityMotionTest {
    private static final long TICK = 50_000_000L;   // 20 틱/초

    private static void ticks(McvWorld w, int n) {
        // advance 는 호출 사이 시간으로 틱을 센다: 같은 기준에서 시간을 틱 단위로 올려 가며 부른다.
        for (int i = 1; i <= n; i++) w.advance(TICKBASE.addAndGet(TICK), 5);
    }

    private static final java.util.concurrent.atomic.AtomicLong TICKBASE = new java.util.concurrent.atomic.AtomicLong();

    private static McvWorld fresh() {
        TICKBASE.set(0);
        McvWorld w = new McvWorld(16, 16, 16);
        w.advance(0, 5);
        return w;
    }

    @Test
    void moveToArrivesAfterExactlyThatManyTicks() {
        McvWorld w = fresh();
        McvEntity e = w.spawnEntity(0, 0, 0, "cow");
        w.moveEntityTo(e.id(), 10, 0, 0, 20);
        assertTrue(e.moving());
        ticks(w, 10);
        assertEquals(5.0, e.x(), 1e-6, "절반 시간에 절반 거리");
        assertTrue(e.moving());
        ticks(w, 10);
        assertEquals(10.0, e.x(), 1e-9);
        assertFalse(e.moving(), "도착하면 멈춘다");
        ticks(w, 1);
        assertEquals(0f, e.stepSpeed(), 0f, "한 틱 더 돌리면 속도는 0");
    }

    @Test
    void facesTheDirectionOfTravelAndSpeedIsReported() {
        McvWorld w = fresh();
        McvEntity e = w.spawnEntity(0, 0, 0, "cow");
        w.moveEntityTo(e.id(), 0, 0, 10, 20);       // +z 로 간다 → yaw 0
        ticks(w, 1);
        assertEquals(0.5f, e.stepSpeed(), 1e-6f);
        assertEquals(0f, e.yaw(), 1e-3f);
        w.moveEntityTo(e.id(), -10, 0, 0, 20);      // -x 로 간다 → yaw 90(마인크래프트에서 -x 가 90)
        ticks(w, 6);
        assertEquals(90f, e.yaw(), 4f, "방향을 몇 틱에 걸쳐 돌려 도달한다(앞서 z 로 반 블록 갔으므로 정확히 90 은 아니다)");
    }

    @Test
    void faceMovementCanBeDisabled() {
        McvWorld w = fresh();
        McvEntity e = w.spawnEntity(0, 0, 0, "cow");
        w.setEntityField(e.id(), "faceMovement", false);
        w.moveEntityTo(e.id(), -10, 0, 0, 20);
        ticks(w, 5);
        assertEquals(0f, e.yaw(), 0f);
    }

    @Test
    void velocityKeepsMovingUntilStopped() {
        McvWorld w = fresh();
        McvEntity e = w.spawnEntity(0, 0, 0, "cow");
        w.setEntityVelocity(e.id(), 0.25, 0, 0);
        ticks(w, 4);
        assertEquals(1.0, e.x(), 1e-6);
        assertTrue(e.moving());
        w.stopEntity(e.id());
        assertFalse(e.moving());
        ticks(w, 4);
        assertEquals(1.0, e.x(), 1e-6);
        assertThrows(IllegalArgumentException.class, () -> w.setEntityVelocity(e.id(), 99, 0, 0));
    }

    @Test
    void walkToUsesBlocksPerSecondAndTickRate() {
        McvWorld w = fresh();
        McvEntity e = w.spawnEntity(0, 0, 0, "cow");
        w.walkEntityTo(e.id(), 4, 0, 0, 2);          // 초당 2블록, 20 틱/초 → 40 틱
        ticks(w, 39);
        assertTrue(e.moving());
        ticks(w, 1);
        assertEquals(4.0, e.x(), 1e-6);
        assertFalse(e.moving());
        assertThrows(IllegalArgumentException.class, () -> w.walkEntityTo(e.id(), 1, 0, 0, 0));
    }

    @Test
    void teleportAndZeroTicksDoNotInterpolate() {
        McvWorld w = fresh();
        McvEntity e = w.spawnEntity(0, 0, 0, "cow");
        w.moveEntityTo(e.id(), 7, 0, 0, 0);
        assertEquals(7.0, e.x(), 0);
        assertEquals(7.0, e.renderX(0.5f), 0, "순간이동은 이전 위치로 끌려가지 않는다");
        w.moveEntity(e.id(), 3, 0, 0);
        assertEquals(3.0, e.renderX(0f), 0);
    }

    @Test
    void renderInterpolatesBetweenTicks() {
        McvWorld w = fresh();
        McvEntity e = w.spawnEntity(0, 0, 0, "cow");
        w.setEntityVelocity(e.id(), 1, 0, 0);
        ticks(w, 1);                                  // 이전 0 → 현재 1
        assertEquals(0.25, e.renderX(0.25f), 1e-6);
        assertEquals(1.0, e.renderX(1f), 1e-6);
    }

    @Test
    void lookAtTurnsOnlyTheHead() {
        McvWorld w = fresh();
        McvEntity e = w.spawnEntity(0, 0, 0, "cow");
        w.entityLookAt(e.id(), -5, 1.5, 0);           // -x 쪽, 눈높이와 같은 높이
        assertEquals(0f, e.renderYaw(1f), 1e-4f, "몸은 그대로");
        assertEquals(90f, e.renderHeadYaw(1f), 1e-3f);
        assertEquals(0f, e.pitch(), 1e-3f);
        w.rotateEntity(e.id(), 30, 0);
        assertEquals(30f, e.renderHeadYaw(1f), 1e-4f, "몸을 돌리면 머리가 다시 따른다");
    }

    @Test
    void pausedClockShowsLatestTick() {
        McvWorld w = fresh();
        w.setTickRate(0);
        assertEquals(1f, w.entityPartial(), 0f);
    }
}
