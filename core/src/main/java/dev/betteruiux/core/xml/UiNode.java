package dev.betteruiux.core.xml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** XML 요소 하나를 나타내는 UI 트리 노드. 마인크래프트 클래스에 의존하지 않는다. */
public final class UiNode {
    private final String tag;
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final List<UiNode> children = new ArrayList<>();
    private String text = "";

    public UiNode(String tag) { this.tag = tag; }

    public String tag() { return tag; }
    public Map<String, String> attributes() { return attributes; }
    public List<UiNode> children() { return children; }
    public String text() { return text; }
    public void setText(String text) { this.text = text; }
    public String attr(String name) { return attributes.get(name); }
}
