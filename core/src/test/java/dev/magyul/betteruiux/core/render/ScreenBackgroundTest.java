package dev.magyul.betteruiux.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScreenBackgroundTest {
    private static List<DrawCommand> background(String xml) throws Exception {
        UiDocument d = UiDocument.parse(xml, new FixedMetrics());
        d.layout(320, 240);
        DrawList list = new DrawList();
        d.paintBackground(list);
        return list.commands();
    }

    @Test
    void defaultIsBlur() throws Exception {
        assertEquals(List.of(new DrawCommand.BlurBackground()), background("<screen/>"));
    }

    @Test
    void blockThenBlurKeepsWrittenOrder() throws Exception {
        List<DrawCommand> c = background("<screen use='block:minecraft:dirt,blur'/>");
        assertEquals(2, c.size());
        assertEquals(new DrawCommand.DrawTexture("minecraft:textures/block/dirt.png",
                0, 0, 320, 240, 0, 0, 320, 240, 32, 32, 0xFF404040), c.get(0));
        assertEquals(new DrawCommand.BlurBackground(), c.get(1));
    }

    @Test
    void reversedOrderIsHonoured() throws Exception {
        List<DrawCommand> c = background("<screen use='blur, dim'/>");
        assertEquals(new DrawCommand.BlurBackground(), c.get(0));
        assertEquals(new DrawCommand.FillGradient(0, 0, 320, 240,
                ScreenBackground.DIM_TOP, ScreenBackground.DIM_BOTTOM), c.get(1));
    }

    @Test
    void allThreeCombinedAndShortBlockId() throws Exception {
        List<DrawCommand> c = background("<screen use='block:stone dim blur'/>");
        assertEquals(3, c.size());
        assertEquals("minecraft:textures/block/stone.png", ((DrawCommand.DrawTexture) c.get(0)).texture());
        assertTrue(c.get(1) instanceof DrawCommand.FillGradient);
        assertTrue(c.get(2) instanceof DrawCommand.BlurBackground);
    }

    @Test
    void noneDrawsNothingAndIsFlagged() throws Exception {
        UiDocument d = UiDocument.parse("<screen use='none'/>", new FixedMetrics());
        d.layout(100, 100);
        DrawList list = new DrawList();
        d.paintBackground(list);
        assertTrue(list.commands().isEmpty());
        assertTrue(d.background().isNone());
        assertTrue(d.warnings().isEmpty());
    }

    @Test
    void noneMixedWithOthersWarnsAndIsIgnored() {
        ScreenBackground b = ScreenBackground.parse("none,dim");
        assertFalse(b.isNone());
        assertEquals(1, b.layers().size());
        assertEquals(1, b.warnings().size());
    }

    @Test
    void unknownTokensAndBadBlockIdsWarnButOthersStillWork() throws Exception {
        UiDocument d = UiDocument.parse("<screen use='dim,sparkle,block:../x'/>", new FixedMetrics());
        d.layout(100, 100);
        assertEquals(1, d.background().layers().size());
        assertEquals(2, d.warnings().size());
    }

    @Test
    void useCanBeSetFromStyleSheetWithBlockOptions() throws Exception {
        List<DrawCommand> c = background("""
            <screen>
              <style>screen { use: block:mymod:tile; block-size: 16; block-tint: #FFFFFFFF }</style>
            </screen>""");
        assertEquals(new DrawCommand.DrawTexture("mymod:textures/block/tile.png",
                0, 0, 320, 240, 0, 0, 320, 240, 16, 16, 0xFFFFFFFF), c.get(0));
    }

    @Test
    void sizeFollowsRelayout() throws Exception {
        UiDocument d = UiDocument.parse("<screen use='dim'/>", new FixedMetrics());
        d.layout(100, 50);
        d.layout(200, 80);
        DrawList list = new DrawList();
        d.paintBackground(list);
        assertEquals(200, ((DrawCommand.FillGradient) list.commands().get(0)).w());
    }
}
