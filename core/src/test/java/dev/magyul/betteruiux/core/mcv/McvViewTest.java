package dev.magyul.betteruiux.core.mcv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 화면 ↔ 월드 변환과 블록 맞히기. 카메라 수식은 렌더러와 같아야 하므로, 투영한 점을 다시 쏘면 같은 블록이 나와야 한다. */
class McvViewTest {
    private final McvViewer v = new McvViewer("v");                  // 테스트 하나당 뷰어 하나(JUnit 이 테스트마다 새 인스턴스를 만든다)

    private McvWorld world() {
        McvWorld w = new McvWorld(6, 4, 6);
        v.setViewport(new McvViewer.Viewport(100, 50, 240, 180, 20));
        return w;
    }

    private static McvBlock stone() { return McvBlock.parse("stone", null); }

    @Test
    void worldCenterProjectsToTheViewportCenter() {
        McvWorld w = world();
        double[] p = McvView.project(w, v, 3, 2, 3);
        assertEquals(100 + 120, p[0], 1e-9);
        assertEquals(50 + 90, p[1], 1e-9);
    }

    @Test
    void higherPointsAppearHigherOnScreenAndTheCameraSideIsNearer() {
        McvWorld w = world();                                          // 기본 카메라: yaw 225, pitch 30 (위에서 내려다봄)
        double[] low = McvView.project(w, v, 3, 1, 3), high = McvView.project(w, v, 3, 3, 3);
        assertTrue(high[1] < low[1], "위쪽 점이 화면 위(y 작음)");
        assertTrue(high[2] < low[2], "위에서 내려다보므로 위쪽 점이 더 가깝다(깊이 작음)");
    }

    @Test
    void projectingThenCastingFindsTheSameBlockFromEveryAngle() {
        for (float yaw : new float[] {0, 45, 135, 225, 300}) {
            for (float pitch : new float[] {10, 30, 60, 89}) {
                McvWorld w = world();
                v.camera().setYaw(yaw);
                v.camera().setPitch(pitch);
                w.setBlock(2, 1, 3, stone());
                double[] s = McvView.project(w, v, 2.5, 1.5, 3.5);
                McvView.Hit hit = McvView.raycast(w, v, s[0], s[1]);
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
        v.camera().setYaw(0);
        v.camera().setPitch(0);                                        // 수평: 같은 x,y 에서 z 만 다른 두 블록이 한 점에 겹친다
        w.setBlock(2, 1, 1, stone());
        w.setBlock(2, 1, 4, stone());
        double[] s = McvView.project(w, v, 2.5, 1.5, 2.5);
        McvView.Hit hit = McvView.raycast(w, v, s[0], s[1]);
        assertNotNull(hit);
        double[] nearer = McvView.project(w, v, 2.5, 1.5, hit.z() + 0.5);
        double[] other = McvView.project(w, v, 2.5, 1.5, hit.z() == 1 ? 4.5 : 1.5);
        assertTrue(nearer[2] < other[2], "맞은 블록이 다른 블록보다 가까워야 한다");
        assertNull(McvView.raycast(w, v, 101, 51), "아무것도 없는 곳");
    }

    @Test
    void topFaceIsReportedWhenLookingDown() {
        McvWorld w = world();
        v.camera().setPitch(89);
        w.setBlock(2, 1, 2, stone());
        double[] s = McvView.project(w, v, 2.5, 1.9, 2.5);
        McvView.Hit hit = McvView.raycast(w, v, s[0], s[1]);
        assertNotNull(hit);
        assertEquals("up", hit.face());
    }

    @Test
    void blocksOutsideTheViewAreaAreSkippedAndNoViewportMeansNoHits() {
        McvWorld w = world();
        w.setBlock(0, 0, 0, stone());
        w.setViewArea(3, 0, 3, 5, 3, 5);
        double[] s = McvView.project(w, v, 0.5, 0.5, 0.5);
        assertNull(McvView.raycast(w, v, s[0], s[1]), "보기 영역 밖 블록은 그려지지 않으니 맞지도 않는다");
        McvWorld bare = new McvWorld(2, 2, 2);
        assertNull(McvView.project(bare, new McvViewer("b"), 1, 1, 1));
        assertNull(McvView.raycast(bare, new McvViewer("b"), 10, 10));
    }

    // ---- 엔티티 맞히기 ----

    @Test
    void entitiesAreOnlyPickedOnceTheirBoundsAreKnown() {
        McvWorld w = world();
        McvEntity cow = w.spawnEntity(3, 1, 3, "cow");
        double[] s = McvView.project(w, v, 3, 1.5, 3);
        assertNull(McvView.raycast(w, v, s[0], s[1]), "크기를 모르는(그려지지 않은) 엔티티는 맞지 않는다");
        cow.setBounds(0.9, 1.4);
        McvView.Hit hit = McvView.raycast(w, v, s[0], s[1]);
        assertNotNull(hit);
        assertTrue(hit.isEntity());
        assertEquals(cow.id(), hit.entity().id());
        assertNull(hit.block());
    }

    @Test
    void theNearerOfEntityAndBlockWins() {
        McvWorld w = world();
        v.camera().setYaw(0);
        v.camera().setPitch(0);                                        // 수평으로 본다: 깊이는 z 방향
        w.setBlock(3, 1, 1, stone());
        McvEntity cow = w.spawnEntity(3.5, 1, 4.5, "cow");
        cow.setBounds(0.9, 1.4);
        double[] s = McvView.project(w, v, 3.5, 1.5, 3);
        McvView.Hit hit = McvView.raycast(w, v, s[0], s[1]);
        assertNotNull(hit);
        double[] blockDepth = McvView.project(w, v, 3.5, 1.5, 1.5), cowDepth = McvView.project(w, v, 3.5, 1.5, 4.5);
        boolean blockNearer = blockDepth[2] < cowDepth[2];
        assertEquals(blockNearer, hit.isBlock(), "더 가까운 쪽이 맞아야 한다");
        cow.setBounds(0.9, 1.4);
        w.setBlock(3, 1, 1, McvBlock.AIR);
        assertTrue(McvView.raycast(w, v, s[0], s[1]).isEntity(), "블록을 치우면 엔티티가 맞는다");
    }

    @Test
    void entityPickingRespectsTheViewArea() {
        McvWorld w = world();
        McvEntity cow = w.spawnEntity(1, 1, 1, "cow");
        cow.setBounds(0.9, 1.4);
        double[] s = McvView.project(w, v, 1, 1.5, 1);
        assertNotNull(McvView.raycast(w, v, s[0], s[1]));
        w.setViewArea(3, 0, 3, 5, 3, 5);
        double[] s2 = McvView.project(w, v, 1, 1.5, 1);
        assertNull(McvView.raycast(w, v, s2[0], s2[1]), "보기 영역 밖 엔티티는 그려지지 않으니 맞지도 않는다");
    }

    // ---- 뷰어마다 카메라/뷰포트가 다르다 ----

    @Test
    void twoViewersOfOneWorldProjectAndPickIndependently() {
        McvWorld w = new McvWorld(6, 4, 6);
        w.setBlock(2, 1, 3, stone());
        McvViewer a = new McvViewer("a"), b = new McvViewer("b");
        a.setViewport(new McvViewer.Viewport(0, 0, 200, 150, 20));
        b.setViewport(new McvViewer.Viewport(300, 40, 100, 100, 10));       // 위치와 배율이 다르다
        b.camera().setYaw(45);
        b.camera().setPitch(80);                                            // 거의 위에서 내려다본다
        double[] pa = McvView.project(w, a, 2.5, 1.5, 3.5), pb = McvView.project(w, b, 2.5, 1.5, 3.5);
        assertTrue(Math.abs(pa[0] - pb[0]) > 50 || Math.abs(pa[1] - pb[1]) > 50, "뷰어마다 화면 위치가 다르다");
        McvView.Hit ha = McvView.raycast(w, a, pa[0], pa[1]), hb = McvView.raycast(w, b, pb[0], pb[1]);
        assertNotNull(ha);
        assertNotNull(hb);
        assertEquals(2, ha.x());
        assertEquals(2, hb.x());
        assertEquals("up", hb.face(), "위에서 보는 뷰어는 윗면을 맞힌다");
        McvView.Hit cross = McvView.raycast(w, b, pa[0], pa[1]);              // a 의 화면 좌표를 b 의 카메라로 쏘면 같은 블록이 나올 이유가 없다
        assertTrue(cross == null || cross.x() != 2 || cross.z() != 3, "화면 좌표는 뷰어마다 따로 해석된다");
    }

    @Test
    void viewerAdoptsTheWorldDefaultCameraOnlyOnTheFirstShow() {
        McvWorld a = new McvWorld(2, 2, 2), b = new McvWorld(2, 2, 2);
        a.camera().setYaw(90);                                              // 뷰어가 붙기 전에 월드의 기본 카메라를 설정
        McvViewer viewer = new McvViewer("x");
        viewer.show(a);
        assertEquals(90f, viewer.camera().yaw(), "처음 보여 줄 때 이어받는다");
        viewer.camera().setYaw(10);
        viewer.show(b);                                                     // 만지지 않은 월드로 옮기면 자기 카메라를 그대로 쓴다
        assertEquals(10f, viewer.camera().yaw());
        viewer.show(a);                                                     // 다시 붙어도 덮어쓰지 않는다
        assertEquals(10f, viewer.camera().yaw());
    }
}
