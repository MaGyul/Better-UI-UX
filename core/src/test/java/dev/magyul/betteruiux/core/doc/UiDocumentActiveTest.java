package dev.magyul.betteruiux.core.doc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import org.junit.jupiter.api.Test;

class UiDocumentActiveTest {
    @Test
    void pressedButtonIsPaintedWithActiveStyle() throws Exception {
        UiDocument d = UiDocument.parse("""
            <screen>
              <style>
                button { background: #505070FF }
                button:hover { background: #7070A0FF }
                button:active { background: #303050FF }
              </style>
              <button id='b' text='x' width='40' height='20'/>
            </screen>""", new FixedMetrics());
        d.layout(200, 100);
        var b = d.findById("b");
        int cx = b.x + 5, cy = b.y + 5;

        DrawList normal = new DrawList();
        d.paint(normal, -1, -1);
        assertTrue(normal.commands().contains(new DrawCommand.FillRect(b.x, b.y, b.w, b.h, 0xFF505070)));

        DrawList hover = new DrawList();
        d.paint(hover, cx, cy);
        assertTrue(hover.commands().contains(new DrawCommand.FillRect(b.x, b.y, b.w, b.h, 0xFF7070A0)));

        d.mousePressed(cx, cy, 0);
        DrawList active = new DrawList();
        d.paint(active, cx, cy);
        assertTrue(active.commands().contains(new DrawCommand.FillRect(b.x, b.y, b.w, b.h, 0xFF303050)),
                "active 색이 그려지지 않음: " + active.commands());
    }
}
