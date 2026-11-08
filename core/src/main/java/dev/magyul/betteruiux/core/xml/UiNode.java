package dev.magyul.betteruiux.core.xml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * XML 요소 하나를 나타내는 UI 트리(DOM) 노드. 마인크래프트 클래스에 의존하지 않는다.
 * 구조를 바꿀 때는 {@link #append}, {@link #insert}, {@link #detach} 를 쓴다(부모 포인터를 함께 갱신한다).
 */
public final class UiNode {
    private final String tag;
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final List<UiNode> children = new ArrayList<>();
    private UiNode parent;
    private String text = "";
    private Object userData;
    private int sourceIndex = -1;

    public UiNode(String tag) { this.tag = tag; }

    public String tag() { return tag; }

    public Map<String, String> attributes() { return attributes; }

    /** 자식 목록(읽기용으로 취급). 변경은 {@link #append}/{@link #insert}/{@link #detach} 로. */
    public List<UiNode> children() { return children; }

    public UiNode parent() { return parent; }

    public String text() { return text; }

    public void setText(String text) { this.text = text; }

    public String attr(String name) { return attributes.get(name); }

    /** 커스텀 위젯 등이 노드에 붙여 두는 상태(리로드/재레이아웃에도 노드와 함께 유지된다). */
    public Object userData() { return userData; }

    public void setUserData(Object userData) { this.userData = userData; }

    /**
     * 원본 XML 에서 이 요소가 몇 번째(전위 순회, 0부터)인지. 확장(include/template)으로 생긴 노드나 코드로 만든 노드는 -1.
     * 에디터가 노드를 원본 텍스트 위치에 연결하는 데 쓴다.
     */
    public int sourceIndex() { return sourceIndex; }

    public void setSourceIndex(int i) { this.sourceIndex = i; }

    public void append(UiNode child) { insert(children.size(), child); }

    public void insert(int index, UiNode child) {
        if (child.isAncestorOf(this)) throw new IllegalArgumentException("순환 구조는 만들 수 없습니다");
        child.detach();
        child.parent = this;
        children.add(Math.max(0, Math.min(index, children.size())), child);
    }

    /** 부모에서 떼어낸다. */
    public void detach() {
        if (parent != null) {
            parent.children.remove(this);
            parent = null;
        }
    }

    /** this 가 other 의 조상이거나 other 자신인지. */
    public boolean isAncestorOf(UiNode other) {
        for (UiNode n = other; n != null; n = n.parent) if (n == this) return true;
        return false;
    }

    /** 속성/텍스트/자식을 깊게 복사한다(부모와 userData 는 복사하지 않는다). */
    public UiNode copy() {
        UiNode c = new UiNode(tag);
        c.attributes.putAll(attributes);
        c.text = text;
        for (UiNode child : children) c.append(child.copy());
        return c;
    }
}
