package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 에디터의 규칙 편집이 CSS 중첩 규칙({@code a { &:hover { … } }})을 읽고 그 자리에서 고친다. */
class EditorNestedStyleTest {
    private static final String XML = "<screen><style>.card { padding: 8; &:hover { background: #FF0000; } .title { color: #00FF00; } }</style>"
            + "<panel id='c' class='card' width='80' height='40'><label class='title' id='t' text='x'/></panel></screen>";

    @Test
    void scanFindsNestedRulesWithCombinedSelectors() {
        List<StyleText.Rule> rules = StyleText.rules(XmlSource.parse(XML));
        assertEquals(List.of(".card", ".card:hover", ".card .title"), rules.stream().map(StyleText.Rule::selector).toList());
        assertEquals("padding", rules.get(0).decls().get(0).prop());
        assertEquals(1, rules.get(0).decls().size(), "부모 규칙에는 부모의 선언만");
        assertEquals("background", rules.get(1).decls().get(0).prop());
    }

    @Test
    void editingANestedValueChangesItInPlace() throws Exception {
        XmlSource src = XmlSource.parse(XML);
        List<StyleText.Rule> rules = StyleText.rules(src);
        int hover = 1;
        String next = StyleText.apply(src, StyleText.KEY + hover + ":background", "#0000FF");
        assertTrue(next.contains("&:hover { background: #0000FF; }"), next);
        assertTrue(next.contains(".card { padding: 8;"), "다른 규칙은 그대로");
        UiDocument.parse(next, new FixedMetrics());                      // 결과는 여전히 올바른 문서
    }

    @Test
    void addingADeclarationGoesInsideTheNestedBlock() {
        XmlSource src = XmlSource.parse(XML);
        String next = StyleText.apply(src, StyleText.ADD_KEY + 1, "opacity: 0.5");
        int hoverBlock = next.indexOf("&:hover {");
        int close = next.indexOf('}', hoverBlock);
        assertTrue(next.substring(hoverBlock, close).contains("opacity: 0.5"), next);
    }

    @Test
    void matchingUsesTheCombinedSelector() throws Exception {
        UiDocument d = UiDocument.parse(XML, new FixedMetrics());
        d.layout(200, 200);
        XmlSource src = XmlSource.parse(XML);
        assertEquals(List.of(0, 1), StyleText.matching(src, d.findById("c")), ".card 와 .card:hover");
        assertEquals(List.of(2), StyleText.matching(src, d.findById("t")), ".card .title");
    }
}
