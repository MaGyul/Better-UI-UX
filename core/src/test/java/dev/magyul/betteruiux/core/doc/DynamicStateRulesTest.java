package dev.magyul.betteruiux.core.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.layout.LayoutBox;
import java.util.List;
import org.junit.jupiter.api.Test;

class DynamicStateRulesTest {
    private static UiDocument doc(String xml) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.setClock(() -> 0);
        d.setAnimationsEnabled(false);
        d.layout(300, 200);
        return d;
    }

    private static boolean paints(UiDocument d, LayoutBox b, int argb, int mx, int my) {
        DrawList l = new DrawList();
        d.paint(l, mx, my);
        return l.commands().contains(new DrawCommand.FillRect(b.x, b.y, b.w, b.h, argb));
    }

    @Test
    void parentHoverStylesAChild() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                .card { background: #111111FF }
                .card .title { background: #222222FF }
                .card:hover .title { background: #FF0000FF }
              </style>
              <panel class='card' x='10' y='10' width='100' height='60'>
                <panel class='title' id='t' x='0' y='0' width='50' height='10'/>
              </panel>
              <panel id='other' x='200' y='150' width='10' height='10'/>
            </screen>""");
        LayoutBox t = d.findById("t");
        assertTrue(paints(d, t, 0xFF222222, -1, -1));
        assertTrue(paints(d, t, 0xFF222222, 205, 155), "카드 밖에 마우스가 있으면 평소 색");
        assertTrue(paints(d, t, 0xFFFF0000, 80, 60), "카드 위(자식 밖)에 마우스가 있어도 부모 hover 규칙이 적용된다");
        assertTrue(paints(d, t, 0xFFFF0000, 12, 12), "자식 위에 있을 때도");
    }

    @Test
    void siblingsReactToHover() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                #a:hover + #b { background: #00FF00FF }
              </style>
              <panel id='a' x='0' y='0' width='20' height='20'/>
              <panel id='b' x='0' y='30' width='20' height='20' background='#000000FF'/>
            </screen>""");
        LayoutBox b = d.findById("b");
        assertTrue(paints(d, b, 0xFF000000, -1, -1));
        assertTrue(paints(d, b, 0xFF00FF00, 5, 5));
    }

    @Test
    void notHoverAppliesWhenNotHovered() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>panel:not(:hover) { opacity: 0.5 } panel { background: #FF0000FF }</style>
              <panel id='a' x='0' y='0' width='20' height='20'/>
            </screen>""");
        LayoutBox a = d.findById("a");
        DrawList idle = new DrawList();
        d.paint(idle, -1, -1);
        assertTrue(idle.commands().contains(new DrawCommand.FillRect(a.x, a.y, a.w, a.h, 0x80FF0000)));
        DrawList hov = new DrawList();
        d.paint(hov, 5, 5);
        assertTrue(hov.commands().contains(new DrawCommand.FillRect(a.x, a.y, a.w, a.h, 0xFFFF0000)));
    }

    @Test
    void focusAndFocusWithin() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                button { background: #333333FF }
                button:focus { background: #0000FFFF }
                .row:focus-within { background: #FFFF00FF }
              </style>
              <panel class='row' id='r' x='0' y='60' width='100' height='20'>
                <button id='b' text='x' x='0' y='0' width='40' height='20'/>
              </panel>
            </screen>""");
        LayoutBox b = d.findById("b"), r = d.findById("r");
        assertTrue(paints(d, b, 0xFF333333, -1, -1));
        d.focus(d.element(b.node));
        assertTrue(paints(d, b, 0xFF0000FF, -1, -1), "포커스를 받은 버튼");
        assertTrue(paints(d, r, 0xFFFFFF00, -1, -1), "자손이 포커스를 가진 부모(:focus-within)");
    }

    @Test
    void forcedFocusPreviewForTheEditor() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>panel:focus { background: #00FFFFFF }</style>
              <panel id='p' x='0' y='0' width='20' height='20' background='#000000FF'/>
            </screen>""");
        LayoutBox p = d.findById("p");
        assertTrue(paints(d, p, 0xFF000000, -1, -1));
        d.forcePseudo(p.node.sourceIndex(), false, false, true);
        assertTrue(paints(d, p, 0xFF00FFFF, -1, -1));
        d.forcePseudo(-1, false, false, false);
        assertTrue(paints(d, p, 0xFF000000, -1, -1));
    }

    @Test
    void specificityOrderAndImportantInDynamicRules() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>
                #p:hover { background: #111111FF }
                panel:hover { background: #222222FF !important }
                .x:hover { background: #333333FF }
              </style>
              <panel id='p' class='x' x='0' y='0' width='20' height='20'/>
            </screen>""");
        assertTrue(paints(d, d.findById("p"), 0xFF222222, 5, 5));
    }

    @Test
    void layoutAffectingPropsInStateRulesNowRelayout() throws Exception {
        UiDocument d = doc("""
            <screen>
              <style>panel:hover { width: 100 }</style>
              <panel id='p' x='0' y='0' width='20' height='20'/>
            </screen>""");
        d.paint(new DrawList(), 5, 5);
        assertEquals(100, d.findById("p").w, "상태 규칙의 width 도 배치에 반영된다(LayoutStateRulesTest 참고)");
    }
}
