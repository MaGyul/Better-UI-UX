package dev.magyul.betteruiux.core.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.magyul.betteruiux.core.doc.UiDocument;
import org.junit.jupiter.api.Test;

/** FixedMetrics: 글자당 6px, 줄 높이 9px. 줄바꿈하는 글자가 컨테이너/grid 칸의 높이에 반영된다. */
class LayoutWrapHeightTest {
    private static UiDocument doc(String xml) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.layout(300, 300);
        return d;
    }

    // 폭 60 → 한 줄에 10글자: 'aaaa bbbb cccc' = 14자 → 2줄(18px)
    private static final String WRAP = "style='white-space: normal'";

    @Test
    void columnWithFixedWidthGrowsForWrappedLabel() throws Exception {
        UiDocument d = doc("<screen><column id='c' width='60'><label id='l' " + WRAP + ">aaaa bbbb cccc</label></column></screen>");
        assertEquals(18, d.findById("l").h);
        assertEquals(18, d.findById("c").h, "컨테이너 높이도 두 줄만큼");
    }

    @Test
    void nestedContainersPropagateTheWidth() throws Exception {
        UiDocument d = doc("<screen><column id='c' width='60'><panel id='p'><label id='l' " + WRAP + ">aaaa bbbb cccc</label></panel></column></screen>");
        assertEquals(18, d.findById("p").h);
        assertEquals(18, d.findById("c").h);
    }

    @Test
    void paddingIsSubtractedFromTheWrapWidth() throws Exception {
        UiDocument d = doc("<screen><column id='c' width='72' padding='6'><label id='l' " + WRAP + ">aaaa bbbb cccc</label></column></screen>");
        assertEquals(18 + 12, d.findById("c").h, "안쪽 폭 60 → 두 줄 + 위아래 여백 12");
    }

    @Test
    void gridCellRowGrowsForWrappedText() throws Exception {
        UiDocument d = doc("<screen><grid id='g' columns='2' width='130'><label id='a' width='60' " + WRAP + ">aaaa bbbb cccc</label><label id='b' width='60'>x</label>"
                + "<label id='c' width='60'>y</label></grid></screen>");
        assertEquals(18, d.findById("a").h);
        assertEquals(18, d.findById("c").y - d.findById("a").y, "둘째 줄은 첫 줄의 두 줄 높이만큼 아래");
    }

    @Test
    void scrollContentUsesWrappedHeight() throws Exception {
        UiDocument d = doc("<screen><scroll id='s' width='60' height='20'><label id='a' " + WRAP + ">aaaa bbbb cccc dddd eeee</label><label id='b'>z</label></scroll></screen>");
        assertEquals(27, d.findById("a").h, "25자 → 3줄");
        assertEquals(d.findById("a").y + 27, d.findById("b").y, "다음 항목은 줄바꿈된 높이만큼 아래");
    }
}
