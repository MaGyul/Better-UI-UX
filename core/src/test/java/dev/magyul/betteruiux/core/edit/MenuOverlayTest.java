package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.edit.Geometry.Rect;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 드롭다운 메뉴: 열기/그리기/고르기/바깥 눌러 닫기, 화면 가장자리에서 위치 보정. */
class MenuOverlayTest {
    private static final int W = 300, H = 200, ROW = 11;
    private final FixedMetrics tm = new FixedMetrics();

    private List<Region> paint(MenuOverlay m) {
        List<Region> regions = new ArrayList<>();
        m.paint(new DrawList(), tm, W, H, ROW, 0, 0, regions);
        return regions;
    }

    @Test
    void closedMenuDrawsNothing() {
        MenuOverlay m = new MenuOverlay();
        assertFalse(m.isOpen());
        assertTrue(paint(m).isEmpty());
    }

    @Test
    void choosingAnItemCallsBackAndCloses() {
        MenuOverlay m = new MenuOverlay();
        List<String> picked = new ArrayList<>();
        m.open(new Rect(10, 0, 40, 16), List.of("a", "bb", "ccc"), picked::add);
        assertTrue(m.isOpen());
        List<Region> regions = paint(m);
        // 바깥 영역 1 + 메뉴 상자 1 + 항목 3
        assertEquals(5, regions.size());
        regions.get(3).action().run();                                    // 두 번째 항목
        assertEquals(List.of("bb"), picked);
        assertFalse(m.isOpen());
    }

    @Test
    void clickingOutsideClosesWithoutPicking() {
        MenuOverlay m = new MenuOverlay();
        List<String> picked = new ArrayList<>();
        m.open(new Rect(10, 0, 40, 16), List.of("a"), picked::add);
        paint(m).get(0).action().run();
        assertFalse(m.isOpen());
        assertTrue(picked.isEmpty());
    }

    @Test
    void staysInsideTheViewNearTheEdges() {
        MenuOverlay m = new MenuOverlay();
        m.open(new Rect(W - 5, H - 14, 20, 12), List.of("one", "two", "three"), s -> { });
        for (Region r : paint(m)) {
            if (r.rect().w() >= W) continue;                              // 바깥 전체 영역은 제외
            assertTrue(r.rect().x() >= 0 && r.rect().right() <= W, "가로로 화면 안: " + r.rect());
            assertTrue(r.rect().y() >= 0 && r.rect().bottom() <= H, "세로로 화면 안: " + r.rect());
        }
    }
}
