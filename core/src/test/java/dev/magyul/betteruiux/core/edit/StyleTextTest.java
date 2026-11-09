package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 문서 안 &lt;style&gt; 규칙의 위치 추적과 선언 편집. */
class StyleTextTest {
    private static final String XML = """
            <screen>
              <style>
                /* 카드 */
                .card { background: #E0181A24; padding: 8; }
                button:hover { background: #FF445566 }   /* 끝 세미콜론 없음 */
                @keyframes pulse { from { opacity: 0 } to { opacity: 1 } }
                #a, .big {
                  width: 100;
                  color: #FFFFFF;
                }
              </style>
              <panel id="a" class="card"><button id="b" text="x"/></panel>
            </screen>
            """;

    private static XmlSource src(String t) { return XmlSource.parse(t); }

    @Test
    void readsRulesWithPositionsAndSkipsAtRules() {
        List<StyleText.Rule> rules = StyleText.rules(src(XML));
        assertEquals(3, rules.size());
        assertEquals(".card", rules.get(0).selector());
        assertEquals("button:hover", rules.get(1).selector());
        assertEquals("#a, .big", rules.get(2).selector());
        assertEquals(2, rules.get(0).decls().size());
        StyleText.Decl d = rules.get(0).decls().get(0);
        assertEquals("background", d.prop());
        assertEquals("#E0181A24", XML.substring(d.valueStart(), d.valueEnd()));
        assertEquals("#FF445566", XML.substring(rules.get(1).decls().get(0).valueStart(), rules.get(1).decls().get(0).valueEnd()));
    }

    @Test
    void replacesAValueKeepingEverythingElse() {
        String out = StyleText.setDecl(src(XML), 0, "padding", "12");
        assertEquals(XML.replace("padding: 8;", "padding: 12;"), out);
        UiDocument.class.getName();                                  // 결과도 여전히 올바른 문서
        assertTrue(out.contains("/* 카드 */"));
    }

    @Test
    void addsADeclarationWithMatchingStyle() {
        String single = StyleText.setDecl(src(XML), 1, "color", "#FFFFFF");   // 한 줄 규칙(끝 세미콜론 없음)
        assertTrue(single.contains("button:hover { background: #FF445566; color: #FFFFFF; }") || single.contains("background: #FF445566; color: #FFFFFF;"), single);

        String multi = StyleText.setDecl(src(XML), 2, "opacity", "0.5");      // 여러 줄 규칙은 같은 들여쓰기로 한 줄 추가
        assertTrue(multi.contains("  color: #FFFFFF;\n      opacity: 0.5;") || multi.contains("color: #FFFFFF;\n      opacity: 0.5;"), multi);
        assertEquals(1, StyleText.rules(src(multi)).get(2).decls().stream().filter(d -> d.prop().equals("opacity")).count());
    }

    @Test
    void removesADeclaration() {
        String out = StyleText.setDecl(src(XML), 2, "width", "");
        assertEquals(1, StyleText.rules(src(out)).get(2).decls().size());
        assertEquals("color", StyleText.rules(src(out)).get(2).decls().get(0).prop());
        assertEquals(XML, StyleText.setDecl(src(XML), 2, "nothing", ""), "없는 속성 지우기는 변화 없음");
    }

    @Test
    void panelKeysApplyAndValidate() {
        String out = StyleText.apply(src(XML), "css:0:background", "#FF000000");
        assertTrue(out.contains("background: #FF000000;"));
        String added = StyleText.apply(src(XML), "css+:0", "border: 1");
        assertTrue(StyleText.rules(src(added)).get(0).decls().stream().anyMatch(d -> d.prop().equals("border")));
        assertThrows(IllegalArgumentException.class, () -> StyleText.apply(src(XML), "css+:0", "엉터리"));
        assertThrows(IllegalArgumentException.class, () -> StyleText.apply(src(XML), "css:9:x", "1"));
    }

    @Test
    void matchingRulesForAnElement() throws Exception {
        UiDocument d = UiDocument.parse(XML, new FixedMetrics());
        d.layout(400, 240);
        var a = d.layout().children.stream().filter(b -> "a".equals(b.id())).findFirst().orElseThrow();
        assertEquals(List.of(0, 2), StyleText.matching(src(XML), a));   // .card 와 #a
        var b = a.children.get(0);
        assertEquals(List.of(1), StyleText.matching(src(XML), b));      // button:hover
    }
}
