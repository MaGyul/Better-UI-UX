package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 에디터의 규칙 편집이 CSS 중첩 규칙({@code a { &:hover { … } }})을 읽고, 그 자리에서 고치고, 새로 만든다. */
class EditorNestedStyleTest {
    private static final String XML = "<screen><style>.card { padding: 8; &:hover { background: #FF0000; } .title { color: #00FF00; } }</style>"
            + "<panel id='c' class='card' width='80' height='40'><label class='title' id='t' text='x'/></panel></screen>";

    private static List<String> selectors(String xml) {
        return StyleText.rules(XmlSource.parse(xml)).stream().map(StyleText.Rule::selector).toList();
    }

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
        String next = StyleText.apply(src, StyleText.KEY + 1 + ":background", "#0000FF");
        assertTrue(next.contains("&:hover { background: #0000FF; }"), next);
        assertTrue(next.contains(".card { padding: 8;"), "다른 규칙은 그대로");
        UiDocument.parse(next, new FixedMetrics());                      // 결과는 여전히 올바른 문서
    }

    @Test
    void addingADeclarationGoesInsideTheNestedBlock() {
        String next = StyleText.apply(XmlSource.parse(XML), StyleText.ADD_KEY + 1, "opacity: 0.5");
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

    @Test
    void nestedRuleCanBeCreatedInsideAnInlineRule() throws Exception {
        String xml = "<screen><style>.card { padding: 8; }</style><panel class='card' id='c'/></screen>";
        String next = StyleText.apply(XmlSource.parse(xml), StyleText.NEST_KEY + 0, "&:hover");
        assertTrue(next.contains(".card { padding: 8; &:hover { } }"), next);
        assertEquals(List.of(".card", ".card:hover"), selectors(next));
        UiDocument d = UiDocument.parse(next, new FixedMetrics());
        assertTrue(d.warnings().isEmpty(), d.warnings().toString());
    }

    @Test
    void nestedRuleInAMultiLineRuleKeepsFormatting() {
        String xml = "<screen>\n  <style>\n    .card {\n      padding: 8;\n    }\n  </style>\n  <panel class='card'/>\n</screen>";
        String next = StyleText.apply(XmlSource.parse(xml), StyleText.NEST_KEY + 0, ".title { color: #FF0000; }");
        assertTrue(next.contains("      padding: 8;\n      .title { color: #FF0000; }\n    }"), next);
        assertEquals(List.of(".card", ".card .title"), selectors(next));
    }

    @Test
    void nestedRuleAfterADeclarationWithoutSemicolonGetsOne() {
        String next = StyleText.apply(XmlSource.parse("<screen><style>a { color: red }</style><panel/></screen>"), StyleText.NEST_KEY + 0, "&:hover");
        assertTrue(next.contains("a { color: red; &:hover { } }"), next);
    }

    @Test
    void invalidNestedSelectorIsRejectedAndEmptyInputChangesNothing() {
        XmlSource src = XmlSource.parse("<screen><style>a { color: red; }</style><panel/></screen>");
        assertThrows(IllegalArgumentException.class, () -> StyleText.apply(src, StyleText.NEST_KEY + 0, "&:bogus-pseudo("));
        assertThrows(IllegalArgumentException.class, () -> StyleText.apply(src, StyleText.NEST_KEY + 0, "{ color: red }"));
        assertEquals(src.text(), StyleText.apply(src, StyleText.NEST_KEY + 0, "  "));
    }

    @Test
    void externalSheetNestedRule() {
        String next = StyleText.applyExternal(".card { padding: 4; }\n", StyleText.X_NEST_KEY + "a.uicss:0", "&:active");
        assertTrue(next.contains("&:active { }"), next);
        assertEquals("a.uicss", StyleText.externalHref(StyleText.X_NEST_KEY + "a.uicss:0"));
    }

    @Test
    void everyInlineRuleOffersANestedRow() throws Exception {
        UiDocument d = UiDocument.parse(XML, new FixedMetrics());
        d.layout(200, 200);
        List<PropertyModel.PropRow> rows = StyleText.propRows(d.findById("c"), XmlSource.parse(XML));
        assertTrue(rows.stream().anyMatch(r -> r.label().equals("+ 중첩") && r.key().equals(StyleText.NEST_KEY + 0)), "각 규칙에 '+ 중첩' 행이 있다");
    }
}
