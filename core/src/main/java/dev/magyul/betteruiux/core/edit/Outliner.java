package dev.magyul.betteruiux.core.edit;

import static dev.magyul.betteruiux.core.edit.LayoutQueries.displayChildren;
import static dev.magyul.betteruiux.core.edit.LayoutQueries.displayParent;

import dev.magyul.betteruiux.core.layout.LayoutBox;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 왼쪽 "구조" 트리의 상태와 행 만들기: 접힌 행, 검색어, 스크롤 위치. 화면 그리기/입력은 컨트롤러가 맡고,
 * 여기서는 문서의 상자 트리를 어떤 행들로 보여 줄지만 정한다.
 */
final class Outliner {
    /** 트리 한 줄. {@code key} 는 접힘 상태를 기억하는 데 쓰는 요소 식별자(id 가 있으면 #id, 없으면 경로). */
    record Row(LayoutBox box, int depth, boolean hasKids, boolean collapsed, String key) {}

    /** 검색 칸을 편집할 때 쓰는 칸 이름. */
    static final String FILTER_KEY = "§filter";

    final Set<String> collapsed = new HashSet<>();
    /** 확정된 검색어(편집 중인 글자는 컨트롤러가 {@code active} 로 넘긴다). */
    String filter = "";
    int scroll;

    static String keyOf(LayoutBox b) {
        if (b.id() != null) return "#" + b.id();
        StringBuilder path = new StringBuilder();
        for (LayoutBox x = b; displayParent(x) != null; x = displayParent(x)) {
            path.insert(0, "/" + displayChildren(displayParent(x)).indexOf(x));
        }
        return "r" + path;
    }

    /** {@code active} 가 비어 있지 않으면 그 글자가 들어 있는 줄과 그 조상만(모두 펼친 채로), 아니면 접힘을 반영한 전체. */
    List<Row> rows(LayoutBox root, String active, Function<LayoutBox, String> label) {
        List<Row> out = new ArrayList<>();
        build(root, 0, out, active, label);
        return out;
    }

    private boolean subtreeMatches(LayoutBox b, String f, Function<LayoutBox, String> label) {
        if (label.apply(b).toLowerCase().contains(f)) return true;
        for (LayoutBox k : displayChildren(b)) if (subtreeMatches(k, f, label)) return true;
        return false;
    }

    private void build(LayoutBox b, int depth, List<Row> out, String f, Function<LayoutBox, String> label) {
        List<LayoutBox> kids = displayChildren(b);
        String key = keyOf(b);
        if (!f.isEmpty()) {
            if (!subtreeMatches(b, f, label)) return;
            out.add(new Row(b, depth, !kids.isEmpty(), false, key));
            for (LayoutBox k : kids) build(k, depth + 1, out, f, label);
            return;
        }
        boolean isCollapsed = !kids.isEmpty() && collapsed.contains(key);
        out.add(new Row(b, depth, !kids.isEmpty(), isCollapsed, key));
        if (!isCollapsed) for (LayoutBox k : kids) build(k, depth + 1, out, f, label);
    }

    void toggle(String key) {
        if (!collapsed.remove(key)) collapsed.add(key);
    }

    /** 자식이 있는 모든 줄(루트 제외)을 접는다. */
    void collapseAll(LayoutBox root, String active, Function<LayoutBox, String> label) {
        for (Row r : rows(root, active, label)) {
            if (r.hasKids() && r.box().parent != null) collapsed.add(r.key());
        }
        scroll = 0;
    }

    void expandAll() { collapsed.clear(); }

    /**
     * 선택한 요소가 보이도록 조상 줄을 펼치고 스크롤한다.
     *
     * @param rows 지금 그려지는 줄들(조상을 펼치지 않았으면 이것을 그대로 쓴다)
     */
    void reveal(List<Row> rows, LayoutBox selectedBox, int selectedIndex, int visibleRows, LayoutBox root, String active,
                Function<LayoutBox, String> label) {
        if (selectedBox == null) return;
        boolean changed = false;
        for (LayoutBox a = displayParent(selectedBox); a != null; a = displayParent(a)) changed |= collapsed.remove(keyOf(a));
        List<Row> now = changed ? rows(root, active, label) : rows;
        for (int i = 0; i < now.size(); i++) {
            if (now.get(i).box().node.sourceIndex() == selectedIndex) {
                if (i < scroll) scroll = i;
                else if (i >= scroll + visibleRows) scroll = i - visibleRows + 1;
                break;
            }
        }
    }
}
