package dev.magyul.betteruiux.core.edit;

import dev.magyul.betteruiux.api.TextMetrics;
import java.util.ArrayList;
import java.util.List;

/**
 * 도구 모음 버튼 배치(그리기와 상관없는 계산): 줄에 다 들어가면 모두 놓고, 모자라면 들어가는 데까지만 놓은 뒤
 * 나머지는 "..." 메뉴로 보낸다("..." 자리를 미리 남겨 두고, 한 번 숨기기 시작하면 뒤의 버튼은 모두 숨긴다).
 */
final class ToolbarLayout {
    private ToolbarLayout() {}

    /** @param gap 이 버튼 앞의 추가 간격 */
    record Item(String label, int gap) {}

    /** 놓인 버튼: 목록의 {@code index} 번째가 x 에서 시작해 너비 w. */
    record Placed(int index, int x, int w) {}

    /**
     * @param placed   그려질 버튼들
     * @param hidden   "..." 메뉴에 들어간 버튼 번호들(없으면 "..." 도 없다)
     * @param endX     마지막으로 놓은 버튼 다음 x(여기에 "..." 가 놓인다)
     * @param overflowW "..." 버튼 너비
     */
    record Result(List<Placed> placed, List<Integer> hidden, int endX, int overflowW) {}

    /** 버튼 하나의 너비(글자 + 좌우 여백). */
    static int widthOf(TextMetrics tm, String label) { return tm.width(label) + 8; }

    /** @param limit 버튼을 놓을 수 있는 오른쪽 끝 x(Exit 버튼 앞) */
    static Result layout(List<Item> items, TextMetrics tm, int limit) {
        int total = 2;
        for (Item it : items) total += it.gap() + widthOf(tm, it.label()) + 2;
        boolean overflow = total - 2 > limit;
        int ovW = widthOf(tm, "...");
        int x = 2;
        List<Placed> placed = new ArrayList<>();
        List<Integer> hidden = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            int w = widthOf(tm, it.label());
            if (!hidden.isEmpty() || (overflow && x + it.gap() + w + 2 + ovW > limit)) {
                hidden.add(i);
                continue;
            }
            x += it.gap();
            placed.add(new Placed(i, x, w));
            x += w + 2;
        }
        return new Result(placed, hidden, x, ovW);
    }
}
