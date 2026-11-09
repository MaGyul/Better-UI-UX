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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 서버 쪽 동기화 로직(마인크래프트 비의존). 전송은 {@link Outbox} 로 추상화되어 있어, 패킷을 어떻게 보내는지(Fabric/NeoForge)와 무관하게
 * 같은 규칙으로 동작하고 단위 테스트가 된다. 클라이언트가 보낸 모든 것은 검증하며, 어긋나면 위반으로 세고 일정 횟수를 넘으면 그 클라이언트를 무시한다.
 */
public final class ServerSyncCore {
    /** 메시지를 상대에게 보낸다. */
    @FunctionalInterface
    public interface Outbox {
        void send(SyncMessage m);
    }

    /** 서버가 배포하는 번들(파일 내용 + 매니페스트). */
    public record ServerBundle(Map<String, byte[]> files, BundleManifest manifest) {
        public static final ServerBundle EMPTY = new ServerBundle(Map.of(), new BundleManifest(Bundle.bundleHash(List.of()), List.of()));
    }

    /** 서버 액션 처리기. {@code args} 는 스키마를 통과한 값이다. */
    @FunctionalInterface
    public interface ActionHandler {
        void handle(String playerId, Map<String, Object> args);
    }

    private record ActionEntry(ActionSchema schema, ActionHandler handler) {}

    /** 위반이 이 횟수를 넘으면 그 클라이언트의 동기화를 끈다(연결은 끊지 않는다). */
    public static final int MAX_VIOLATIONS = 10;
    /** 이 시간 동안 위반이 없으면 위반 횟수를 0 으로 되돌린다(실수로 몇 번 틀린 것과 계속되는 공격을 구분한다). */
    private static final long VIOLATION_DECAY_NANOS = 60_000_000_000L;
    private static final int CHUNKS_PER_TICK = 8;

    private volatile ServerBundle bundle = ServerBundle.EMPTY;
    private final int policy;
    private final Map<String, ActionEntry> actions = new ConcurrentHashMap<>();

    /** @param policy 0 = server-first(서버 번들이 같은 id 의 로컬 파일을 덮음), 1 = local-first */
    public ServerSyncCore(int policy) { this.policy = policy; }

    /** 배포할 번들을 바꾼다. 검사(경로, 크기, 형식)에 걸리면 {@link SyncException}. */
    public void setBundle(Map<String, byte[]> files) throws SyncException {
        this.bundle = new ServerBundle(Map.copyOf(files), Bundle.manifestOf(files));
    }

    public ServerBundle bundle() { return bundle; }

    /** 서버 액션을 등록한다. XML 의 {@code onClick="server:<id>"} 가 이 id 를 부른다. */
    public void registerAction(String id, ActionSchema schema, ActionHandler handler) {
        if (!UiIds.valid(id)) throw new IllegalArgumentException("액션 id 가 올바르지 않습니다: " + id);
        actions.put(id, new ActionEntry(schema, handler));
    }

    public ServerSession connect(String playerId, Outbox out, LongSupplier nanos) { return new ServerSession(playerId, out, nanos); }

    /** 플레이어 한 명과의 동기화 상태. 서버 스레드에서만 쓴다. */
    public final class ServerSession {
        private final String playerId;
        private final Outbox out;
        private final RateLimiter messages, actionLimiter;
        private boolean active, disabled;
        private int violations;
        private long lastViolation;
        private final LongSupplier nanos;
        private final Set<String> openUis = new HashSet<>();
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final Deque<BundleChunk> pending = new ArrayDeque<>();
        private BundleManifest sentManifest;
        private Set<String> allowedNeed = Set.of();

        private ServerSession(String playerId, Outbox out, LongSupplier nanos) {
            this.playerId = playerId;
            this.out = out;
            this.nanos = nanos;
            this.messages = new RateLimiter(100, 200, nanos);
            this.actionLimiter = new RateLimiter(5, 10, nanos);
        }

        public String playerId() { return playerId; }

        /** 클라이언트가 모드를 가지고 프로토콜이 맞아 서버 기능이 켜져 있는가. */
        public boolean active() { return active && !disabled; }

        public boolean disabled() { return disabled; }

        public Set<String> openUis() { return Set.copyOf(openUis); }

        private void violation(String why) {
            long now = nanos.getAsLong();
            if (violations > 0 && now - lastViolation > VIOLATION_DECAY_NANOS) violations = 0;
            lastViolation = now;
            if (++violations > MAX_VIOLATIONS) disabled = true;
        }

        /** 패킷 해독에 실패했을 때(잘못된 메시지) 글루가 부른다. */
        public void protocolViolation(String why) { violation(why); }

        /** 클라이언트가 보낸 메시지를 처리한다. */
        public void onMessage(SyncMessage m) {
            if (disabled) return;
            if (!messages.allow()) {
                violation("메시지가 너무 많습니다");
                return;
            }
            switch (m) {
                case ClientHello h -> hello(h);
                case BundleNeed n -> need(n);
                case Action a -> action(a);
                default -> violation("클라이언트가 보낼 수 없는 메시지: " + m.getClass().getSimpleName());
            }
        }

        private void hello(ClientHello h) {
            out.send(new ServerHello(SyncLimits.PROTOCOL, bundle.manifest().hash(), policy));
            if (h.protocol() != SyncLimits.PROTOCOL) return;                  // 버전이 다르면 서버 기능을 켜지 않는다
            active = true;
            ServerBundle b = bundle;
            if (!b.files().isEmpty() && !b.manifest().hash().equals(h.bundleHash())) {
                sentManifest = b.manifest();
                allowedNeed = new HashSet<>();
                for (var f : b.manifest().files()) allowedNeed.add(f.path());
                out.send(b.manifest());
            }
            if (!state.isEmpty()) out.send(new StateSnapshot(new LinkedHashMap<>(state)));
        }

        private void need(BundleNeed n) {
            if (!active() || sentManifest == null) {
                violation("요청하지 않은 번들 요청");
                return;
            }
            for (String p : n.paths()) {
                if (!allowedNeed.contains(p)) {
                    violation("매니페스트에 없는 파일 요청: " + p);
                    return;
                }
            }
            ServerBundle b = bundle;
            if (!b.manifest().hash().equals(sentManifest.hash())) return;       // 그 사이 번들이 바뀌었다: 클라이언트는 새 매니페스트를 받는다
            for (String p : n.paths()) pending.addAll(Bundle.chunks(p, b.files().get(p)));
        }

        /** 서버 틱마다 불러 쌓인 조각을 조금씩 보낸다(한 번에 16MB 를 쏟아내지 않도록). */
        public void tick() {
            if (disabled) return;
            for (int i = 0; i < CHUNKS_PER_TICK && !pending.isEmpty(); i++) out.send(pending.poll());
        }

        /** 보낼 조각이 남았는가(테스트/진행 표시용). */
        public int pendingChunks() { return pending.size(); }

        private void action(Action a) {
            if (!active()) {
                violation("활성화되지 않은 클라이언트의 동작");
                return;
            }
            if (!actionLimiter.allow()) return;                                 // 연타: 넘치는 것은 조용히 버린다(위반으로 세지 않는다)
            if (!openUis.contains(a.uiId())) {                                  // 그 플레이어에게 그 UI 가 열려 있어야 한다
                violation("열려 있지 않은 UI 의 동작: " + a.uiId());
                return;
            }
            ActionEntry e = actions.get(a.actionId());
            if (e == null) {
                violation("등록되지 않은 동작: " + a.actionId());
                return;
            }
            Map<String, Object> args = e.schema().coerce(a.args());
            String why = e.schema().validate(args);
            if (why != null) {
                violation("동작 인자 오류(" + a.actionId() + "): " + why);
                return;
            }
            try {
                e.handler().handle(playerId, args);
            } catch (RuntimeException ex) {
                violation("동작 처리 중 오류: " + ex);
            }
        }

        // ---- 서버 → 클라이언트 ----

        /** 화면을 연다(클라이언트가 서버 기능을 켠 경우에만). */
        public boolean open(String id, Map<String, Object> params) throws SyncException {
            if (!UiIds.valid(id)) throw new SyncException("UI id 가 올바르지 않습니다: " + id);
            if (!active()) return false;
            openUis.add(id);
            out.send(new OpenUi(id, params));
            return true;
        }

        public boolean close(String id) {
            if (!active()) return false;
            openUis.remove(id);
            out.send(new CloseUi(id));
            return true;
        }

        /** 클라이언트가 화면을 닫았음을 서버가 알게 되었을 때(예: 접속 종료) 열림 표시를 지운다. */
        public void forgetUi(String id) { openUis.remove(id); }

        /** 값을 설정한다. 키 수/값 크기 상한은 인코딩에서 강제된다. */
        public void set(String key, Object value) throws SyncException {
            if (!state.containsKey(key) && state.size() >= SyncLimits.MAX_STATE_KEYS) throw new SyncException("상태 키가 너무 많습니다");
            StateSet msg = new StateSet(key, value);
            SyncCodec.encode(msg);                                              // 형식/길이 검증(예외면 상태를 바꾸지 않는다)
            state.put(key, value);
            if (active()) out.send(msg);
        }

        public void clear(String key) {
            if (state.remove(key) != null && active()) out.send(new StateClear(key));
        }

        public Map<String, Object> state() { return Map.copyOf(state); }
    }
}
