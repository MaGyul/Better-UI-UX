package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.edit.PropertyModel.Kind;
import dev.magyul.betteruiux.core.edit.PropertyModel.PropRow;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

class PropertyModelTest {
    private static List<PropRow> rows(String xml, String id) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.layout(300, 200);
        return PropertyModel.rows(d.findById(id), null, null);
    }

    private static PropRow row(List<PropRow> rows, String key) {
        return rows.stream().filter(r -> r.key().equals(key)).findFirst().orElse(null);
    }

    @Test
    void advancedSectionsExistAndStartCollapsed() throws Exception {
        List<PropRow> rows = rows("<screen><column><label id='l' text='hi'/></column></screen>", "l");
        for (String sec : new String[] {PropertyModel.SEC_BOX, PropertyModel.SEC_TEXT, PropertyModel.SEC_FX}) {
            PropRow h = row(rows, "§" + sec);
            assertEquals(Kind.HEADER, h.kind());
            assertTrue(PropertyModel.DEFAULT_COLLAPSED.contains(h.key()));
        }
    }

    @Test
    void rowKindsForNewProperties() throws Exception {
        List<PropRow> rows = rows("<screen><column><label id='l' text='hi' style='font-size: 12'/></column></screen>", "l");
        assertEquals(Kind.NUMBER, row(rows, "font-size").kind());
        assertEquals("12", row(rows, "font-size").value());
        assertEquals(Kind.ENUM, row(rows, "white-space").kind());
        assertTrue(row(rows, "white-space").options().contains("pre-wrap"));
        assertEquals(Kind.ENUM, row(rows, "text-decoration").kind());
        assertEquals(Kind.NUMBER, row(rows, "min-width").kind());
        assertEquals(Kind.COLOR, row(rows, "outline-color").kind());
        assertEquals(Kind.TEXT, row(rows, "border-radius").kind());
        assertEquals(Kind.TEXT, row(rows, "box-shadow").kind());
        assertTrue(row(rows, "justify") == null || row(rows, "justify").options().contains("space-evenly"));
    }

    @Test
    void rowsDependOnTheElementAndItsParent() throws Exception {
        List<PropRow> inFlow = rows("<screen><row><panel id='p'/></row></screen>", "p");
        assertTrue(row(inFlow, "grow") != null && row(inFlow, "order") != null);
        assertFalse(row(inFlow, "grid-column") != null);
        assertEquals(null, row(inFlow, "font-size"));                       // panel 에는 글자 섹션이 없다

        List<PropRow> inGrid = rows("<screen><grid columns='2'><panel id='p'/></grid></screen>", "p");
        assertTrue(row(inGrid, "grid-column") != null && row(inGrid, "grid-row") != null);
        assertEquals(null, row(inGrid, "grow"));

        List<PropRow> grid = rows("<screen><grid id='g' columns='2'/></screen>", "g");
        assertTrue(row(grid, "grid-template-columns") != null);

        List<PropRow> row = rows("<screen><row id='r'/></screen>", "r");
        assertTrue(row(row, "flex-wrap") != null);
    }
}
