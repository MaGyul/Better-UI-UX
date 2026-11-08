package dev.magyul.betteruiux.core.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.edit.XmlSource.Element;
import dev.magyul.betteruiux.core.xml.UiNode;
import dev.magyul.betteruiux.core.xml.UiParser;
import org.junit.jupiter.api.Test;

class XmlPatcherTest {
    private static final String DOC = """
        <!-- 주석은 보존된다 -->
        <screen use="blur">
          <style><![CDATA[ a > b { x: y } ]]></style>
          <panel id="p" width='100' anchor="center">
            <!-- 안쪽 주석 -->
            <label id="l" text="a > b" />
            <button id="b" text="OK"/>
          </panel>
        </screen>
        """;

    private static XmlSource src(String t) { return XmlSource.parse(t); }

    private static Element byId(XmlSource s, String id) {
        return s.all().stream().filter(e -> id.equals(s.attrValue(e, "id"))).findFirst().orElseThrow();
    }

    /** 패치 결과가 런타임 파서로도 정상 파싱되는지(= 올바른 XML 인지). */
    private static String valid(String t) throws Exception {
        UiParser.parse(t);
        return t;
    }

    // ---- 소스 지도 ----

    @Test
    void indicesMatchRuntimeParserPreorderAndHandleQuirks() throws Exception {
        XmlSource s = src(DOC);
        UiNode root = UiParser.parse(DOC);
        assertEquals(List.of("screen", "style", "panel", "label", "button"),
                s.all().stream().map(e -> e.tag).toList());
        // 런타임 노드의 sourceIndex 가 지도의 번호와 같다
        UiNode label = root.children().get(1).children().get(0);
        assertEquals("label", label.tag());
        assertEquals(s.all().get(label.sourceIndex()).tag, "label");
        assertEquals("a > b", s.attrValue(byId(s, "l"), "text"), "값 안의 '>' 가 태그 끝으로 오해되지 않는다");
        assertEquals("100", s.attrValue(byId(s, "p"), "width"));
        assertTrue(byId(s, "l").selfClosing());
        assertFalse(byId(s, "p").selfClosing());
    }

    @Test
    void malformedSourceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> src("<a><b></a>"));
        assertThrows(IllegalArgumentException.class, () -> src("<a x=1/>"));
        assertThrows(IllegalArgumentException.class, () -> src("<a>"));
        assertThrows(IllegalArgumentException.class, () -> src("   "));
    }

    // ---- 속성 ----

    private static final class List {
        static java.util.List<String> of(String... a) { return java.util.List.of(a); }
    }

    @Test
    void setExistingAttributeKeepsQuotesAndEverythingElse() throws Exception {
        XmlSource s = src(DOC);
        String out = valid(XmlPatcher.setAttr(s, byId(s, "p"), "width", "250"));
        assertTrue(out.contains("<panel id=\"p\" width='250' anchor=\"center\">"), out);
        assertEquals(DOC.replace("width='100'", "width='250'"), out, "바뀐 곳 외에는 한 글자도 달라지지 않는다");
    }

    @Test
    void addAttributeBeforeCloseForBothSelfClosingAndNormalTags() throws Exception {
        XmlSource s = src(DOC);
        String a = valid(XmlPatcher.setAttr(s, byId(s, "l"), "color", "#FF0000"));
        assertTrue(a.contains("<label id=\"l\" text=\"a > b\" color=\"#FF0000\" />"), a);
        String b = valid(XmlPatcher.setAttr(s, byId(s, "b"), "width", "40"));
        assertTrue(b.contains("<button id=\"b\" text=\"OK\" width=\"40\"/>"), b);
        String c = valid(XmlPatcher.setAttr(s, byId(s, "p"), "height", "60"));
        assertTrue(c.contains("<panel id=\"p\" width='100' anchor=\"center\" height=\"60\">"), c);
        String bare = valid(XmlPatcher.setAttr(src("<screen><spacer/></screen>"), src("<screen><spacer/></screen>").element(1), "height", "5"));
        assertEquals("<screen><spacer height=\"5\"/></screen>", bare);
    }

    @Test
    void removeAttributeAndMissingAttributeIsNoOp() throws Exception {
        XmlSource s = src(DOC);
        String out = valid(XmlPatcher.setAttr(s, byId(s, "p"), "anchor", null));
        assertTrue(out.contains("<panel id=\"p\" width='100'>"), out);
        assertEquals(DOC, XmlPatcher.setAttr(s, byId(s, "p"), "nothing", null));
    }

    @Test
    void valuesAreEscapedPerQuoteStyle() throws Exception {
        XmlSource s = src(DOC);
        String out = valid(XmlPatcher.setAttr(s, byId(s, "l"), "text", "He said \"hi\" & <left>"));
        assertEquals("He said \"hi\" & <left>", src(out).attrValue(byId(src(out), "l"), "text"));
        String single = valid(XmlPatcher.setAttr(s, byId(s, "p"), "width", "it's"));    // 홑따옴표 속성
        assertEquals("it's", src(single).attrValue(byId(src(single), "p"), "width"));
    }

    // ---- 내용 ----

    @Test
    void setContentOnSelfClosingAndOpenElements() throws Exception {
        XmlSource s = src("<screen><label id='a' text='x'/><button id='b'>Old</button></screen>");
        String a = valid(XmlPatcher.setContent(s, byId(s, "a"), "A & B"));
        assertTrue(a.contains("<label id='a' text='x'>A &amp; B</label>"), a);
        String b = valid(XmlPatcher.setContent(s, byId(s, "b"), "New"));
        assertTrue(b.contains("<button id='b'>New</button>"), b);
        assertThrows(IllegalArgumentException.class, () -> XmlPatcher.setContent(src(DOC), src(DOC).element(2), "x"));
    }

    // ---- 삭제 ----

    @Test
    void removeElementRemovesWholeLineAndKeepsComments() throws Exception {
        XmlSource s = src(DOC);
        String out = valid(XmlPatcher.removeElement(s, byId(s, "l")));
        assertEquals(DOC.replace("    <label id=\"l\" text=\"a > b\" />\n", ""), out);
        assertTrue(out.contains("<!-- 안쪽 주석 -->"));
        assertThrows(IllegalArgumentException.class, () -> XmlPatcher.removeElement(s, s.root()));
    }

    @Test
    void removeInlineElementKeepsTheLine() throws Exception {
        XmlSource s = src("<screen><a id='x'/><b/></screen>");
        assertEquals("<screen><b/></screen>", valid(XmlPatcher.removeElement(s, byId(s, "x"))));
    }

    // ---- 삽입 ----

    @Test
    void insertChildIndentsLikeSiblings() throws Exception {
        XmlSource s = src(DOC);
        Element panel = byId(s, "p");
        String last = valid(XmlPatcher.insertChild(s, panel, 99, "<spacer id=\"s\"/>"));
        assertTrue(last.contains("    <button id=\"b\" text=\"OK\"/>\n    <spacer id=\"s\"/>\n  </panel>"), last);
        String first = valid(XmlPatcher.insertChild(s, panel, 0, "<spacer id=\"s\"/>"));
        assertTrue(first.contains("<!-- 안쪽 주석 -->\n    <spacer id=\"s\"/>\n    <label"), "첫 자식 요소 앞에 들어가고 주석은 제자리: " + first);
        String mid = valid(XmlPatcher.insertChild(s, panel, 1, "<spacer id=\"s\"/>"));
        assertTrue(mid.indexOf("id=\"s\"") > mid.indexOf("id=\"l\"") && mid.indexOf("id=\"s\"") < mid.indexOf("id=\"b\""), mid);
    }

    @Test
    void insertIntoSelfClosingAndEmptyParents() throws Exception {
        XmlSource s = src("<screen>\n  <column id=\"c\" gap=\"2\" />\n  <row id=\"r\"></row>\n</screen>");
        String a = valid(XmlPatcher.insertChild(s, byId(s, "c"), 0, "<label text=\"x\"/>"));
        assertTrue(a.contains("<column id=\"c\" gap=\"2\">\n    <label text=\"x\"/>\n  </column>"), a);
        String b = valid(XmlPatcher.insertChild(s, byId(s, "r"), 0, "<label text=\"y\"/>"));
        assertTrue(b.contains("<row id=\"r\">\n    <label text=\"y\"/>\n  </row>"), b);
    }

    @Test
    void insertMultilineBlockIsReindented() throws Exception {
        XmlSource s = src("<screen>\n  <panel id=\"p\"/>\n</screen>");
        String out = valid(XmlPatcher.insertChild(s, byId(s, "p"), 0, "<column>\n  <label text=\"a\"/>\n</column>"));
        assertTrue(out.contains("  <panel id=\"p\">\n    <column>\n      <label text=\"a\"/>\n    </column>\n  </panel>"), out);
    }

    // ---- 복제 ----

    @Test
    void duplicateInsertsAfterOriginalWithUniqueId() throws Exception {
        XmlSource s = src(DOC);
        String once = valid(XmlPatcher.duplicate(s, byId(s, "b")));
        XmlSource s1 = src(once);
        assertEquals(java.util.List.of("b", "b_copy"), s1.all().stream().map(e -> s1.attrValue(e, "id")).filter(i -> i != null && i.startsWith("b")).toList());
        String twice = valid(XmlPatcher.duplicate(s1, byId(s1, "b")));
        assertTrue(twice.contains("id=\"b_copy2\""), twice);
        assertThrows(IllegalArgumentException.class, () -> XmlPatcher.duplicate(s, s.root()));
    }

    @Test
    void duplicateMultilineElementKeepsRelativeIndent() throws Exception {
        XmlSource s = src("<screen>\n  <column id=\"c\">\n    <label text=\"a\"/>\n  </column>\n</screen>");
        String out = valid(XmlPatcher.duplicate(s, byId(s, "c")));
        assertTrue(out.contains("  </column>\n  <column id=\"c_copy\">\n    <label text=\"a\"/>\n  </column>\n</screen>"), out);
    }

    // ---- 이동 ----

    @Test
    void moveReordersWithinSameParentAndAcrossParents() throws Exception {
        XmlSource s = src("<screen>\n  <column id=\"c\">\n    <a id=\"a\"/>\n    <b id=\"b\"/>\n    <c id=\"cc\"/>\n  </column>\n  <row id=\"r\"/>\n</screen>");
        // a 를 맨 뒤로
        String o1 = valid(XmlPatcher.move(s, byId(s, "a"), byId(s, "c"), 3));
        XmlSource s1 = src(o1);
        assertEquals(java.util.List.of("b", "cc", "a"), childIds(s1, "c"));
        // 같은 위치(앞)로 옮기면 그대로
        String same = valid(XmlPatcher.move(s, byId(s, "b"), byId(s, "c"), 1));
        assertEquals(java.util.List.of("a", "b", "cc"), childIds(src(same), "c"));
        // 다른 부모(row)로
        String o2 = valid(XmlPatcher.move(s, byId(s, "b"), byId(s, "r"), 0));
        assertEquals(java.util.List.of("a", "cc"), childIds(src(o2), "c"));
        assertEquals(java.util.List.of("b"), childIds(src(o2), "r"));
        // 자기 자신 안으로는 불가
        assertThrows(IllegalArgumentException.class, () -> XmlPatcher.move(s, byId(s, "c"), byId(s, "a"), 0));
    }

    private static java.util.List<String> childIds(XmlSource s, String parentId) {
        Element p = byId(s, parentId);
        return p.children().stream().map(e -> s.attrValue(e, "id")).toList();
    }

    @Test
    void movingBeforeParentInDocumentOrderKeepsIndicesConsistent() throws Exception {
        // 이동할 요소가 새 부모보다 문서 앞에 있는 경우(제거로 번호가 당겨지는 경우)
        XmlSource s = src("<screen>\n  <label id=\"x\"/>\n  <panel id=\"p\"/>\n</screen>");
        String out = valid(XmlPatcher.move(s, byId(s, "x"), byId(s, "p"), 0));
        XmlSource r = src(out);
        assertEquals(java.util.List.of("x"), childIds(r, "p"));
        assertNotNull(byId(r, "p"));
        assertNull(r.root().attr("x"));
    }
}
