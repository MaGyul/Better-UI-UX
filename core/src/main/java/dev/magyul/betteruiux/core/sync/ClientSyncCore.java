package dev.magyul.betteruiux.core.sync;

import dev.magyul.betteruiux.core.sync.SyncMessage.Action;
import dev.magyul.betteruiux.core.sync.SyncMessage.BundleChunk;
import dev.magyul.betteruiux.core.sync.SyncMessage.BundleManifest;
import dev.magyul.betteruiux.core.sync.SyncMessage.BundleNeed;
import dev.magyul.betteruiux.core.sync.SyncMessage.ClientHello;
import dev.magyul.betteruiux.core.sync.SyncMessage.CloseUi;
import dev.magyul.betteruiux.core.sync.SyncMessage.OpenUi;
import dev.magyul.betteruiux.core.sync.SyncMessage.ServerHello;
import dev.magyul.betteruiux.core.sync.SyncMessage.StateClear;
import dev.magyul.betteruiux.core.sync.SyncMessage.StateSet;
import dev.magyul.betteruiux.core.sync.SyncMessage.StateSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 클라이언트 쪽 동기화 로직(마인크래프트 비의존). 서버가 보낸 모든 것을 검증하고, 어긋나면 서버 기능을 끈다(게임 연결은 그대로).
 * 서버 UI 는 번들 캐시({@link BundleCache})에 해시를 확인해 저장한 파일만 쓸 수 있다.
 */
public final class ClientSyncCore {
    /** 클라이언트의 번들 캐시(서버별 폴더). 구현체는 파일 저장을 원자적으로 해야 한다. */
    public interface BundleCache {
        /** 지금 캐시에 있는 번들 해시(없으면 빈 문자열). */
        String bundleHash();

        /** 캐시에 있는 파일들의 sha256(경로 → 해시). */
        Map<String, byte[]> fileHashes();

        /** 새로 받은 파일을 저장하고, 매니페스트에 없는 옛 파일은 지운다. 번들 해시도 함께 저장한다. */
        void store(BundleManifest manifest, Map<String, byte[]> newFiles);
    }

    /** 클라이언트 쪽 이벤트. 모두 렌더 스레드에서 처리하도록 글루가 넘긴다. */
    public interface Listener {
        /** 번들이 캐시에 준비됐다(변경 없음 포함). */
        void onBundleReady(BundleManifest manifest, int policy);

        void onOpenUi(String id, Map<String, Object> params);

        void onCloseUi(String id);

        /** 서버 값이 바뀌었다(전체 현재 값). */
        void onState(Map<String, Object> values);

        /** 서버 기능이 꺼졌다(버전 불일치, 프로토콜 위반 등). */
        void onDisabled(String reason);
    }

    private final ServerSyncCore.Outbox out;
    private final BundleCache cache;
    private final Listener listener;
    private boolean active, disabled;
    private int policy;
    private BundleManifest pendingManifest;
    private BundleAssembler assembler;
    private final Map<String, Object> state = new LinkedHashMap<>();

    public ClientSyncCore(ServerSyncCore.Outbox out, BundleCache cache, Listener listener) {
        this.out = out;
        this.cache = cache;
        this.listener = listener;
    }

    public boolean active() { return active && !disabled; }

    public Map<String, Object> state() { return Map.copyOf(state); }

    /** 서버에 접속했을 때 부른다: 인사를 보낸다. */
    public void connected() { out.send(new ClientHello(SyncLimits.PROTOCOL, cache.bundleHash())); }

    private void disable(String reason) {
        if (disabled) return;
        disabled = true;
        active = false;
        assembler = null;
        pendingManifest = null;
        listener.onDisabled(reason);
    }

    /** 서버 메시지를 처리한다. 패킷 해독 실패는 {@link #protocolViolation} 로. */
    public void onMessage(SyncMessage m) {
        if (disabled) return;
        try {
            switch (m) {
                case ServerHello h -> hello(h);
                case BundleManifest b -> manifest(b);
                case BundleChunk c -> chunk(c);
                case OpenUi o -> {
                    if (!active) throw new SyncException("인사 전의 화면 열기");
                    if (!UiIds.valid(o.id())) throw new SyncException("UI id 가 올바르지 않습니다");
                    listener.onOpenUi(o.id(), o.params());
                }
                case CloseUi c -> {
                    if (!active) throw new SyncException("인사 전의 화면 닫기");
                    listener.onCloseUi(c.id());
                }
                case StateSet s -> {
                    if (!active) throw new SyncException("인사 전의 상태");
                    if (!state.containsKey(s.key()) && state.size() >= SyncLimits.MAX_STATE_KEYS) throw new SyncException("상태 키가 너무 많습니다");
                    state.put(s.key(), s.value());
                    listener.onState(Map.copyOf(state));
                }
                case StateClear s -> {
                    if (!active) throw new SyncException("인사 전의 상태");
                    state.remove(s.key());
                    listener.onState(Map.copyOf(state));
                }
                case StateSnapshot s -> {
                    if (!active) throw new SyncException("인사 전의 상태");
                    state.clear();
                    state.putAll(s.values());
                    listener.onState(Map.copyOf(state));
                }
                default -> throw new SyncException("서버가 보낼 수 없는 메시지: " + m.getClass().getSimpleName());
            }
        } catch (SyncException e) {
            disable(e.getMessage());
        }
    }

    public void protocolViolation(String reason) { disable(reason); }

    private void hello(ServerHello h) throws SyncException {
        if (h.protocol() != SyncLimits.PROTOCOL) {
            disable("서버 동기화 프로토콜 버전이 다릅니다(서버 " + h.protocol() + ", 클라이언트 " + SyncLimits.PROTOCOL + ")");
            return;
        }
        active = true;
        policy = h.policy();
        if (h.policy() != 0 && h.policy() != 1) throw new SyncException("알 수 없는 정책: " + h.policy());
    }

    private void manifest(BundleManifest b) throws SyncException {
        if (!active) throw new SyncException("인사 전의 번들 목록");
        Bundle.validate(b);                                                       // 경로/크기/중복/해시
        List<String> need = Bundle.missing(b, cache.fileHashes());
        pendingManifest = b;
        if (need.isEmpty()) {
            cache.store(b, Map.of());
            pendingManifest = null;
            listener.onBundleReady(b, policy);
            return;
        }
        assembler = new BundleAssembler(b, need);
        out.send(new BundleNeed(need));
    }

    private void chunk(BundleChunk c) throws SyncException {
        if (assembler == null || pendingManifest == null) throw new SyncException("요청하지 않은 번들 조각");
        assembler.accept(c);
        if (!assembler.complete()) return;
        BundleManifest m = pendingManifest;
        Map<String, byte[]> files = assembler.files();
        assembler = null;
        pendingManifest = null;
        cache.store(m, files);
        listener.onBundleReady(m, policy);
    }

    /** 버튼 동작을 서버로 보낸다(서버 기능이 켜져 있을 때만). */
    public boolean sendAction(String uiId, String actionId, Map<String, Object> args) {
        if (!active()) return false;
        if (!UiIds.valid(uiId) || !UiIds.valid(actionId)) return false;
        out.send(new Action(uiId, actionId, args));
        return true;
    }
}
