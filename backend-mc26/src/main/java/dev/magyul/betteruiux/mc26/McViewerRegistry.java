package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.mcv.McvHost;
import java.util.Map;
import java.util.WeakHashMap;

/** 문서 → 그 문서의 스크립트가 만든 가상 월드들({@link McvHost}). {@code <mc-viewer>} 위젯이 자기 문서의 월드를 찾는 데 쓴다. */
final class McViewerRegistry {
    /** 문서의 월드들과, 월드가 틱을 진행할 때 스크립트의 onTick 으로 알려 주는 쪽. */
    record Entry(McvHost host, ScriptEvents events) { }

    /** 위젯이 스크립트로 보내는 신호들. */
    interface ScriptEvents {
        void tick(int world, long tick);

        void call(String fn, java.util.Map<String, Object> event);
    }

    private static final Map<UiDocument, Entry> HOSTS = new WeakHashMap<>();

    private McViewerRegistry() {}

    static synchronized void put(UiDocument doc, McvHost host, ScriptEvents events) {
        HOSTS.put(doc, new Entry(host, events));
    }

    static synchronized void remove(UiDocument doc) { HOSTS.remove(doc); }

    static synchronized McvHost get(UiDocument doc) {
        Entry e = HOSTS.get(doc);
        return e == null ? null : e.host();
    }

    static synchronized ScriptEvents events(UiDocument doc) {
        Entry e = HOSTS.get(doc);
        return e == null ? null : e.events();
    }
}
