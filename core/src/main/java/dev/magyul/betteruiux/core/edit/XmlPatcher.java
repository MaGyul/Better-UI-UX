package dev.magyul.betteruiux.core.edit;

import dev.magyul.betteruiux.core.edit.XmlSource.Attr;
import dev.magyul.betteruiux.core.edit.XmlSource.Element;
import java.util.HashSet;
import java.util.Set;

/**
 * XML 원본 텍스트를 <b>최소한으로</b> 고친다: 바뀌는 부분 밖의 모든 문자(주석, 들여쓰기, 속성 순서, 따옴표 종류)는 그대로 둔다.
 * 각 메서드는 새 텍스트를 돌려주고 호출 후에는 {@link XmlSource#parse} 를 다시 해야 한다(위치가 바뀌므로).
 */
public final class XmlPatcher {
    private static final String INDENT_UNIT = "  ";

    private XmlPatcher() {}

    // ---- 속성 ----

    /** @param value null 이면 속성을 제거한다. 없던 속성은 시작 태그 끝에 추가한다. */
    public static String setAttr(XmlSource s, Element e, String name, String value) {
        String t = s.text();
        Attr a = e.attr(name);
        if (a != null) {
            if (value == null) return t.substring(0, a.start) + t.substring(a.end);
            return t.substring(0, a.valueStart) + XmlSource.escape(value, a.quote) + t.substring(a.valueEnd);
        }
        if (value == null) return t;
        int pos = tagCloseTrimmed(t, e);
        String ins = " " + name + "=\"" + XmlSource.escape(value, '"') + "\"";
        return t.substring(0, pos) + ins + t.substring(pos);
    }

    /** 시작 태그를 닫는 '>' / '/>' 바로 앞(그 앞의 공백은 건너뜀) 위치. */
    private static int tagCloseTrimmed(String t, Element e) {
        int pos = e.tagCloseStart;
        while (pos > e.start && Character.isWhitespace(t.charAt(pos - 1))) pos--;
        return pos;
    }

    // ---- 내용 ----

    /** 자식 요소가 없는 요소의 텍스트 내용을 바꾼다. */
    public static String setContent(XmlSource s, Element e, String content) {
        if (!e.children.isEmpty()) throw new IllegalArgumentException("자식 요소가 있는 요소의 내용은 바꿀 수 없습니다");
        String t = s.text();
        String body = content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        if (e.selfClosing) {
            int pos = tagCloseTrimmed(t, e);
            return t.substring(0, pos) + ">" + body + "</" + e.tag + ">" + t.substring(e.end);
        }
        return t.substring(0, e.startTagEnd) + body + t.substring(e.endTagStart);
    }

    // ---- 삭제 ----

    /** 요소를 지운다. 그 요소가 한 줄을 혼자 차지하면 줄 전체를 지운다. */
    public static String removeElement(XmlSource s, Element e) {
        if (e.parent == null) throw new IllegalArgumentException("루트 요소는 삭제할 수 없습니다");
        String t = s.text();
        int start = e.start, end = e.end;
        int ls = lineStart(t, start);
        boolean ownLineStart = isBlank(t, ls, start);
        if (ownLineStart) {
            start = ls;
            int le = lineEnd(t, end);
            if (isBlank(t, end, le)) end = Math.min(t.length(), le + 1);
        }
        return t.substring(0, start) + t.substring(end);
    }

    // ---- 삽입 ----

    /**
     * parent 의 pos 번째 자식 자리에 {@code xml} (요소 하나)을 넣는다. pos 가 자식 수 이상이면 맨 뒤.
     * 들여쓰기는 형제/부모에 맞춘다. 자기닫힘 부모({@code <column/>})는 여는/닫는 태그 쌍으로 바꾼다.
     */
    public static String insertChild(XmlSource s, Element parent, int pos, String xml) {
        String t = s.text();
        String parentIndent = lineIndent(t, parent.start);
        String childIndent = parentIndent + INDENT_UNIT;
        if (!parent.children.isEmpty()) {
            Element first = parent.children.get(0);
            int ls = lineStart(t, first.start);
            if (isBlank(t, ls, first.start)) childIndent = t.substring(ls, first.start);
        }
        String block = reindent(xml.strip(), childIndent);

        if (parent.selfClosing) {
            int p = tagCloseTrimmed(t, parent);
            return t.substring(0, p) + ">\n" + childIndent + block + "\n" + parentIndent + "</" + parent.tag + ">"
                    + t.substring(parent.end);
        }
        if (parent.children.isEmpty()) {
            String content = t.substring(parent.startTagEnd, parent.endTagStart);
            String add = "\n" + childIndent + block + "\n" + parentIndent;
            if (content.isBlank()) return t.substring(0, parent.startTagEnd) + add + t.substring(parent.endTagStart);
            return t.substring(0, parent.endTagStart) + add + t.substring(parent.endTagStart);
        }
        if (pos >= parent.children.size()) {
            Element last = parent.children.get(parent.children.size() - 1);
            return t.substring(0, last.end) + "\n" + childIndent + block + t.substring(last.end);
        }
        Element before = parent.children.get(Math.max(0, pos));
        return t.substring(0, before.start) + block + "\n" + childIndent + t.substring(before.start);
    }

    /** 요소를 바로 뒤에 복제한다. id 가 있으면 문서 안에서 겹치지 않게 {@code _copy} 를 붙인다. */
    public static String duplicate(XmlSource s, Element e) {
        if (e.parent == null) throw new IllegalArgumentException("루트 요소는 복제할 수 없습니다");
        String block = dedentBlock(s.text(), e);
        XmlSource copy = XmlSource.parse(block);
        String id = copy.attrValue(copy.root(), "id");
        if (id != null) {
            block = XmlPatcher.setAttr(copy, copy.root(), "id", uniqueId(s, id));
        }
        int at = e.parent.children.indexOf(e) + 1;
        return insertChild(s, e.parent, at, block);
    }

    static String uniqueId(XmlSource s, String base) {
        Set<String> used = new HashSet<>();
        for (Element el : s.all()) {
            String v = s.attrValue(el, "id");
            if (v != null) used.add(v);
        }
        String candidate = base + "_copy";
        for (int i = 2; used.contains(candidate); i++) candidate = base + "_copy" + i;
        return candidate;
    }

    /** e 를 newParent 의 pos 번째 자식 자리(제거 전 기준)로 옮긴다. */
    public static String move(XmlSource s, Element e, Element newParent, int pos) {
        if (e.parent == null) throw new IllegalArgumentException("루트 요소는 옮길 수 없습니다");
        if (isInside(e, newParent)) throw new IllegalArgumentException("요소를 자기 자신 안으로 옮길 수 없습니다");
        String block = dedentBlock(s.text(), e);
        int adjusted = pos;
        if (newParent == e.parent && e.parent.children.indexOf(e) < pos) adjusted = pos - 1;
        String removed = removeElement(s, e);
        XmlSource s2 = XmlSource.parse(removed);
        int parentIndex = newParent.index - (e.index < newParent.index ? subtreeSize(e) : 0);
        Element np = s2.element(parentIndex);
        return insertChild(s2, np, Math.min(adjusted, np.children.size()), block);
    }

    // ---- 도우미 ----

    /** a 가 b 와 같거나 b 의 조상이면 true. */
    private static boolean isInside(Element a, Element b) {
        for (Element x = b; x != null; x = x.parent) if (x == a) return true;
        return false;
    }

    static int subtreeSize(Element e) {
        int n = 1;
        for (Element c : e.children) n += subtreeSize(c);
        return n;
    }

    private static int lineStart(String t, int pos) {
        int i = t.lastIndexOf('\n', pos - 1);
        return i < 0 ? 0 : i + 1;
    }

    private static int lineEnd(String t, int pos) {
        int i = t.indexOf('\n', pos);
        return i < 0 ? t.length() : i;
    }

    private static boolean isBlank(String t, int from, int to) {
        for (int i = from; i < to; i++) if (!Character.isWhitespace(t.charAt(i))) return false;
        return true;
    }

    /** pos 가 속한 줄의 앞쪽 공백. */
    private static String lineIndent(String t, int pos) {
        int ls = lineStart(t, pos);
        int j = ls;
        while (j < t.length() && (t.charAt(j) == ' ' || t.charAt(j) == '\t')) j++;
        return t.substring(ls, j);
    }

    /** 요소 원문을 꺼내 첫 줄의 들여쓰기만큼 이후 줄에서 덜어낸다(상대 들여쓰기만 남김). */
    static String dedentBlock(String t, Element e) {
        String raw = t.substring(e.start, e.end);
        String indent = lineIndent(t, e.start);
        String[] lines = raw.split("\n", -1);
        StringBuilder sb = new StringBuilder(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            String l = lines[i];
            sb.append('\n').append(l.startsWith(indent) ? l.substring(indent.length()) : l);
        }
        return sb.toString();
    }

    /** 첫 줄은 그대로, 이후 줄에는 indent 를 붙인다(빈 줄 제외). */
    private static String reindent(String block, String indent) {
        String[] lines = block.split("\n", -1);
        StringBuilder sb = new StringBuilder(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            sb.append('\n');
            if (!lines[i].isBlank()) sb.append(indent).append(lines[i]);
            else sb.append(lines[i]);
        }
        return sb.toString();
    }
}
