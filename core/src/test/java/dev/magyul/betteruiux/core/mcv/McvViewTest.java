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

    // ---- 원근(perspective) ----

    private McvWorld perspectiveWorld() {
        McvWorld w = world();
        v.camera().setProjection(McvCamera.Projection.PERSPECTIVE);
        return w;
    }

    @Test
    void perspectiveOrbitCameraKeepsTheWorldCenterAtTheViewportCenter() {
        McvWorld w = perspectiveWorld();
        double[] p = McvView.project(w, v, 3, 2, 3);
        assertEquals(100 + 120, p[0], 1e-6);
        assertEquals(50 + 90, p[1], 1e-6);
        assertTrue(p[2] > 0, "앞에 있다(깊이 > 0)");
    }

    @Test
    void perspectiveMakesNearThingsLargerAndFarThingsSmaller() {
        McvWorld w = perspectiveWorld();
        v.camera().setYaw(0);
        v.camera().setPitch(0);                                         // 수평: 보는 방향은 +z
        double[] e = McvView.eye(w, v);
        assertTrue(e[2] < 0, "눈은 월드 앞(−z 쪽)에 있다: " + e[2]);
        double[] nearA = McvView.project(w, v, 4.5, 2, 0.5), nearB = McvView.project(w, v, 1.5, 2, 0.5);    // 가까운 두 점(같은 z)
        double[] farA = McvView.project(w, v, 4.5, 2, 5.5), farB = McvView.project(w, v, 1.5, 2, 5.5);      // 먼 두 점
        assertTrue(Math.abs(nearA[0] - nearB[0]) > Math.abs(farA[0] - farB[0]) + 1, "같은 간격이라도 가까울수록 화면에서 더 벌어진다");
    }

    @Test
    void perspectiveProjectThenRaycastFindsTheSameBlock() {
        for (float yaw : new float[] {0, 45, 135, 225, 300}) {
            for (float pitch : new float[] {10, 30, 60}) {
                McvWorld w = perspectiveWorld();
                v.camera().setYaw(yaw);
                v.camera().setPitch(pitch);
                w.setBlock(2, 1, 3, stone());
                double[] s = McvView.project(w, v, 2.5, 1.5, 3.5);
                assertNotNull(s, "yaw " + yaw + " pitch " + pitch);
                McvView.Hit hit = McvView.raycast(w, v, s[0], s[1]);
                assertNotNull(hit, "yaw " + yaw + " pitch " + pitch);
                assertEquals(2, hit.x());
                assertEquals(1, hit.y());
                assertEquals(3, hit.z());
            }
        }
    }

    @Test
    void perspectivePositionedCameraLooksAlongYawAndPitchFromItsPosition() {
        McvWorld w = perspectiveWorld();
        v.camera().setPosition(2.5, 1.5, -5);
        v.camera().setYaw(0);
        v.camera().setPitch(0);                                         // +z 를 본다
        double[] ahead = McvView.project(w, v, 2.5, 1.5, 3);
        assertEquals(220, ahead[0], 1e-6, "정면의 점은 화면 가운데");
        assertEquals(140, ahead[1], 1e-6);
        assertNull(McvView.project(w, v, 2.5, 1.5, -8), "카메라 뒤의 점은 화면에 없다(null)");
        double[] left = McvView.project(w, v, 3.5, 1.5, 3), right = McvView.project(w, v, 1.5, 1.5, 3);
        assertTrue(right[0] > ahead[0] && left[0] < ahead[0], "월드 −x 쪽이 화면 오른쪽(정사영과 같은 방향 규칙)");
        double[] up = McvView.project(w, v, 2.5, 2.5, 3);
        assertTrue(up[1] < ahead[1], "위쪽 점은 화면 위");
    }

    @Test
    void perspectiveZoomMovesTheOrbitCameraCloser() {
        McvWorld w = perspectiveWorld();
        double[] far = McvView.eye(w, v);
        v.camera().setZoom(2f);
        double[] near = McvView.eye(w, v);
        double[] c = McvView.center(w);
        double dFar = Math.sqrt(Math.pow(far[0] - c[0], 2) + Math.pow(far[1] - c[1], 2) + Math.pow(far[2] - c[2], 2));
        double dNear = Math.sqrt(Math.pow(near[0] - c[0], 2) + Math.pow(near[1] - c[1], 2) + Math.pow(near[2] - c[2], 2));
        assertEquals(dFar / 2, dNear, 1e-6, "zoom 2 = 거리 절반");
    }

    @Test
    void perspectivePicksEntitiesAndTheNearerOfEntityAndBlock() {
        McvWorld w = perspectiveWorld();
        McvEntity cow = w.spawnEntity(3, 1, 3, "cow");
        cow.setBounds(0.9, 1.4);
        double[] s = McvView.project(w, v, 3, 1.5, 3);
        McvView.Hit hit = McvView.raycast(w, v, s[0], s[1]);
        assertNotNull(hit);
        assertTrue(hit.isEntity());
        w.setBlock(3, 1, 3, stone());                                   // 같은 자리에 블록: 소가 블록 안에 있으니 가까운 쪽(눈에서 먼저 닿는 면)이 이긴다
        assertNotNull(McvView.raycast(w, v, s[0], s[1]));
    }
}
