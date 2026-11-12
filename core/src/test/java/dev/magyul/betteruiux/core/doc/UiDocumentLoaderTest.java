package dev.magyul.betteruiux.core.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.api.DrawCommand;
import dev.magyul.betteruiux.api.DrawList;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import dev.magyul.betteruiux.core.xml.UiParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UiDocumentLoaderTest {
    @TempDir Path dir;

    @Test
    void loadsLinkedAndInlineStylesInDocumentOrder() throws Exception {
        Files.writeString(dir.resolve("t.uicss"), ".a { color: red }");
        Files.writeString(dir.resolve("t.uiml"), """
            <screen>
              <link rel="style" href="t.uicss"/>
              <style>.a { color: blue }</style>
              <label id='x' class='a' text='hi'/>
            </screen>""");
        UiDocument d = UiDocumentLoader.load(dir, "t", new FixedMetrics());
        d.layout(100, 100);
        assertEquals("blue", d.findById("x").style.get("color"));   // 나중에 온 규칙이 이김
        assertTrue(d.warnings().isEmpty());
        assertEquals(1, d.layout().children.size());                // <link>/<style> 은 레이아웃에서 제외
    }

    @Test
    void missingStylesheetIsAWarningNotAnError() throws Exception {
        Files.writeString(dir.resolve("t.uiml"), "<screen><link href='nope.uicss'/><label text='a'/></screen>");
        UiDocument d = UiDocumentLoader.load(dir, "t", new FixedMetrics());
        assertEquals(1, d.warnings().size());
    }

    @Test
    void cssWarningsAreCollected() throws Exception {
        Files.writeString(dir.resolve("t.uiml"), "<screen><style>a:bogus { color: red }</style></screen>");
        assertEquals(1, UiDocumentLoader.load(dir, "t", new FixedMetrics()).warnings().size());
    }

    @Test
    void missingXmlAndBrokenXml() throws Exception {
        assertThrows(NoSuchFileException.class, () -> UiDocumentLoader.load(dir, "nothing", new FixedMetrics()));
        Files.writeString(dir.resolve("bad.uiml"), "<screen>\n<column>\n</screen>");
        UiParseException e = assertThrows(UiParseException.class,
                () -> UiDocumentLoader.load(dir, "bad", new FixedMetrics()));
        assertTrue(e.line() >= 1);
    }

    @Test
    void userFileOverridesAndPathTraversalIsRejected() throws IOException {
        Files.writeString(dir.resolve("a.uiml"), "<screen/>");
        assertTrue(UiSources.load(dir, "a").isPresent());
        assertFalse(UiSources.load(dir, "../a").isPresent());
        assertFalse(UiSources.load(dir, "sub/a").isPresent());
        assertFalse(UiSources.loadFile(dir, "..\\a.uiml").isPresent());
    }

    @Test
    void errorOverlayDrawsOneBarPerMessageAndTruncates() {
        DrawList list = new DrawList();
        ErrorOverlay.paint(list, new FixedMetrics(), 120, List.of(
                new ErrorOverlay.Message(true, "아주 아주 긴 오류 메시지가 화면 폭을 넘는 경우"),
                new ErrorOverlay.Message(false, "짧은 경고")));
        List<DrawCommand> c = list.commands();
        assertEquals(4, c.size());                                     // (바+글자) × 2
        assertEquals(new DrawCommand.FillRect(0, 0, 120, 13, 0xE0801010), c.get(0));
        var text = (DrawCommand.DrawText) c.get(1);
        assertTrue(text.text().endsWith("..."));
        assertTrue(text.text().length() * 6 <= 120 - 8);
        assertEquals(0xE0806010, ((DrawCommand.FillRect) c.get(2)).argb());
    }

    @Test
    void configParsesSectionsAndSystemPropertyWins() throws Exception {
        Path f = dir.resolve("cfg").resolve("config.toml");
        UiConfig created = UiConfig.load(f);                           // 없으면 기본 파일 생성
        assertTrue(Files.exists(f));
        assertTrue(created.bool("hotReload.enabled", false));
        assertFalse(created.bool("editor.enabled", true));
        Files.writeString(f, "[hotReload]\nenabled = false # 끔\n");
        assertFalse(UiConfig.load(f).bool("hotReload.enabled", true));
        System.setProperty("betteruiux.hotreload.enabled", "true");
        try {
            assertTrue(UiConfig.load(f).bool("hotReload.enabled", false));
        } finally {
            System.clearProperty("betteruiux.hotreload.enabled");
        }
    }

    @Test
    void watcherReportsChangeOnlyAfterQuietPeriod() throws Exception {
        try (UiFileWatcher w = UiFileWatcher.start(dir)) {
            assertFalse(w.pollChanged(0));
            Files.writeString(dir.resolve("x.uiml"), "<screen/>");
            Files.writeString(dir.resolve("ignored.txt"), "no");
            boolean got = false;
            long end = System.currentTimeMillis() + 5000;
            while (!got && System.currentTimeMillis() < end) {
                got = w.pollChanged(50);
                if (!got) Thread.sleep(20);
            }
            assertTrue(got, "변경 이벤트가 감지되지 않음");
            assertFalse(w.pollChanged(0));                             // 한 번 소비하면 다시 false
        }
    }

    @Test
    void loadTextBuildsADocumentWithoutTouchingTheFileAndAtomicWriteReplacesFiles() throws Exception {
        Files.writeString(dir.resolve("s.uicss"), ".a { color: red }");
        UiDocument d = UiDocumentLoader.loadText(dir,
                "<screen><link href='s.uicss'/><label id='x' class='a' text='t'/></screen>", new FixedMetrics());
        d.layout(100, 100);
        assertEquals("red", d.findById("x").style.get("color"), "텍스트로 만들어도 link 스타일시트를 읽는다");

        Path f = dir.resolve("sub").resolve("out.uiml");
        AtomicFiles.write(f, "<screen/>");
        assertEquals("<screen/>", Files.readString(f));
        AtomicFiles.write(f, "<screen><label/></screen>");
        assertEquals("<screen><label/></screen>", Files.readString(f), "기존 파일을 덮어쓴다");
        try (var files = Files.list(f.getParent())) {
            assertEquals(1, files.count(), "임시 파일이 남지 않는다");
        }
    }
}
