package dev.magyul.betteruiux.core.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.magyul.betteruiux.core.doc.UiDocument;
import org.junit.jupiter.api.Test;

class LayoutFlexTest {
    private static UiDocument doc(String xml) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.layout(400, 200);
        return d;
    }

    private static LayoutBox box(UiDocument d, String id) { return d.findById(id); }

    @Test
    void wrapBreaksIntoLines() throws Exception {
        UiDocument d = doc("""
            <screen>
              <row id='r' width='100' height='100' flex-wrap='wrap' gap='4'>
                <panel id='a' width='40' height='10'/><panel id='b' width='40' height='10'/>
                <panel id='c' width='40' height='10'/><panel id='d' width='40' height='10'/>
                <panel id='e' width='40' height='10'/>
              </row>
            </screen>""");
        assertEquals(0, box(d, "a").x);
        assertEquals(44, box(d, "b").x);
        assertEquals(0, box(d, "c").x);                      // 두 번째 줄
        assertEquals(14, box(d, "c").y);                     // 첫 줄 높이 10 + 줄 간격 4
        assertEquals(28, box(d, "e").y);
    }

    @Test
    void growSharesFreeSpaceAndShrinkTakesOverflow() throws Exception {
        UiDocument d = doc("""
            <screen>
              <row width='200' height='20'>
                <panel id='a' width='50' height='5' grow='1'/><panel id='b' width='50' height='5'/>
              </row>
              <row y='30' width='100' height='20'>
                <panel id='c' width='80' height='5' flex-shrink='1'/><panel id='d' width='80' height='5' flex-shrink='1'/>
              </row>
            </screen>""");
        assertEquals(150, box(d, "a").w);
        assertEquals(50, box(d, "b").w);
        assertEquals(50, box(d, "c").w);                     // 160 - 100 = 60 넘침, 각 30씩 줄어든다
        assertEquals(50, box(d, "d").w);
    }

    @Test
    void flexShorthandAndBasis() throws Exception {
        UiDocument d = doc("""
            <screen>
              <row width='200' height='20'>
                <panel id='a' height='5' flex='1'/><panel id='b' height='5' flex='3'/>
              </row>
              <row y='30' width='200' height='20'>
                <panel id='c' height='5' flex-basis='60'/><panel id='d' height='5' flex='0 0 30px'/>
              </row>
            </screen>""");
        assertEquals(50, box(d, "a").w);
        assertEquals(150, box(d, "b").w);
        assertEquals(60, box(d, "c").w);
        assertEquals(30, box(d, "d").w);
    }

    @Test
    void orderAndJustifyAliases() throws Exception {
        UiDocument d = doc("""
            <screen>
              <row width='100' height='20'>
                <panel id='a' width='20' height='5' order='2'/><panel id='b' width='20' height='5' order='1'/>
              </row>
              <row y='30' width='100' height='20' justify-content='space-evenly'>
                <panel id='c' width='10' height='5'/><panel id='e' width='10' height='5'/>
              </row>
              <row y='60' width='100' height='20' align-items='center'>
                <panel id='f' width='10' height='6'/>
              </row>
            </screen>""");
        assertEquals(0, box(d, "b").x);
        assertEquals(20, box(d, "a").x);
        assertEquals(26, box(d, "c").x);                     // 남는 80 / 3 = 26
        assertEquals(7, box(d, "f").y - 60);                 // 가운데 정렬 (20-6)/2
    }

    @Test
    void autoMargins() throws Exception {
        UiDocument d = doc("""
            <screen>
              <column width='100' height='100'>
                <panel id='a' width='20' height='10' margin='0 auto'/>
                <panel id='b' width='20' height='10' margin='0 0 0 auto'/>
              </column>
              <row y='120' width='100' height='20'>
                <panel id='c' width='10' height='5'/><panel id='d' width='10' height='5' margin='0 0 0 auto'/>
              </row>
            </screen>""");
        assertEquals(40, box(d, "a").x);
        assertEquals(80, box(d, "b").x);
        assertEquals(90, box(d, "d").x);                     // auto 가 남는 공간을 모두 먹는다
        assertEquals(0, box(d, "c").x);
    }

    @Test
    void displayFlexOnPanel() throws Exception {
        UiDocument d = doc("""
            <screen>
              <panel id='p' display='flex' gap='5' width='100' height='50'>
                <panel id='a' width='10' height='10'/><panel id='b' width='10' height='10'/>
              </panel>
              <panel id='q' y='60' display='flex' flex-direction='column' gap='2' width='100' height='50'>
                <panel id='c' width='10' height='10'/><panel id='e' width='10' height='10'/>
              </panel>
            </screen>""");
        assertEquals(15, box(d, "b").x);
        assertEquals(12, box(d, "e").y - 60);
    }

    @Test
    void absoluteAndRelativePositioning() throws Exception {
        UiDocument d = doc("""
            <screen>
              <panel id='p' x='50' y='20' width='200' height='100'>
                <panel id='a' position='absolute' right='10' bottom='5' width='20' height='10'/>
                <panel id='s' position='absolute' left='5' right='5' top='1' height='4'/>
                <panel id='r' position='relative' left='7' top='3' width='10' height='10'/>
              </panel>
            </screen>""");
        assertEquals(50 + 200 - 10 - 20, box(d, "a").x);
        assertEquals(20 + 100 - 5 - 10, box(d, "a").y);
        assertEquals(190, box(d, "s").w);                    // left/right 로 늘어남
        assertEquals(57, box(d, "r").x);
        assertEquals(23, box(d, "r").y);
    }

    @Test
    void absoluteChildrenLeaveTheFlow() throws Exception {
        UiDocument d = doc("""
            <screen>
              <row width='100' height='20' gap='3'>
                <panel id='a' width='10' height='5'/>
                <panel id='x' position='absolute' left='0' top='0' width='50' height='5'/>
                <panel id='b' width='10' height='5'/>
              </row>
            </screen>""");
        assertEquals(13, box(d, "b").x);
    }

    @Test
    void zIndexControlsHitOrder() throws Exception {
        UiDocument d = doc("""
            <screen>
              <panel id='a' x='0' y='0' width='50' height='50' z-index='5'/>
              <panel id='b' x='0' y='0' width='50' height='50'/>
            </screen>""");
        assertSame(box(d, "a"), d.hitTest(10, 10));
    }

    @Test
    void aspectRatio() throws Exception {
        UiDocument d = doc("""
            <screen>
              <panel id='a' width='100' aspect-ratio='2'/>
              <panel id='b' x='150' height='30' aspect-ratio='16/9'/>
              <row y='100' width='200' height='80'>
                <panel id='c' width='60' aspect-ratio='3'/>
              </row>
            </screen>""");
        assertEquals(50, box(d, "a").h);
        assertEquals(53, box(d, "b").w);
        assertEquals(20, box(d, "c").h);
    }

    @Test
    void borderTakesLayoutSpace() throws Exception {
        UiDocument d = doc("""
            <screen>
              <column id='p' width='100' height='60' border='2' padding='3'>
                <panel id='a' width='fill' height='10'/>
              </column>
            </screen>""");
        assertEquals(5, box(d, "a").x);
        assertEquals(5, box(d, "a").y);
        assertEquals(90, box(d, "a").w);
    }
}
