package dev.magyul.betteruiux.core.edit;

import java.util.ArrayList;
import java.util.List;

/**
 * XML 원본 텍스트의 "위치 지도". 에디터가 주석/들여쓰기를 보존한 채 텍스트를 고칠 수 있도록, 각 요소와 속성이
 * 원본의 어느 문자 범위에 있는지 기록한다. 런타임 파서(StAX)와 같은 문서에서 같은 순서(전위 순회)로 요소를 세므로
 * {@link Element#index()} 가 {@code UiNode.sourceIndex()} 와 일치한다.
 * 문법 검증은 하지 않는다(이미 StAX 로 검증된 텍스트에 쓴다). 구조가 어긋나면 {@link IllegalArgumentException}.
 */
public final class XmlSource {
    /** 속성 하나. {@code start} 는 앞의 공백부터(삭제 시 함께 지운다), {@code end} 는 닫는 따옴표 다음. */
    public static final class Attr {
        public final String name;
        public final int start, end, valueStart, valueEnd;
        public final char quote;

        Attr(String name, int start, int end, int valueStart, int valueEnd, char quote) {
            this.name = name;
            this.start = start;
            this.end = end;
            this.valueStart = valueStart;
            this.valueEnd = valueEnd;
            this.quote = quote;
        }
    }

    public static final class Element {
        public final String tag;
        /** '&lt;' 위치. */
        public final int start;
        /** 시작 태그의 '&gt;' (또는 '/&gt;') 다음 위치. */
        int startTagEnd;
        /** 시작 태그를 닫는 '&gt;' 또는 '/&gt;' 의 첫 글자 위치. */
        int tagCloseStart;
        boolean selfClosing;
        /** 닫는 태그 '&lt;/' 위치(자기닫힘이면 -1). */
        int endTagStart = -1;
        /** 요소 전체의 끝(마지막 '&gt;' 다음). */
        int end;
        final List<Attr> attrs = new ArrayList<>();
        final List<Element> children = new ArrayList<>();
        Element parent;
        int index;

        Element(String tag, int start) {
            this.tag = tag;
            this.start = start;
        }

        public int start() { return start; }

        public int end() { return end; }

        public int index() { return index; }

        public boolean selfClosing() { return selfClosing; }

        public Element parent() { return parent; }

        public List<Element> children() { return children; }

        public List<Attr> attrs() { return attrs; }

        public Attr attr(String name) {
            for (Attr a : attrs) if (a.name.equals(name)) return a;
            return null;
        }
    }

    private final String text;
    private final Element root;
    private final List<Element> all = new ArrayList<>();

    private XmlSource(String text, Element root) {
        this.text = text;
        this.root = root;
        index(root);
    }

    private void index(Element e) {
        e.index = all.size();
        all.add(e);
        for (Element c : e.children) index(c);
    }

    public String text() { return text; }

    public Element root() { return root; }

    /** 전위 순회 순서의 모든 요소. */
    public List<Element> all() { return all; }

    public Element element(int index) {
        if (index < 0 || index >= all.size()) throw new IllegalArgumentException("요소 번호 범위 밖: " + index);
        return all.get(index);
    }

    /** 속성 값(따옴표 안, 이스케이프된 그대로가 아니라 해석된 값). 없으면 null. */
    public String attrValue(Element e, String name) {
        Attr a = e.attr(name);
        return a == null ? null : unescape(text.substring(a.valueStart, a.valueEnd));
    }

    // ---- 파싱 ----

    public static XmlSource parse(String t) {
        int i = 0, n = t.length();
        java.util.ArrayDeque<Element> stack = new java.util.ArrayDeque<>();
        Element root = null;
        while (i < n) {
            if (t.charAt(i) != '<') {
                i++;
                continue;
            }
            if (t.startsWith("<!--", i)) {
                i = after(t, "-->", i + 4);
            } else if (t.startsWith("<![CDATA[", i)) {
                i = after(t, "]]>", i + 9);
            } else if (t.startsWith("<?", i)) {
                i = after(t, "?>", i + 2);
            } else if (t.startsWith("<!", i)) {
                i = after(t, ">", i + 2);
            } else if (t.startsWith("</", i)) {
                int close = t.indexOf('>', i);
                if (close < 0) throw new IllegalArgumentException("닫는 태그가 끝나지 않음");
                String name = t.substring(i + 2, close).trim();
                Element top = stack.peek();
                if (top == null || !top.tag.equals(name)) throw new IllegalArgumentException("태그가 맞지 않음: </" + name + ">");
                top.endTagStart = i;
                top.end = close + 1;
                stack.pop();
                i = close + 1;
            } else {
                Element el = readStartTag(t, i);
                Element parent = stack.peek();
                el.parent = parent;
                if (parent != null) parent.children.add(el);
                else if (root == null) root = el;
                if (!el.selfClosing) stack.push(el);
                i = el.startTagEnd;
            }
        }
        if (root == null) throw new IllegalArgumentException("루트 요소가 없음");
        if (!stack.isEmpty()) throw new IllegalArgumentException("닫히지 않은 태그: <" + stack.peek().tag + ">");
        return new XmlSource(t, root);
    }

    private static int after(String t, String token, int from) {
        int e = t.indexOf(token, from);
        if (e < 0) throw new IllegalArgumentException("닫히지 않은 구문: " + token);
        return e + token.length();
    }

    private static Element readStartTag(String t, int lt) {
        int n = t.length();
        int j = lt + 1;
        while (j < n && !Character.isWhitespace(t.charAt(j)) && t.charAt(j) != '/' && t.charAt(j) != '>') j++;
        Element el = new Element(t.substring(lt + 1, j), lt);
        int wsStart = j;
        while (j < n) {
            char c = t.charAt(j);
            if (Character.isWhitespace(c)) {
                j++;
                continue;
            }
            if (c == '>') {
                el.tagCloseStart = j;
                el.startTagEnd = j + 1;
                el.end = el.startTagEnd;
                return el;
            }
            if (c == '/' && j + 1 < n && t.charAt(j + 1) == '>') {
                el.selfClosing = true;
                el.tagCloseStart = j;
                el.startTagEnd = j + 2;
                el.end = el.startTagEnd;
                return el;
            }
            int nameStart = j;
            while (j < n && !Character.isWhitespace(t.charAt(j)) && t.charAt(j) != '=' && t.charAt(j) != '>'
                    && !(t.charAt(j) == '/' && j + 1 < n && t.charAt(j + 1) == '>')) j++;
            String name = t.substring(nameStart, j);
            while (j < n && Character.isWhitespace(t.charAt(j))) j++;
            if (j >= n || t.charAt(j) != '=') throw new IllegalArgumentException("속성에 '=' 가 없음: " + name);
            j++;
            while (j < n && Character.isWhitespace(t.charAt(j))) j++;
            if (j >= n || (t.charAt(j) != '"' && t.charAt(j) != '\'')) throw new IllegalArgumentException("속성 값에 따옴표가 없음: " + name);
            char quote = t.charAt(j);
            int valueEnd = t.indexOf(quote, j + 1);
            if (valueEnd < 0) throw new IllegalArgumentException("속성 값이 닫히지 않음: " + name);
            el.attrs.add(new Attr(name, wsStart, valueEnd + 1, j + 1, valueEnd, quote));
            j = valueEnd + 1;
            wsStart = j;
        }
        throw new IllegalArgumentException("시작 태그가 닫히지 않음: <" + el.tag + ">");
    }

    // ---- 이스케이프 ----

    static String escape(String v, char quote) {
        StringBuilder sb = new StringBuilder(v.length() + 8);
        for (char c : v.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append(quote == '"' ? "&quot;" : "\"");
                case '\'' -> sb.append(quote == '\'' ? "&apos;" : "'");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String unescape(String v) {
        if (v.indexOf('&') < 0) return v;
        return v.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }
}
