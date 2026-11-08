package dev.magyul.betteruiux.core.doc;

import dev.magyul.betteruiux.core.layout.LayoutBox;
import dev.magyul.betteruiux.core.style.Selector;
import dev.magyul.betteruiux.core.style.Stylesheet;
import dev.magyul.betteruiux.core.xml.UiNode;
import dev.magyul.betteruiux.core.xml.UiParseException;
import dev.magyul.betteruiux.core.xml.UiParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 모드/스크립트가 문서 요소를 읽고 바꾸는 핸들(DOM 의 Element 에 해당). {@link UiDocument#query} 로 얻는다.
 * 변경은 다음 프레임에 스타일/레이아웃이 다시 계산되어 반영된다. 마인크래프트 렌더 스레드에서 호출할 것.
 */
public final class UiElement {
    private final UiDocument doc;
    private final UiNode node;

    UiElement(UiDocument doc, UiNode node) {
        this.doc = doc;
        this.node = node;
    }

    public UiNode node() { return node; }

    public UiDocument document() { return doc; }

    public String tag() { return node.tag(); }

    public String id() { return node.attr("id"); }

    @Override public boolean equals(Object o) { return o instanceof UiElement e && e.node == node && e.doc == doc; }

    @Override public int hashCode() { return System.identityHashCode(node); }

    // ---- 속성 / 텍스트 / 클래스 ----

    public String attr(String name) { return node.attr(name); }

    /** @param value null 이면 속성을 제거한다. */
    public UiElement setAttr(String name, String value) {
        if (value == null) node.attributes().remove(name);
        else node.attributes().put(name, value);
        doc.markDirty();
        return this;
    }

    /** label/button 의 텍스트({@code text} 속성 우선, 없으면 본문). */
    public String text() {
        String t = node.attr("text");
        return t != null ? t : node.text().trim();
    }

    public UiElement setText(String text) { return setAttr("text", text); }

    public boolean hasClass(String name) { return classes().contains(name); }

    public UiElement addClass(String name) {
        List<String> c = classes();
        if (!c.contains(name)) {
            c.add(name);
            writeClasses(c);
        }
        return this;
    }

    public UiElement removeClass(String name) {
        List<String> c = classes();
        if (c.remove(name)) writeClasses(c);
        return this;
    }

    public UiElement toggleClass(String name, boolean on) { return on ? addClass(name) : removeClass(name); }

    private List<String> classes() {
        String cls = node.attr("class");
        List<String> c = new ArrayList<>();
        if (cls != null && !cls.isBlank()) c.addAll(List.of(cls.trim().split("\\s+")));
        return c;
    }

    private void writeClasses(List<String> c) { setAttr("class", c.isEmpty() ? null : String.join(" ", c)); }

    // ---- 스타일 ----

    /** 인라인 스타일(가장 높은 우선순위)을 설정한다. @param value null 이면 해당 속성 제거. */
    public UiElement setStyle(String prop, String value) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Stylesheet.Declaration d : Stylesheet.parseDeclarations(node.attr("style"))) m.put(d.prop(), d.value());
        if (value == null) m.remove(prop.toLowerCase());
        else m.put(prop.toLowerCase(), value);
        if (m.isEmpty()) {
            node.attributes().remove("style");
        } else {
            StringBuilder sb = new StringBuilder();
            m.forEach((k, v) -> sb.append(k).append(": ").append(v).append("; "));
            node.attributes().put("style", sb.toString().trim());
        }
        doc.markDirty();
        return this;
    }

    /** 해석된(computed) 스타일 값. 레이아웃 전이면 null. */
    public String style(String prop) {
        LayoutBox b = box();
        return b == null ? null : b.style.get(prop);
    }

    public UiElement setVisible(boolean visible) { return setStyle("visible", visible ? null : "false"); }

    public UiElement setEnabled(boolean enabled) { return setAttr("enabled", enabled ? null : "false"); }

    // ---- 트리 ----

    public UiElement parent() { return node.parent() == null ? null : doc.element(node.parent()); }

    public List<UiElement> children() {
        List<UiElement> r = new ArrayList<>();
        for (UiNode c : node.children()) r.add(doc.element(c));
        return r;
    }

    public UiElement appendChild(UiElement child) {
        node.append(child.node);
        doc.markDirty();
        return child;
    }

    /** XML 조각({@code <label text="x"/>})을 파싱해 자식으로 붙이고 새 요소를 돌려준다. */
    public UiElement appendChild(String xml) throws UiParseException {
        UiNode n = UiParser.parse(xml);
        node.append(n);
        doc.markDirty();
        return doc.element(n);
    }

    /** 문서에서 이 요소를 제거한다. */
    public void remove() {
        node.detach();
        doc.markDirty();
    }

    public UiElement query(String selector) { return doc.queryFrom(node, selector, false).stream().findFirst().orElse(null); }

    public List<UiElement> queryAll(String selector) { return doc.queryFrom(node, selector, false); }

    /** 이 요소 또는 조상 중 선택자에 맞는 가장 가까운 요소. */
    public UiElement closest(String selector) {
        Selector s = Selector.parse(selector);
        for (UiNode n = node; n != null; n = n.parent()) if (s.matches(n)) return doc.element(n);
        return null;
    }

    // ---- 이벤트 ----

    /** @return 핸들러 등록 해제 함수 */
    public Runnable on(UiEvent.Type type, UiEvent.Handler handler) { return doc.addHandler(node, null, type, handler); }

    // ---- 레이아웃 결과 ----

    /** 마지막 레이아웃 결과. 아직 레이아웃되지 않았거나 문서에서 제거됐으면 null. */
    public LayoutBox box() { return doc.boxOf(node); }

    public int x() { LayoutBox b = box(); return b == null ? 0 : b.x; }

    public int y() { LayoutBox b = box(); return b == null ? 0 : b.y; }

    public int width() { LayoutBox b = box(); return b == null ? 0 : b.w; }

    public int height() { LayoutBox b = box(); return b == null ? 0 : b.h; }
}
