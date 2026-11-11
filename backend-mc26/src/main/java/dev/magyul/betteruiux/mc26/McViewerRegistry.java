package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.mcv.McvHost;
import java.util.Map;
import java.util.WeakHashMap;

/** 문서 → 그 문서의 스크립트가 만든 가상 월드들({@link McvHost}). {@code <mc-viewer>} 위젯이 자기 문서의 월드를 찾는 데 쓴다. */
final class McViewerRegistry {
    private static final Map<UiDocument, McvHost> HOSTS = new WeakHashMap<>();

    private McViewerRegistry() {}

    static synchronized void put(UiDocument doc, McvHost host) { HOSTS.put(doc, host); }

    static synchronized void remove(UiDocument doc) { HOSTS.remove(doc); }

    static synchronized McvHost get(UiDocument doc) { return HOSTS.get(doc); }
}
