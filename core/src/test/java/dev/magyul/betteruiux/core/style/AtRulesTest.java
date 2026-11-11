package dev.magyul.betteruiux.core.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.doc.UiDocumentLoader;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtRulesTest {
    private static boolean m(String q, int w, int h) { return MediaQuery.parse(q).matches(w, h); }

    @Test
    void mediaFeatures() {
        assertTrue(m("(min-width: 300px)", 400, 200));
        assertFalse(m("(min-width: 300px)", 200, 200));
        assertTrue(m("(max-width: 300px) and (min-height: 100px)", 250, 150));
        assertFalse(m("(max-width: 300px) and (min-height: 100px)", 250, 50));
        assertTrue(m("screen and (width >= 400)", 400, 100));
        assertTrue(m("(width < 300px)", 200, 100));
        assertTrue(m("(orientation: landscape)", 400, 200));
        assertTrue(m("(orientation: portrait)", 200, 400));
        assertTrue(m("(min-aspect-ratio: 16/9)", 480, 270));
        assertFalse(m("(min-aspect-ratio: 2)", 480, 270));
        assertTrue(m("(min-width: 50em)", 400, 100));                 // 50em = 400px
        assertTrue(m("print, (min-width: 100px)", 200, 100));         // 쉼표 = OR
        assertFalse(m("print", 200, 100));
        assertTrue(m("not print", 200, 100));
        assertTrue(m("not (min-width: 500px)", 200, 100));
        assertFalse(m("(hover: hover)", 200, 100));                   // 모르는 특성은 거짓
        assertThrows(IllegalArgumentException.class, () -> MediaQuery.parse("screen and"));
    }

    @Test
    void mediaRulesAreFilteredByViewport() {
        Stylesheet s = Stylesheet.parse("""
            .a { width: 1 }
            @media (min-width: 300px) {
              .a { width: 2 }
              @media (max-height: 100px) { .a { width: 3 } }
            }
            """);
        assertEquals(3, s.rules().size());
        assertTrue(s.rules().get(0).appliesTo(100, 500));
        assertFalse(s.rules().get(1).appliesTo(100, 500));
        assertTrue(s.rules().get(1).appliesTo(400, 500));
        assertFalse(s.rules().get(2).appliesTo(400, 500));
        assertTrue(s.rules().get(2).appliesTo(400, 50));
        assertTrue(s.warnings().isEmpty(), s.warnings().toString());
    }

    @Test
    void mediaAffectsCascadeAndLayout() throws Exception {
        String xml = """
            <screen>
              <style>
                panel { width: 10 }
                @media (min-width: 300px) { panel { width: 99 } }
              </style>
              <panel id='p'/>
            </screen>""";
        UiDocument wide = UiDocument.parse(xml, new FixedMetrics());
        wide.layout(400, 200);
        assertEquals("99", wide.findById("p").style.get("width"));
        UiDocument narrow = UiDocument.parse(xml, new FixedMetrics());
        narrow.layout(200, 200);
        assertEquals("10", narrow.findById("p").style.get("width"));
    }

    @Test
    void supports() {
        assertTrue(Stylesheet.supports("(width: 10px)"));
        assertFalse(Stylesheet.supports("(frobnicate: yes)"));
        assertTrue(Stylesheet.supports("(width: 1) and (height: 1)"));
        assertFalse(Stylesheet.supports("(width: 1) and (frobnicate: 1)"));
        assertTrue(Stylesheet.supports("(frobnicate: 1) or (height: 1)"));
        assertTrue(Stylesheet.supports("not (frobnicate: 1)"));
        assertTrue(Stylesheet.supports("selector(a:nth-child(2n))"));
        assertTrue(Stylesheet.supports("selector(a::before)"));
        assertFalse(Stylesheet.supports("selector(a::first-line)"));
        Stylesheet s = Stylesheet.parse("@supports (width: 1) { a { color: red } } @supports (zzz: 1) { b { color: red } }");
        assertEquals(1, s.rules().size());
    }

    @Test
    void importStatementsAreCollectedAndDoNotSwallowTheNextRule() {
        Stylesheet s = Stylesheet.parse("""
            @charset "utf-8";
            @import "base.uicss";
            @import url('theme.uicss') (min-width: 300px);
            .a { color: red }
            """);
        assertEquals(1, s.rules().size());
        assertEquals(2, s.imports().size());
        assertEquals("base.uicss", s.imports().get(0).href());
        assertEquals("theme.uicss", s.imports().get(1).href());
        assertEquals(1, s.imports().get(1).media().size());
        assertTrue(s.warnings().isEmpty(), s.warnings().toString());
    }

    @Test
    void importedSheetComesBeforeImporter(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("base.uicss"), "panel { width: 5; height: 6 }");
        Files.writeString(dir.resolve("loop.uicss"), "@import 'loop.uicss'; panel { height: 7 }");
        Files.writeString(dir.resolve("m.xml"), """
            <screen>
              <style>@import 'base.uicss'; panel { width: 50 }</style>
              <panel id='p'/>
            </screen>""");
        UiDocument d = UiDocumentLoader.load(dir, "m", new FixedMetrics());
        d.layout(200, 200);
        assertEquals("50", d.findById("p").style.get("width"));      // 가져온 시트보다 뒤의 규칙이 이긴다
        assertEquals("6", d.findById("p").style.get("height"));

        Files.writeString(dir.resolve("c.xml"), "<screen><link href='loop.uicss'/><panel id='p'/></screen>");
        UiDocument c = UiDocumentLoader.load(dir, "c", new FixedMetrics());
        assertTrue(c.warnings().stream().anyMatch(w -> w.contains("순환")), c.warnings().toString());
    }

    @Test
    void importMediaAppliesToWholeImportedSheet(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("wide.uicss"), "panel { width: 77 }");
        Files.writeString(dir.resolve("m.xml"), """
            <screen>
              <style>@import 'wide.uicss' (min-width: 300px);</style>
              <panel id='p' width='1'/>
            </screen>""");
        UiDocument d = UiDocumentLoader.load(dir, "m", new FixedMetrics());
        d.layout(400, 200);
        assertEquals("77", d.findById("p").style.get("width"));
        d.layout(200, 200);
        assertEquals("1", d.findById("p").style.get("width"));
    }
}
