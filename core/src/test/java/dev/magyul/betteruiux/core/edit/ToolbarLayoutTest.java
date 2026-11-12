package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 도구 모음 배치: 다 들어가면 모두 놓고, 좁으면 "..." 로 넘긴다. */
class ToolbarLayoutTest {
    private final FixedMetrics tm = new FixedMetrics();
    private static final List<ToolbarLayout.Item> ITEMS = List.of(
            new ToolbarLayout.Item("File", 0), new ToolbarLayout.Item("Save", 0), new ToolbarLayout.Item("Dup", 4),
            new ToolbarLayout.Item("Del", 0), new ToolbarLayout.Item("View", 4));

    @Test
    void everythingFitsWhenThereIsRoom() {
        ToolbarLayout.Result r = ToolbarLayout.layout(ITEMS, tm, 1000);
        assertEquals(5, r.placed().size());
        assertTrue(r.hidden().isEmpty());
        assertEquals(2, r.placed().get(0).x(), "첫 버튼은 왼쪽 여백 2");
        // 앞 버튼 끝 + 2 + 추가 간격 = 다음 버튼 시작
        for (int i = 1; i < r.placed().size(); i++) {
            ToolbarLayout.Placed prev = r.placed().get(i - 1), cur = r.placed().get(i);
            assertEquals(prev.x() + prev.w() + 2 + ITEMS.get(i).gap(), cur.x());
        }
        ToolbarLayout.Placed last = r.placed().get(4);
        assertEquals(last.x() + last.w() + 2, r.endX());
    }

    @Test
    void narrowBarMovesTheTailIntoTheOverflowMenu() {
        int w = ToolbarLayout.widthOf(tm, "File") + 2 + ToolbarLayout.widthOf(tm, "Save") + 2;   // 두 개 + 여유
        ToolbarLayout.Result r = ToolbarLayout.layout(ITEMS, tm, 2 + w + ToolbarLayout.widthOf(tm, "...") + 2);
        assertTrue(r.placed().size() < 5, "일부는 숨는다");
        assertEquals(5, r.placed().size() + r.hidden().size());
        assertTrue(r.hidden().get(0) >= 1, "앞쪽 버튼이 먼저 남는다");
        // 한 번 숨기기 시작하면 뒤는 모두 숨는다(연속)
        for (int i = 1; i < r.hidden().size(); i++) assertEquals(r.hidden().get(i - 1) + 1, r.hidden().get(i));
        // 놓인 버튼과 "..." 가 한계를 넘지 않는다
        assertTrue(r.endX() + r.overflowW() <= 2 + w + ToolbarLayout.widthOf(tm, "...") + 2);
    }

    @Test
    void nothingFitsMeansEverythingIsHidden() {
        ToolbarLayout.Result r = ToolbarLayout.layout(ITEMS, tm, 5);
        assertTrue(r.placed().isEmpty());
        assertEquals(List.of(0, 1, 2, 3, 4), r.hidden());
        assertEquals(2, r.endX());
    }
}
