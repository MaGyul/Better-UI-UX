package dev.magyul.betteruiux.core.mcv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 화면 ↔ 월드 변환과 블록 맞히기. 카메라 수식은 렌더러와 같아야 하므로, 투영한 점을 다시 쏘면 같은 블록이 나와야 한다. */
class McvViewTest {
    private static McvWorld world() {
        McvWorld w = new McvWorld(6, 4, 6);
        w.setViewport(new McvWorld.Viewport(100, 50, 240, 180, 20));
        return w;
    }

    private static McvBlock stone() { return McvBlock.parse("stone", null); }

    @Test
    void worldCenterProjectsToTheViewportCenter() {
        McvWorld w = world();
        double[] p = McvView.project(w, 3, 2, 3);
        assertEquals(100 + 120, p[0], 1e-9);
        assertEquals(50 + 90, p[1], 1e-9);
    }

    @Test
    void higherPointsAppearHigherOnScreenAndTheCameraSideIsNearer() {
        McvWorld w = world();                                          // 기본 카메라: yaw 225, pitch 30 (위에서 내려다봄)
        double[] low = McvView.project(w, 3, 1, 3), high = McvView.project(w, 3, 3, 3);
        assertTrue(high[1] < low[1], "위쪽 점이 화면 위(y 작음)");
        assertTrue(high[2] < low[2], "위에서 내려다보므로 위쪽 점이 더 가깝다(깊이 작음)");
    }

    @Test
    void projectingThenCastingFindsTheSameBlockFromEveryAngle() {
        for (float yaw : new float[] {0, 45, 135, 225, 300}) {
            for (float pitch : new float[] {10, 30, 60, 89}) {
                McvWorld w = world();
                w.camera().setYaw(yaw);
                w.camera().setPitch(pitch);
                w.setBlock(2, 1, 3, stone());
                double[] s = McvView.project(w, 2.5, 1.5, 3.5);
                McvView.Hit hit = McvView.raycast(w, s[0], s[1]);
                assertNotNull(hit, "yaw " + yaw + " pitch " + pitch);
                assertEquals(2, hit.x());
                assertEquals(1, hit.y());
                assertEquals(3, hit.z());
            }
        }
    }

    @Test
    void theNearerBlockWinsAndMissesReturnNull() {
        McvWorld w = world();
        w.camera().setYaw(0);
        w.camera().setPitch(0);                                        // 수평: 같은 x,y 에서 z 만 다른 두 블록이 한 점에 겹친다
        w.setBlock(2, 1, 1, stone());
        w.setBlock(2, 1, 4, stone());
        double[] s = McvView.project(w, 2.5, 1.5, 2.5);
        McvView.Hit hit = McvView.raycast(w, s[0], s[1]);
        assertNotNull(hit);
        double[] nearer = McvView.project(w, 2.5, 1.5, hit.z() + 0.5);
        double[] other = McvView.project(w, 2.5, 1.5, hit.z() == 1 ? 4.5 : 1.5);
        assertTrue(nearer[2] < other[2], "맞은 블록이 다른 블록보다 가까워야 한다");
        assertNull(McvView.raycast(w, 101, 51), "아무것도 없는 곳");
    }

    @Test
    void topFaceIsReportedWhenLookingDown() {
        McvWorld w = world();
        w.camera().setPitch(89);
        w.setBlock(2, 1, 2, stone());
        double[] s = McvView.project(w, 2.5, 1.9, 2.5);
        McvView.Hit hit = McvView.raycast(w, s[0], s[1]);
        assertNotNull(hit);
        assertEquals("up", hit.face());
    }

    @Test
    void blocksOutsideTheViewAreaAreSkippedAndNoViewportMeansNoHits() {
        McvWorld w = world();
        w.setBlock(0, 0, 0, stone());
        w.setViewArea(3, 0, 3, 5, 3, 5);
        double[] s = McvView.project(w, 0.5, 0.5, 0.5);
        assertNull(McvView.raycast(w, s[0], s[1]), "보기 영역 밖 블록은 그려지지 않으니 맞지도 않는다");
        McvWorld bare = new McvWorld(2, 2, 2);
        assertNull(McvView.project(bare, 1, 1, 1));
        assertNull(McvView.raycast(bare, 10, 10));
    }
}
