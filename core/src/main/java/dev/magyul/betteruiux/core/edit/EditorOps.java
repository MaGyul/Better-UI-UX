package dev.magyul.betteruiux.core.edit;

import dev.magyul.betteruiux.core.edit.XmlSource.Element;
import dev.magyul.betteruiux.core.style.Stylesheet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** 에디터가 하는 편집 하나하나를 "텍스트 → 새 텍스트" 변환으로 구현한다. */
public final class EditorOps {
    private EditorOps() {}

    /** 새 요소를 넣은 결과와 그 요소의 번호(전위 순회). */
    public record Added(String text, int index) {}

    // ---- 속성/스타일 ----

    /**
     * 스타일 속성 하나를 <b>효과가 있는 자리</b>에 기록한다(우선순위: 인라인 style &gt; 스타일시트 &gt; XML 속성).
     * <ol>
     *   <li>인라인 {@code style} 에 이미 있으면 거기를 고친다</li>
     *   <li>XML 속성으로 이미 있으면 그 속성을 고친다(원본 모양 유지)</li>
     *   <li>값이 지워지는 경우(null)면 할 일 없음</li>
     *   <li>스타일시트/상속이 값을 정하고 있으면(definedElsewhere) 인라인 style 로 덮어쓴다 — 속성은 시트에 지므로</li>
     *   <li>그 외에는 새 XML 속성으로 추가한다</li>
     * </ol>
     */
    public static String setProp(XmlSource src, Element el, String prop, String value, boolean definedElsewhere) {
        String styleAttr = src.attrValue(el, "style");
        Map<String, String> decls = declarations(styleAttr);
        if (decls.containsKey(prop)) {
            if (value == null) decls.remove(prop);
            else decls.put(prop, value);
            return writeStyle(src, el, decls);
        }
        if (el.attr(prop) != null) return XmlPatcher.setAttr(src, el, prop, value);
        if (value == null) return src.text();
        if (definedElsewhere) {
            decls.put(prop, value);
            return writeStyle(src, el, decls);
        }
        return XmlPatcher.setAttr(src, el, prop, value);
    }

    /** 여러 속성을 차례로 적용한다(적용할 때마다 위치가 바뀌므로 매번 다시 파싱). */
    public static String setProps(String text, int elementIndex, Map<String, String> props,
                                  Predicate<String> definedElsewhere) {
        String t = text;
        for (Map.Entry<String, String> e : props.entrySet()) {
            XmlSource s = XmlSource.parse(t);
            t = setProp(s, s.element(elementIndex), e.getKey(), e.getValue(), definedElsewhere.test(e.getKey()));
        }
        return t;
    }

    private static Map<String, String> declarations(String styleAttr) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Stylesheet.Declaration d : Stylesheet.parseDeclarations(styleAttr)) m.put(d.prop(), d.value());
        return m;
    }

    private static String writeStyle(XmlSource src, Element el, Map<String, String> decls) {
        if (decls.isEmpty()) return XmlPatcher.setAttr(src, el, "style", null);
        StringBuilder sb = new StringBuilder();
        decls.forEach((k, v) -> sb.append(sb.isEmpty() ? "" : "; ").append(k).append(": ").append(v));
        return XmlPatcher.setAttr(src, el, "style", sb.toString());
    }

    /**
     * 텍스트 편집. {@code text} 속성이 있으면 그것을, 없고 본문이 있으면 본문을, 둘 다 없으면 {@code text} 속성을 추가한다.
     * (본문과 자식 요소가 섞여 있으면 본문은 건드리지 않고 text 속성으로 쓴다.)
     */
    public static String setText(XmlSource src, Element el, String value) {
        if (el.attr("text") != null) return XmlPatcher.setAttr(src, el, "text", value);
        boolean hasContent = !el.selfClosing() && el.children().isEmpty()
                && !src.text().substring(contentStart(src, el), contentEnd(src, el)).isBlank();
        if (hasContent) return XmlPatcher.setContent(src, el, value);
        return XmlPatcher.setAttr(src, el, "text", value);
    }

    private static int contentStart(XmlSource s, Element e) { return s.text().indexOf('>', e.start()) + 1; }

    private static int contentEnd(XmlSource s, Element e) { return s.text().lastIndexOf("</", e.end()); }

    // ---- 추가 ----

    /** 도구 모음에서 추가할 수 있는 요소와 기본 모양. */
    public static String snippet(String tag) {
        return switch (tag) {
            case "label" -> "<label text=\"Label\"/>";
            case "button" -> "<button text=\"Button\"/>";
            case "panel" -> "<panel width=\"80\" height=\"40\" background=\"#80000000\"/>";
            case "column" -> "<column gap=\"4\"/>";
            case "row" -> "<row gap=\"4\"/>";
            case "image" -> "<image src=\"minecraft:textures/item/diamond.png\" tex=\"16 16\" width=\"16\" height=\"16\"/>";
            case "spacer" -> "<spacer width=\"8\" height=\"8\"/>";
            default -> throw new IllegalArgumentException("추가할 수 없는 요소: " + tag);
        };
    }

    /** parent 의 pos 번째 자식 자리에 tag 요소를 추가한다. */
    public static Added addChild(String text, int parentIndex, int pos, String tag) {
        XmlSource s = XmlSource.parse(text);
        Element parent = s.element(parentIndex);
        int clamped = Math.max(0, Math.min(pos, parent.children().size()));
        String out = XmlPatcher.insertChild(s, parent, clamped, snippet(tag));
        XmlSource s2 = XmlSource.parse(out);
        Element p2 = s2.element(parentIndex);
        return new Added(out, p2.children().get(Math.min(clamped, p2.children().size() - 1)).index());
    }

    /** 요소 원문(첫 줄 들여쓰기를 덜어낸 상태). 복사에 쓴다. */
    public static String elementXml(String text, int index) {
        XmlSource s = XmlSource.parse(text);
        return XmlPatcher.dedentBlock(text, s.element(index));
    }

    /** parent 의 pos 번째 자식 자리에 임의의 요소 XML 을 넣는다. */
    public static Added insertXml(String text, int parentIndex, int pos, String xml) {
        XmlSource s = XmlSource.parse(text);
        Element parent = s.element(parentIndex);
        int clamped = Math.max(0, Math.min(pos, parent.children().size()));
        String out = XmlPatcher.insertChild(s, parent, clamped, xml);
        XmlSource s2 = XmlSource.parse(out);
        Element p2 = s2.element(parentIndex);
        return new Added(out, p2.children().get(Math.min(clamped, p2.children().size() - 1)).index());
    }

    /** 붙여넣을 XML 의 id 가 문서에 이미 있으면 {@code _copy} 를 붙여 겹치지 않게 한다. */
    public static String uniqueIds(String text, String xml) {
        XmlSource s = XmlSource.parse(text);
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Element el : s.all()) {
            String v = s.attrValue(el, "id");
            if (v != null) used.add(v);
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\sid\\s*=\\s*)([\"'])([^\"']*)\\2").matcher(xml);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String id = m.group(3);
            String use = id;
            if (used.contains(id)) {
                use = id + "_copy";
                for (int i = 2; used.contains(use); i++) use = id + "_copy" + i;
            }
            used.add(use);
            out.append(xml, last, m.start()).append(m.group(1)).append(m.group(2)).append(use).append(m.group(2));
            last = m.end();
        }
        return out.append(xml, last, xml.length()).toString();
    }

    private static final String[] POSITION_ATTRS = {"x", "y", "anchor"};

    /**
     * 요소를 {@code tag} 컨테이너로 감싼다. 자유 배치 부모 안에서 보이던 자리가 유지되도록 x/y/anchor 는 감싼 컨테이너로 옮긴다.
     * 감싼 요소의 번호(안쪽 요소가 아니라 컨테이너)를 돌려준다.
     */
    public static Added wrap(String text, int index, String tag) {
        if (!java.util.Set.of("row", "column", "panel", "stack").contains(tag)) throw new IllegalArgumentException("감쌀 수 없는 요소: " + tag);
        XmlSource s = XmlSource.parse(text);
        Element e = s.element(index);
        if (e.parent() == null) throw new IllegalArgumentException("루트 요소는 감쌀 수 없습니다");
        Element parent = e.parent();
        int pos = parent.children().indexOf(e);
        String block = XmlPatcher.dedentBlock(text, e);
        StringBuilder attrs = new StringBuilder();
        XmlSource inner = XmlSource.parse(block);
        String innerXml = block;
        for (String name : POSITION_ATTRS) {
            String v = inner.attrValue(inner.root(), name);
            if (v == null) continue;
            attrs.append(' ').append(name).append("=\"").append(v).append('"');
            innerXml = XmlPatcher.setAttr(inner, inner.root(), name, null);
            inner = XmlSource.parse(innerXml);
        }
        StringBuilder wrapped = new StringBuilder("<" + tag + attrs + ">\n");
        for (String line : innerXml.split("\n", -1)) wrapped.append(line.isBlank() ? line : "  " + line).append('\n');
        wrapped.append("</").append(tag).append('>');
        String removed = XmlPatcher.removeElement(s, e);
        XmlSource s2 = XmlSource.parse(removed);
        Element p2 = s2.element(parent.index());
        String out = XmlPatcher.insertChild(s2, p2, pos, wrapped.toString());
        XmlSource s3 = XmlSource.parse(out);
        return new Added(out, s3.element(parent.index()).children().get(pos).index());
    }

    /** 컨테이너를 풀어 자식들을 그 자리에 올린다. 첫 자식의 번호를 돌려준다. */
    public static Added unwrap(String text, int index) {
        XmlSource s = XmlSource.parse(text);
        Element e = s.element(index);
        if (e.parent() == null) throw new IllegalArgumentException("루트 요소는 풀 수 없습니다");
        if (e.children().isEmpty()) throw new IllegalArgumentException("자식이 없는 요소는 풀 수 없습니다");
        Element parent = e.parent();
        int pos = parent.children().indexOf(e);
        java.util.List<String> blocks = new java.util.ArrayList<>();
        for (Element c : e.children()) blocks.add(XmlPatcher.dedentBlock(text, c));
        String out = XmlPatcher.removeElement(s, e);
        for (int i = 0; i < blocks.size(); i++) {
            XmlSource cur = XmlSource.parse(out);
            out = XmlPatcher.insertChild(cur, cur.element(parent.index()), pos + i, blocks.get(i));
        }
        XmlSource fin = XmlSource.parse(out);
        return new Added(out, fin.element(parent.index()).children().get(pos).index());
    }

    /** 요소를 삭제한 뒤, 선택을 옮길 번호(같은 부모의 이웃, 없으면 부모)를 함께 돌려준다. */
    public static Added delete(String text, int index) {
        XmlSource s = XmlSource.parse(text);
        Element e = s.element(index);
        Element parent = e.parent();
        int pos = parent.children().indexOf(e);
        String out = XmlPatcher.removeElement(s, e);
        XmlSource s2 = XmlSource.parse(out);
        Element p2 = s2.element(parent.index());
        int next = p2.children().isEmpty() ? p2.index() : p2.children().get(Math.min(pos, p2.children().size() - 1)).index();
        return new Added(out, next);
    }

    /** 복제하고 복제본의 번호를 돌려준다. */
    public static Added duplicate(String text, int index) {
        XmlSource s = XmlSource.parse(text);
        Element e = s.element(index);
        int pos = e.parent().children().indexOf(e);
        String out = XmlPatcher.duplicate(s, e);
        XmlSource s2 = XmlSource.parse(out);
        return new Added(out, s2.element(e.parent().index()).children().get(pos + 1).index());
    }

    /** e 를 newParent 의 pos 번째 자리(제거 전 기준)로 옮기고 옮겨진 요소의 번호를 돌려준다. */
    public static Added move(String text, int index, int newParentIndex, int pos) {
        XmlSource s = XmlSource.parse(text);
        Element e = s.element(index);
        Element np = s.element(newParentIndex);
        int adjusted = pos;
        if (np == e.parent() && np.children().indexOf(e) < pos) adjusted = pos - 1;
        String out = XmlPatcher.move(s, e, np, pos);
        XmlSource s2 = XmlSource.parse(out);
        int parentAfter = newParentIndex - (index < newParentIndex ? XmlPatcher.subtreeSize(e) : 0);
        Element p2 = s2.element(parentAfter);
        return new Added(out, p2.children().get(Math.min(adjusted, p2.children().size() - 1)).index());
    }
}
