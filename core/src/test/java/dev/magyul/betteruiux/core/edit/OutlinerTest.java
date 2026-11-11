package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 구조 트리: 행 만들기, 접기/펼치기, 검색, 선택 요소 드러내기. */
class OutlinerTest {
    private static final String XML = """
            <screen>
              <panel id="p" width="100" height="80">
                <column id="c">
                  <label id="l1" text="Hello"/>
                  <button id="b1" text="World"/>
                </column>
              </panel>
              <label id="solo" text="Alone"/>
            </screen>
            """;

    private final Outliner outliner = new Outliner();
    private LayoutBox root;

    private List<Outliner.Row> rows(String active) throws Exception {
        UiDocument d = UiDocument.parse(XML, new FixedMetrics());
        d.layout(400, 300);
        root = d.layout();
        return outliner.rows(root, active, EditorControllerLabels::label);
    }

    private static List<String> keys(List<Outliner.Row> rows) { return rows.stream().map(Outliner.Row::key).toList(); }

    @Test
    void listsEveryElementWithItsDepth() throws Exception {
        List<Outliner.Row> rows = rows("");
        assertEquals(List.of("r", "#p", "#c", "#l1", "#b1", "#solo"), keys(rows));
        assertEquals(List.of(0, 1, 2, 3, 3, 1), rows.stream().map(Outliner.Row::depth).toList());
        assertTrue(rows.get(1).hasKids());
        assertFalse(rows.get(3).hasKids());
    }

    @Test
    void collapsingHidesTheSubtree() throws Exception {
        outliner.toggle("#c");
        assertEquals(List.of("r", "#p", "#c", "#solo"), keys(rows("")));
        assertTrue(rows("").get(2).collapsed());
        outliner.toggle("#c");
        assertEquals(6, rows("").size());
    }

    @Test
    void collapseAllKeepsTheRootOpenAndExpandAllReopens() throws Exception {
        rows("");
        outliner.scroll = 5;
        outliner.collapseAll(root, "", EditorControllerLabels::label);
        assertEquals(0, outliner.scroll);
        assertEquals(List.of("r", "#p", "#solo"), keys(rows("")).stream().filter(k -> !k.equals("#c")).toList().subList(0, 3));
        outliner.expandAll();
        assertEquals(6, rows("").size());
    }

    @Test
    void filterKeepsMatchesAndTheirAncestors() throws Exception {
        assertEquals(List.of("r", "#p", "#c", "#b1"), keys(rows("world")));
        assertEquals(List.of(), keys(rows("nothing-matches")));
    }

    @Test
    void revealOpensCollapsedAncestorsAndScrolls() throws Exception {
        outliner.toggle("#p");
        List<Outliner.Row> before = rows("");
        assertEquals(List.of("r", "#p", "#solo"), keys(before));
        LayoutBox b1 = findById(root, "b1");
        outliner.reveal(before, b1, b1.node.sourceIndex(), 2, root, "", EditorControllerLabels::label);
        assertEquals(6, rows("").size(), "조상이 펼쳐져야 한다");
        assertEquals(3, outliner.scroll, "보이는 줄 2개 기준으로 선택 줄(4번째)이 보이도록 스크롤");
    }

    private static LayoutBox findById(LayoutBox b, String id) {
        if (id.equals(b.id())) return b;
        for (LayoutBox c : b.children) {
            LayoutBox r = findById(c, id);
            if (r != null) return r;
        }
        return null;
    }

    /** 컨트롤러의 행 라벨과 같은 규칙(태그 + 아이디 + 글자)을 흉내 낸다. */
    static final class EditorControllerLabels {
        static String label(LayoutBox b) {
            return b.tag() + (b.id() != null ? "#" + b.id() : "") + " " + (b.node.attr("text") == null ? "" : b.node.attr("text"));
        }
    }
}
