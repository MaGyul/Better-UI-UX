package dev.magyul.betteruiux.core.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.magyul.betteruiux.core.xml.UiParser;
import org.junit.jupiter.api.Test;

class LayoutEngineTest {
    private static LayoutBox layout(String xml, int w, int h) throws Exception {
        return new LayoutEngine(new FixedMetrics()).layout(UiParser.parse(xml), w, h);
    }

    private static LayoutBox byId(LayoutBox b, String id) {
        if (id.equals(b.id())) return b;
        for (LayoutBox c : b.children) {
            LayoutBox r = byId(c, id);
            if (r != null) return r;
        }
        return null;
    }

    @Test
    void anchorCenter() throws Exception {
        LayoutBox root = layout("<screen><panel id='p' anchor='center' width='100' height='40'/></screen>", 320, 240);
        LayoutBox p = byId(root, "p");
        assertEquals(110, p.x);
        assertEquals(100, p.y);
        assertEquals(100, p.w);
    }

    @Test
    void anchorCornersWithMargin() throws Exception {
        LayoutBox root = layout("""
            <screen>
              <panel id='tl' width='10' height='10' margin='4'/>
              <panel id='br' anchor='bottom-right' width='10' height='10' margin='4'/>
            </screen>""", 100, 80);
        assertEquals(4, byId(root, "tl").x);
        assertEquals(4, byId(root, "tl").y);
        assertEquals(86, byId(root, "br").x);
        assertEquals(66, byId(root, "br").y);
    }

    @Test
    void columnGapAndAutoSize() throws Exception {
        LayoutBox root = layout("""
            <screen><column id='c' gap='3' padding='2'>
              <label id='a' text='abcd'/>
              <label id='b' text='ab'/>
            </column></screen>""", 200, 200);
        LayoutBox c = byId(root, "c");
        assertEquals(24 + 4, c.w);              // max(24, 12) + padding 4
        assertEquals(9 + 3 + 9 + 4, c.h);       // 두 줄 + gap + padding
        assertEquals(2, byId(root, "a").x);
        assertEquals(2 + 9 + 3, byId(root, "b").y);
    }

    @Test
    void rowFillSharesRemainingSpace() throws Exception {
        LayoutBox root = layout("""
            <screen><row id='r' width='100' height='20' gap='5'>
              <panel id='fixed' width='30'/>
              <panel id='f1' width='fill'/>
              <panel id='f2' width='fill'/>
            </row></screen>""", 200, 200);
        assertEquals(30, byId(root, "fixed").w);
        assertEquals(30, byId(root, "f1").w);  // (100 - 30 - 10) / 2
        assertEquals(30, byId(root, "f2").w);
        assertEquals(30 + 5, byId(root, "f1").x);
        assertEquals(30 + 5 + 30 + 5, byId(root, "f2").x);
    }

    @Test
    void justifyCenterAndAlignCenter() throws Exception {
        LayoutBox root = layout("""
            <screen><row width='100' height='30' justify='center' align='center'>
              <panel id='p' width='20' height='10'/>
            </row></screen>""", 200, 200);
        LayoutBox p = byId(root, "p");
        assertEquals(40, p.x);
        assertEquals(10, p.y);
    }

    @Test
    void percentAndSpaceBetween() throws Exception {
        LayoutBox root = layout("""
            <screen><row width='100%' height='10' justify='space-between'>
              <panel id='a' width='10'/><panel id='b' width='10'/><panel id='c' width='10'/>
            </row></screen>""", 100, 50);
        assertEquals(0, byId(root, "a").x);
        assertEquals(45, byId(root, "b").x);
        assertEquals(90, byId(root, "c").x);
    }

    @Test
    void inlineStyleOverridesAttribute() throws Exception {
        LayoutBox root = layout("<screen><panel id='p' width='10' style='width: 50px; height: 7'/></screen>", 100, 100);
        assertEquals(50, byId(root, "p").w);
        assertEquals(7, byId(root, "p").h);
    }

    @Test
    void invisibleChildrenTakeNoSpace() throws Exception {
        LayoutBox root = layout("""
            <screen><column gap='5'>
              <panel id='a' height='10' visible='false'/>
              <panel id='b' height='10' width='10'/>
            </column></screen>""", 100, 100);
        assertEquals(0, byId(root, "b").y);
    }
}
