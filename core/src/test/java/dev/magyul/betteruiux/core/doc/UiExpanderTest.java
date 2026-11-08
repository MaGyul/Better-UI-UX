package dev.magyul.betteruiux.core.doc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UiExpanderTest {
    @TempDir Path dir;

    private UiDocument load(String id) throws Exception {
        UiDocument d = UiDocumentLoader.load(dir, id, new FixedMetrics());
        d.layout(200, 100);
        return d;
    }

    private void write(String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content);
    }

    // ---- template / use ----

    @Test
    void useClonesTemplateWithParametersAndDefaults() throws Exception {
        UiDocument d = UiDocument.parse("""
            <screen>
              <template id="item" text="기본" tint="#111">
                <label class="${cls:row}" text="${text}-${tint}"/>
              </template>
              <column>
                <use template="item" text="A"/>
                <use template="item" text="B" tint="#222" cls="hot"/>
              </column>
            </screen>""", new FixedMetrics());
        List<UiElement> labels = d.queryAll("label");
        assertEquals(2, labels.size());
        assertEquals("A-#111", labels.get(0).attr("text"));
        assertEquals("row", labels.get(0).attr("class"));
        assertEquals("B-#222", labels.get(1).attr("text"));
        assertEquals("hot", labels.get(1).attr("class"));
        assertNull(d.query("template"), "template 정의는 문서에서 사라진다");
        assertNull(d.query("use"));
        assertTrue(d.warnings().isEmpty(), d.warnings().toString());
    }

    @Test
    void slotsReceiveUseChildrenOrFallbackToDefaultContent() throws Exception {
        UiDocument d = UiDocument.parse("""
            <screen>
              <template id="card">
                <panel class="card"><label class="head" text="${title}"/><slot><label class="empty" text="(비어 있음)"/></slot></panel>
              </template>
              <use template="card" title="One"><label class="body" text="내용"/></use>
              <use template="card" title="Two"/>
            </screen>""", new FixedMetrics());
        List<UiElement> cards = d.queryAll(".card");
        assertEquals(2, cards.size());
        assertNotNull(cards.get(0).query(".body"));
        assertNull(cards.get(0).query(".empty"));
        assertNotNull(cards.get(1).query(".empty"), "내용이 없으면 slot 기본 내용");
    }

    @Test
    void nestedTemplatesExpandAndRecursionIsStopped() throws Exception {
        UiDocument ok = UiDocument.parse("""
            <screen>
              <template id="inner"><label text="in"/></template>
              <template id="outer"><panel><use template="inner"/></panel></template>
              <use template="outer"/>
            </screen>""", new FixedMetrics());
        assertEquals(1, ok.queryAll("label").size());
        assertTrue(ok.warnings().isEmpty());

        UiDocument loop = UiDocument.parse("""
            <screen>
              <template id="a"><use template="a"/></template>
              <use template="a"/>
            </screen>""", new FixedMetrics());
        assertTrue(loop.warnings().stream().anyMatch(w -> w.contains("깊게")), loop.warnings().toString());
        assertNull(loop.query("use"));
    }

    @Test
    void unknownTemplateAndMissingParameterBecomeWarnings() throws Exception {
        UiDocument d = UiDocument.parse("""
            <screen>
              <template id="t"><label text="[${must}]"/></template>
              <use template="nope"/>
              <use template="t"/>
              <use template="t"/>
            </screen>""", new FixedMetrics());
        assertEquals(2, d.queryAll("label").size(), "알 수 없는 템플릿만 빠지고 나머지는 표시");
        assertEquals("[]", d.queryAll("label").get(0).attr("text"));
        assertEquals(1, d.warnings().stream().filter(w -> w.contains("nope")).count());
        assertEquals(1, d.warnings().stream().filter(w -> w.contains("must")).count(), "같은 누락은 한 번만 보고");
    }

    // ---- include ----

    @Test
    void includeSplicesFragmentChildrenAndPassesParameters() throws Exception {
        write("card.xml", """
            <fragment>
              <link rel="style" href="card.uicss"/>
              <panel class="card"><label text="${title:untitled}"/><slot/></panel>
            </fragment>""");
        write("card.uicss", ".card { background: #FF112233 }");
        write("main.xml", """
            <screen>
              <include src="card" title="Hello"><label id="extra" text="x"/></include>
              <include src="card.xml"/>
            </screen>""");
        UiDocument d = load("main");
        List<UiElement> cards = d.queryAll(".card");
        assertEquals(2, cards.size());
        assertEquals("Hello", cards.get(0).query("label").attr("text"));
        assertNotNull(d.query("#extra"), "include 의 자식은 slot 으로 들어간다");
        assertEquals("untitled", cards.get(1).query("label").attr("text"), "기본값");
        assertEquals("#FF112233", cards.get(0).style("background"), "include 된 파일의 link 스타일도 적용");
        assertTrue(d.warnings().isEmpty(), d.warnings().toString());
    }

    @Test
    void includeOfNonFragmentRootInsertsRootItselfAndTemplatesSpanFiles() throws Exception {
        write("parts.xml", """
            <fragment>
              <template id="btn"><button text="${t}"/></template>
            </fragment>""");
        write("one.xml", "<label id='one' text='1'/>");
        write("main.xml", """
            <screen>
              <include src="parts"/>
              <include src="one"/>
              <use template="btn" t="OK"/>
            </screen>""");
        UiDocument d = load("main");
        assertEquals("1", d.query("#one").attr("text"));
        assertEquals("OK", d.query("button").attr("text"), "다른 파일에서 정의한 template 도 쓸 수 있다");
    }

    @Test
    void includeProblemsAreWarningsNotErrors() throws Exception {
        write("a.xml", "<fragment><include src='b'/><label text='a'/></fragment>");
        write("b.xml", "<fragment><include src='a'/><label text='b'/></fragment>");
        write("broken.xml", "<fragment>\n<column>\n</fragment>");
        write("main.xml", """
            <screen>
              <include src="a"/>
              <include src="missing"/>
              <include src="broken"/>
              <include src="../escape"/>
              <include/>
              <label id="alive" text="still here"/>
            </screen>""");
        UiDocument d = load("main");
        assertNotNull(d.query("#alive"));
        String w = d.warnings().toString();
        assertTrue(w.contains("순환"), w);
        assertTrue(w.contains("missing"), w);
        assertTrue(w.contains("broken") && w.contains("XML 오류"), w);
        assertTrue(w.contains("../escape"), w);
        assertTrue(w.contains("src 속성이 없습니다"), w);
        assertEquals(2, d.queryAll("label").stream().filter(l -> !"alive".equals(l.id())).count(),
                "순환 직전까지는 펼쳐진다(a, b 한 번씩)");
    }

    @Test
    void expandedDocumentIsLaidOutAndHotReloadsThroughLoader() throws Exception {
        write("row.xml", "<fragment><button id='b' text='${t}' width='40' height='10'/></fragment>");
        write("main.xml", "<screen><column><include src='row' t='A'/><include src='row' t='B'/></column></screen>");
        UiDocument d = load("main");
        assertEquals(10, d.queryAll("button").get(1).y() - d.queryAll("button").get(0).y(), "두 버튼이 세로로 배치");
        write("row.xml", "<fragment><button text='${t}!' width='40' height='20'/></fragment>");
        UiDocument again = load("main");
        assertEquals("A!", again.queryAll("button").get(0).attr("text"));
        assertEquals(20, again.queryAll("button").get(1).y() - again.queryAll("button").get(0).y());
    }
}
