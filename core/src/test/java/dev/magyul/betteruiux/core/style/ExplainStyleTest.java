package dev.magyul.betteruiux.core.style;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import org.junit.jupiter.api.Test;

class ExplainStyleTest {
    private static UiDocument doc() throws Exception {
        UiDocument d = UiDocument.parse("""
            <screen>
              <style>
                .a { width: 9 }
                .imp { width: 7 !important }
                .b { border: 2px solid red }
                .c { color: #fff }
                .c:hover { color: red }
                panel#low { height: 3 }
                .hi { height: 4 }
              </style>
              <panel id='inl' class='a' style='width: 5' height='1'/>
              <panel id='sheet' class='a' width='1'/>
              <panel id='imp' class='imp' style='width: 5'/>
              <panel id='attr' width='12'/>
              <panel id='short' class='b'/>
              <panel id='parent' class='c'><label id='kid' text='x'/></panel>
              <row><separator id='sep'/></row>
              <panel id='none'/>
              <panel id='spec' class='hi'/>
              <panel id='low' class='hi'/>
            </screen>""", new FixedMetrics());
        d.layout(300, 200);
        return d;
    }

    private static String why(UiDocument d, String id, String prop) {
        return d.explainStyle(d.findById(id), prop);
    }

    @Test
    void inlineSheetAttributeAndImportantOrder() throws Exception {
        UiDocument d = doc();
        assertTrue(why(d, "inl", "width").contains("인라인") && why(d, "inl", "width").contains("width: 5"), why(d, "inl", "width"));
        assertTrue(why(d, "inl", "height").contains("XML 속성"), why(d, "inl", "height"));
        assertTrue(why(d, "sheet", "width").contains("스타일 규칙 .a") && why(d, "sheet", "width").contains("width: 9"), why(d, "sheet", "width"));
        assertTrue(why(d, "imp", "width").contains("!important") && why(d, "imp", "width").contains(".imp"), why(d, "imp", "width"));
        assertTrue(why(d, "attr", "width").contains("XML 속성 width=\"12\""), why(d, "attr", "width"));
    }

    @Test
    void specificityPicksTheWinningRule() throws Exception {
        UiDocument d = doc();
        assertTrue(why(d, "low", "height").contains("panel#low"), why(d, "low", "height"));      // id 가 더 구체적
        assertTrue(why(d, "spec", "height").contains(".hi"), why(d, "spec", "height"));
    }

    @Test
    void shorthandsAreTracedBackToTheirDeclaration() throws Exception {
        UiDocument d = doc();
        String s = why(d, "short", "border-color");
        assertTrue(s.contains(".b") && s.contains("border: 2px solid red"), s);
        assertTrue(why(d, "short", "border").contains(".b"));
    }

    @Test
    void inheritanceDefaultsAndNothing() throws Exception {
        UiDocument d = doc();
        assertTrue(why(d, "kid", "color").contains("부모에서 상속"), why(d, "kid", "color"));
        assertTrue(why(d, "sep", "background").contains("기본값"), why(d, "sep", "background"));
        assertTrue(why(d, "none", "opacity").contains("지정 없음"), why(d, "none", "opacity"));
    }

    @Test
    void mentionsStateRules() throws Exception {
        UiDocument d = doc();
        String s = why(d, "parent", "color");
        assertTrue(s.contains(".c") && s.contains("달라짐"), s);
    }
}
