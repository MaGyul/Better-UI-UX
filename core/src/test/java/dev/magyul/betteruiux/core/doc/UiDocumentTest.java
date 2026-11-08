package dev.magyul.betteruiux.core.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiDocumentTest {
    private static final String XML = """
        <screen>
          <panel anchor='center' width='100' height='60' background='#202020'>
            <column gap='4' padding='6' align='center'>
              <label text='Hi' color='#FFFF00'/>
              <button id='ok' onClick='mymod:ok' text='OK' width='60'/>
            </column>
          </panel>
        </screen>""";

    private UiDocument doc() throws Exception {
        UiDocument d = UiDocument.parse(XML, new FixedMetrics());
        d.layout(320, 240);
        return d;
    }

    @Test
    void paintProducesExpectedDrawCommands() throws Exception {
        DrawList list = new DrawList();
        doc().paint(list, -1, -1);
        List<DrawCommand> c = list.commands();
        // panel(110,90 100x60) 안의 auto 폭 column(60+패딩)이 좌상단에 놓이고 label/버튼은 그 안에서 가운데 정렬
        assertEquals(new DrawCommand.FillRect(110, 90, 100, 60, 0xFF202020), c.get(0));
        assertEquals(new DrawCommand.DrawText("Hi", 116 + (60 - 12) / 2, 96, 0xFFFFFF00, true), c.get(1));
        assertEquals(new DrawCommand.FillRect(116, 109, 60, 17, 0xFF555555), c.get(2));
        assertTrue(c.stream().anyMatch(x -> x instanceof DrawCommand.DrawText t && t.text().equals("OK")));
    }

    @Test
    void hoverChangesButtonColor() throws Exception {
        UiDocument d = doc();
        var ok = d.findById("ok");
        DrawList hover = new DrawList();
        d.paint(hover, ok.x + 2, ok.y + 2);
        assertTrue(hover.commands().contains(new DrawCommand.FillRect(ok.x, ok.y, ok.w, ok.h, 0xFF777777)));
    }

    @Test
    void clickFiresActionOnlyWhenReleasedOnSameButton() throws Exception {
        UiDocument d = doc();
        List<String> actions = new ArrayList<>();
        d.setActionHandler((a, src) -> actions.add(a));
        var ok = d.findById("ok");
        int cx = ok.x + ok.w / 2, cy = ok.y + ok.h / 2;

        assertTrue(d.mousePressed(cx, cy, 0));
        assertTrue(d.mouseReleased(cx, cy, 0));
        assertEquals(List.of("mymod:ok"), actions);

        assertTrue(d.mousePressed(cx, cy, 0));
        d.mouseReleased(0, 0, 0);               // 버튼 밖에서 뗌 → 취소
        assertEquals(1, actions.size());

        assertFalse(d.mousePressed(0, 0, 0));    // 빈 곳
        assertFalse(d.mousePressed(cx, cy, 1));  // 우클릭은 무시
    }

    @Test
    void relayoutFollowsViewSize() throws Exception {
        UiDocument d = doc();
        int x1 = d.findById("ok").x;
        d.layout(640, 480);
        assertEquals(x1 + 160, d.findById("ok").x);
    }
}
