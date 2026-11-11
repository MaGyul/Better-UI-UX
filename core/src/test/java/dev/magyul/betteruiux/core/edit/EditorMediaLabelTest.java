package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** @media 안의 규칙은 속성 패널에서 그 조건이 지금 화면 크기에서 켜져 있는지 보여 준다. */
class EditorMediaLabelTest {
    private static final class Host implements EditorHost {
        @Override public UiDocument load(String text) throws UiParseException { return UiDocument.parse(text, new FixedMetrics()); }

        @Override public void save(String text) throws IOException { }

        @Override public String title() { return "t.xml"; }
    }

    private static final String XML = "<screen><style>.card { padding: 8; } @media (max-width: 100) { .card { padding: 2; } }</style>"
            + "<panel id='a' class='card' width='80' height='40'/></screen>";

    private static List<String> headers(int w, int h) throws Exception {
        List<PropertyModel.PropRow> rows = StyleText.propRows(box(w, h), XmlSource.parse(XML), c -> w <= 100);
        return rows.stream().filter(r -> r.kind() == PropertyModel.Kind.HEADER).map(PropertyModel.PropRow::label).toList();
    }

    private static dev.magyul.betteruiux.core.layout.LayoutBox box(int w, int h) throws Exception {
        UiDocument d = UiDocument.parse(XML, new FixedMetrics());
        d.layout(w, h);
        return d.findById("a");
    }

    @Test
    void mediaRuleHeaderShowsWhetherItIsActive() throws Exception {
        List<String> narrow = headers(80, 100), wide = headers(400, 100);
        assertNotNull(narrow.stream().filter(s -> s.contains("@media (max-width: 100)") && s.contains("[적용 중]")).findFirst().orElse(null), narrow.toString());
        assertNotNull(wide.stream().filter(s -> s.contains("@media (max-width: 100)") && s.contains("[꺼짐]")).findFirst().orElse(null), wide.toString());
        assertNull(narrow.stream().filter(s -> s.equals(".card") && s.contains("@media")).findFirst().orElse(null), "조건 밖의 규칙에는 표시가 없다");
    }

    @Test
    void documentEvaluatesMediaConditions() throws Exception {
        UiDocument d = UiDocument.parse(XML, new FixedMetrics());
        d.layout(80, 100);
        assertEquals(true, d.mediaMatches("(max-width: 100)"));
        d.layout(400, 100);
        assertEquals(false, d.mediaMatches("(max-width: 100)"));
        assertEquals(false, d.mediaMatches("(((broken"));
    }
}
