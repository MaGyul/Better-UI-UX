package dev.magyul.betteruiux.core.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.magyul.betteruiux.core.doc.UiDocument;
import org.junit.jupiter.api.Test;

class LayoutGridTest {
    private static UiDocument doc(String xml) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.layout(400, 200);
        return d;
    }

    private static LayoutBox box(UiDocument d, String id) { return d.findById(id); }

    @Test
    void legacyColumnsStillWork() throws Exception {
        UiDocument d = doc("""
            <screen>
              <grid columns='2' gap='2'>
                <panel id='a' width='10' height='5'/><panel id='b' width='20' height='8'/>
                <panel id='c' width='15' height='5'/>
              </grid>
            </screen>""");
        assertEquals(0, box(d, "a").x);
        assertEquals(17, box(d, "b").x);                     // 열 0 폭 = max(10, 15) = 15, gap 2
        assertEquals(0, box(d, "c").x);
        assertEquals(10, box(d, "c").y);                     // 첫 행 높이 8 + gap 2
    }

    @Test
    void frTracksShareTheRemainingWidth() throws Exception {
        UiDocument d = doc("""
            <screen>
              <grid id='g' width='300' grid-template-columns='60px 1fr 2fr' gap='3'>
                <panel id='a' height='5'/><panel id='b' height='5'/><panel id='c' height='5'/>
              </grid>
            </screen>""");
        // 300 - 60 - 2*3 = 234 → 78 / 156
        assertEquals(0, box(d, "a").x);
        assertEquals(63, box(d, "b").x);
        assertEquals(63 + 78 + 3, box(d, "c").x);
    }

    @Test
    void fillChildrenStretchToTheCell() throws Exception {
        UiDocument d = doc("""
            <screen>
              <grid width='200' grid-template-columns='repeat(2, 1fr)' gap='10'>
                <panel id='a' width='fill' height='5'/><panel id='b' width='fill' height='5'/>
              </grid>
            </screen>""");
        assertEquals(95, box(d, "a").w);
        assertEquals(105, box(d, "b").x);
    }

    @Test
    void spanAndExplicitPlacement() throws Exception {
        UiDocument d = doc("""
            <screen>
              <grid width='300' grid-template-columns='repeat(3, 1fr)' row-gap='4'>
                <panel id='a' width='fill' height='10' grid-column='span 2'/>
                <panel id='b' width='fill' height='10'/>
                <panel id='c' width='fill' height='10' grid-column='2 / span 2'/>
                <panel id='d' width='fill' height='10' grid-column='1' grid-row='3'/>
              </grid>
            </screen>""");
        assertEquals(200, box(d, "a").w);
        assertEquals(200, box(d, "b").x);
        assertEquals(14, box(d, "c").y);
        assertEquals(100, box(d, "c").x);
        assertEquals(200, box(d, "c").w);
        assertEquals(28, box(d, "d").y);
        assertEquals(0, box(d, "d").x);
    }

    @Test
    void rowTemplateAndSelfAlignment() throws Exception {
        UiDocument d = doc("""
            <screen>
              <grid width='100' height='100' grid-template-columns='50px 50px' grid-template-rows='20px 1fr'>
                <panel id='a' width='10' height='10' justify-self='center' align-self='end'/>
                <panel id='b' width='10' height='10'/>
                <panel id='c' width='10' height='10'/>
              </grid>
            </screen>""");
        assertEquals(20, box(d, "a").x);
        assertEquals(10, box(d, "a").y);
        assertEquals(20, box(d, "c").y);                     // 둘째 행은 20 에서 시작
    }

    @Test
    void trackParser() {
        assertEquals(3, LayoutEngine.parseTracks("1fr 2fr 100px").size());
        assertEquals(6, LayoutEngine.parseTracks("repeat(3, 1fr 20px)").size());
        assertEquals('f', LayoutEngine.parseTracks("minmax(10px, 1fr)").get(0).kind());
        assertEquals(0, LayoutEngine.parseTracks("1fr nonsense").size());
        assertEquals(2, LayoutEngine.gridLine("2 / span 2")[1]);
        assertEquals(2, LayoutEngine.gridLine("1 / 3")[1]);
        assertEquals(0, LayoutEngine.gridLine("span 3")[0]);
    }
}
