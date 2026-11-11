package dev.magyul.betteruiux.script.graal;

import dev.magyul.betteruiux.core.script.ScriptRuntime;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;

/**
 * GraalJS 로 구현한 {@link ScriptRuntime}. 스크립트는 샌드박스 안에서만 돈다:
 * 호스트(Java) 클래스/객체 접근 없음, 파일/네트워크/프로세스/스레드/환경 변수 없음, 문장 수와 시간 상한.
 * 호스트와는 JSON 글자로만 대화한다(전역 {@code ui} 객체가 요청을 모아 JSON 으로 돌려준다).
 *
 * <p><b>알려진 한계</b>: GraalVM Community 는 힙 메모리 상한(maxHeapMemory)을 지원하지 않는다. 문자열 길이는 제한하지만,
 * 악의적으로 거대한 배열을 만드는 스크립트는 메모리를 많이 쓸 수 있다. 신뢰할 수 없는 스크립트는 허용하지 말고,
 * 필요하면 이 런타임을 별도 프로세스로 옮길 수 있다(경계가 JSON 이라 프로토콜은 그대로).
 */
public final class GraalScriptRuntime implements ScriptRuntime {
    /** 한 번의 호출/로드에서 실행할 수 있는 문장 수. */
    public static final long STATEMENT_LIMIT = 500_000;
    /** 한 번의 호출/로드의 최대 실행 시간. */
    public static final Duration TIME_LIMIT = Duration.ofMillis(300);
    private static final int STRING_LIMIT = 1_000_000;

    private static final String BOOTSTRAP = """
            (function () {
              var ops = [];
              function q(op) { ops.push(op); }
              var ui = {
                setAttr: function (t, n, v) { q({op: 'setAttr', target: String(t), name: String(n), value: String(v)}); },
                removeAttr: function (t, n) { q({op: 'removeAttr', target: String(t), name: String(n)}); },
                setText: function (t, v) { q({op: 'setText', target: String(t), value: String(v)}); },
                addClass: function (t, n) { q({op: 'addClass', target: String(t), name: String(n)}); },
                removeClass: function (t, n) { q({op: 'removeClass', target: String(t), name: String(n)}); },
                toggleClass: function (t, n, on) { q({op: 'toggleClass', target: String(t), name: String(n), on: on !== false}); },
                setStyle: function (t, n, v) { q({op: 'setStyle', target: String(t), name: String(n), value: v == null ? null : String(v)}); },
                setVar: function (n, v) { q({op: 'setVar', name: String(n), value: v == null ? null : String(v)}); },
                append: function (t, xml) { q({op: 'append', target: String(t), xml: String(xml)}); },
                remove: function (t) { q({op: 'remove', target: String(t)}); },
                command: function (line) { q({op: 'command', line: String(line)}); },
                log: function () { q({op: 'log', value: Array.prototype.slice.call(arguments).map(String).join(' ')}); }
              };
              Object.freeze(ui);
              globalThis.ui = ui;
              var host = globalThis.__host;                    // 동기 호스트 콜백: 스크립트가 직접 부를 수 없게 전역에서 지운다
              delete globalThis.__host;
              var jsonParse = JSON.parse, jsonStringify = JSON.stringify;    // 스크립트가 JSON 을 덮어써도 요청이 위조되지 않게 원본을 잡아 둔다
              function H(req) {
                var r = jsonParse(host(jsonStringify(req)));
                if (!r.ok) throw new Error(r.error);
                return r.value;
              }
              function camera(w) {
                function c(op) { return function () { return H({cmd: 'camera', world: w, op: op, args: Array.prototype.slice.call(arguments)}); }; }
                return Object.freeze({
                  setYaw: c('setYaw'), setPitch: c('setPitch'), setPosition: c('setPosition'), setZoom: c('setZoom'),
                  setFov: c('setFov'), setProjection: c('setProjection'), get: c('get')
                });
              }
              function entity(w, id) {
                function call(method, args) { return H({cmd: 'entityCall', world: w, entity: id, method: method, args: args}); }
                function get(n) { return H({cmd: 'entityGet', world: w, entity: id, name: n}); }
                // 엔티티에서 바로 쓰는 메서드. 이름이 여기 없으면 필드(customName 등)로 읽고 쓴다.
                var methods = {
                  setPosition: function (x, y, z) { return call('setPosition', [x, y, z]); },
                  setRotation: function (yaw, pitch) { return call('setRotation', [yaw, pitch]); },
                  remove: function () { return call('remove', []); },
                  mergeNbt: function (snbt) { return call('mergeNbt', [String(snbt)]); },
                  getNbt: function () { return call('getNbt', []); },
                  toString: function () { return 'Entity#' + id; },
                  toJSON: function () { return {id: id}; }
                };
                return new Proxy(Object.create(null), {
                  get: function (_, n) {
                    if (typeof n !== 'string') return undefined;
                    if (n === 'id') return id;
                    if (Object.prototype.hasOwnProperty.call(methods, n)) return methods[n];
                    return get(n);
                  },
                  set: function (_, n, v) {
                    n = String(n);
                    if (n === 'x' || n === 'y' || n === 'z') {            // entity.x = 3 처럼 한 축만 바꿔도 된다
                      var pos = {x: get('x'), y: get('y'), z: get('z')};
                      pos[n] = v;
                      call('setPosition', [pos.x, pos.y, pos.z]);
                    } else if (n === 'yaw' || n === 'pitch') {
                      var rot = {yaw: get('yaw'), pitch: get('pitch')};
                      rot[n] = v;
                      call('setRotation', [rot.yaw, rot.pitch]);
                    } else {
                      H({cmd: 'entitySet', world: w, entity: id, name: n, value: v});
                    }
                    return true;
                  }
                });
              }
              function world(w) {
                var cam = camera(w);
                return Object.freeze({
                  id: w,
                  size: function () { return H({cmd: 'size', world: w}); },
                  setBlock: function (x, y, z, block, nbt) {
                    H({cmd: 'setBlock', world: w, x: x, y: y, z: z, block: String(block), nbt: nbt == null ? null : String(nbt)});
                  },
                  getBlock: function (x, y, z) { return H({cmd: 'getBlock', world: w, x: x, y: y, z: z}); },
                  fill: function (x1, y1, z1, x2, y2, z2, block, nbt) {
                    return H({cmd: 'fill', world: w, x1: x1, y1: y1, z1: z1, x2: x2, y2: y2, z2: z2, block: String(block), nbt: nbt == null ? null : String(nbt)});
                  },
                  setBlocks: function (list) { return H({cmd: 'setBlocks', world: w, blocks: list}); },
                  loadStructure: function (name, x, y, z) { return H({cmd: 'loadStructure', world: w, name: String(name), x: x || 0, y: y || 0, z: z || 0}); },
                  spawnEntity: function (x, y, z, type, nbt) { return entity(w, H({cmd: 'spawnEntity', world: w, x: x, y: y, z: z, type: String(type), nbt: nbt == null ? null : String(nbt)})); },
                  getCamera: function () { return cam; },
                  project: function (x, y, z) { return H({cmd: 'project', world: w, x: x, y: y, z: z}); },
                  raycast: function (sx, sy) { return H({cmd: 'raycast', world: w, x: sx, y: sy}); },
                  setViewArea: function (x1, y1, z1, x2, y2, z2) { H({cmd: 'setViewArea', world: w, x1: x1, y1: y1, z1: z1, x2: x2, y2: y2, z2: z2}); },
                  clearViewArea: function () { H({cmd: 'clearViewArea', world: w}); },
                  setTickRate: function (rate) { H({cmd: 'setTickRate', world: w, rate: rate}); }
                });
              }
              globalThis.mcv = Object.freeze({
                createWorld: function (x, y, z) { return world(H({cmd: 'createWorld', x: x, y: y, z: z})); }
              });
              globalThis.console = Object.freeze({log: ui.log, warn: ui.log, error: ui.log, info: ui.log});
              globalThis.__dispatch = function (json) {
                ops = [];
                var msg = JSON.parse(json);
                if (msg.type === 'init') {
                  if (typeof globalThis.onInit === 'function') globalThis.onInit(msg.state);
                } else if (msg.type === 'tick') {
                  if (typeof globalThis.onTick === 'function') globalThis.onTick(msg.tick, msg.world);
                } else if (msg.type === 'call') {
                  var f = globalThis[msg.fn];
                  if (typeof f === 'function') f(msg.event, msg.state);
                }
                var out = JSON.stringify(ops);
                ops = [];
                return out;
              };
            })();
            """;

    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "better-uiux-script-watchdog");
        t.setDaemon(true);
        return t;
    });

    private Context context;
    private Value dispatch;
    private boolean closed, needsRebuild;
    /** 불러온 스크립트 소스(이름, 내용). 문장 수 한도로 컨텍스트가 닫히면 이것으로 새 컨텍스트를 만든다(스크립트 상태는 초기화). */
    private final java.util.List<String[]> sources = new java.util.ArrayList<>();

    private static Engine engine;

    /**
     * 모든 런타임이 하나의 엔진을 나눠 쓴다: 엔진(언어 초기화, 파싱 캐시)은 처음 한 번만 비싸고, 컨텍스트는 엔진 위에서 금방 만들어진다.
     * 컨텍스트끼리는 전역 상태를 공유하지 않는다.
     */
    private static synchronized Engine sharedEngine() {
        if (engine == null) {
            ClassLoader old = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(GraalScriptRuntime.class.getClassLoader());
            try {
                engine = Engine.newBuilder("js")
                        .allowExperimentalOptions(true)
                        .option("engine.WarnInterpreterOnly", "false")
                        .build();
            } finally {
                Thread.currentThread().setContextClassLoader(old);
            }
        }
        return engine;
    }

    /**
     * 게임이 켜질 때 백그라운드에서 불러 둔다: 엔진과 JS 언어를 미리 초기화해서, 처음 스크립트 화면을 열 때 기다리지 않게 한다.
     * 실패해도 조용히 넘어가고 실제로 쓸 때 다시 시도한다.
     */
    public static void prewarm() {
        try {
            GraalScriptRuntime rt = new GraalScriptRuntime();
            try {
                rt.load("prewarm", "function __warm() { return 1; }");
                rt.call("{\"type\":\"call\",\"fn\":\"__warm\",\"event\":{},\"state\":{}}");
            } catch (ScriptException ignored) {
                // 예열이 목적이라 결과는 상관없다
            } finally {
                rt.close();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // 실제 사용할 때 다시 시도된다
        }
    }

    private final long statementLimit;
    private final Duration timeLimit;
    private volatile java.util.function.Function<String, String> hostCallback;

    public GraalScriptRuntime() {
        this(STATEMENT_LIMIT, TIME_LIMIT);
    }

    /** mc-viewer 처럼 무거운 스크립트를 위해 한도를 직접 정한다(워치독 상한은 그대로 적용된다). */
    public GraalScriptRuntime(long statementLimit, Duration timeLimit) {
        this.statementLimit = statementLimit;
        this.timeLimit = timeLimit;
        withLoader(this::create);
    }

    /** 생성자가 받는 한도 인자(리플렉션용): (long 문장 수, long 밀리초). */
    public GraalScriptRuntime(long statementLimit, long timeLimitMillis) {
        this(statementLimit, Duration.ofMillis(timeLimitMillis));
    }

    @Override
    public void setHostCallback(java.util.function.Function<String, String> callback) {
        this.hostCallback = callback;
    }

    /** 스크립트가 {@code __host} 로 부르는 다리: 글자 하나를 호스트 콜백에 넘기고 글자 하나를 받는다. */
    private final class HostBridge implements org.graalvm.polyglot.proxy.ProxyExecutable {
        @Override
        public Object execute(Value... args) {
            java.util.function.Function<String, String> cb = hostCallback;
            if (cb == null || args.length != 1 || !args[0].isString()) return "{\"ok\":false,\"error\":\"mcv 를 쓸 수 없습니다\"}";
            try {
                return cb.apply(args[0].asString());
            } catch (RuntimeException e) {
                return "{\"ok\":false,\"error\":\"mcv 내부 오류\"}";
            }
        }
    }

    /**
     * Truffle 은 언어/구현을 컨텍스트 클래스로더에서 서비스로 찾는다. 모드 로더(Fabric Knot 등) 환경에서는 이 jar 가
     * 스레드의 기본 클래스로더에게 보이지 않으므로, 우리 클래스를 읽은 로더로 잠시 바꿔 놓고 실행한다.
     */
    private static void withLoader(Runnable r) {
        Thread t = Thread.currentThread();
        ClassLoader old = t.getContextClassLoader();
        t.setContextClassLoader(GraalScriptRuntime.class.getClassLoader());
        try {
            r.run();
        } finally {
            t.setContextClassLoader(old);
        }
    }

    private void create() {
        ResourceLimits limits = ResourceLimits.newBuilder().statementLimit(statementLimit, null).build();
        this.context = Context.newBuilder("js")
                .engine(sharedEngine())
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(name -> false)
                .allowIO(IOAccess.NONE)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowNativeAccess(false)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .allowExperimentalOptions(true)                       // 문자열 길이 상한 같은 우리가 정한 옵션을 쓰기 위해(스크립트는 옵션을 바꿀 수 없다)
                .resourceLimits(limits)
                .option("js.string-length-limit", Integer.toString(STRING_LIMIT))
                .build();
        context.getBindings("js").putMember("__host", new HostBridge());
        context.eval(Source.create("js", BOOTSTRAP));
        this.dispatch = context.getBindings("js").getMember("__dispatch");
        needsRebuild = false;
    }

    /** 한도 초과로 닫힌 컨텍스트를 버리고 같은 스크립트로 새로 만든다. */
    private void rebuild() throws ScriptException {
        try {
            context.close(true);
        } catch (RuntimeException ignored) {
            // 이미 닫힘
        }
        create();
        for (String[] src : sources) {
            try {
                context.eval(Source.newBuilder("js", src[1], src[0]).buildLiteral());
            } catch (PolyglotException e) {
                throw new ScriptException(describe(e, src[0]), e);
            }
        }
    }

    @Override
    public synchronized void load(String name, String source) throws ScriptException {
        run(() -> context.eval(Source.newBuilder("js", source, name).buildLiteral()), name);
        sources.add(new String[] {name, source});
    }

    @Override
    public synchronized String call(String messageJson) throws ScriptException {
        java.util.function.Function<String, String> cb = hostCallback;
        if (cb != null) cb.apply("{\"cmd\":\"beginCall\"}");                       // mcv 호출 횟수 예산을 새로 시작
        Value result = run(() -> dispatch.execute(messageJson), "call");
        return result.isString() ? result.asString() : "[]";
    }

    private interface Job {
        Value run();
    }

    private Value run(Job job, String what) throws ScriptException {
        if (closed) throw new ScriptException("스크립트 런타임이 닫혔습니다");
        Thread thread = Thread.currentThread();
        ClassLoader old = thread.getContextClassLoader();
        thread.setContextClassLoader(GraalScriptRuntime.class.getClassLoader());
        try {
            return runInLoader(job, what);
        } finally {
            thread.setContextClassLoader(old);
        }
    }

    private Value runInLoader(Job job, String what) throws ScriptException {
        if (needsRebuild) rebuild();
        context.resetLimits();
        ScheduledFuture<?> timer = WATCHDOG.schedule(() -> {
            try {
                context.interrupt(Duration.ofSeconds(1));                  // 실행 중인 호출만 끊고 컨텍스트는 계속 쓴다
            } catch (java.util.concurrent.TimeoutException | RuntimeException ignored) {
                // 이미 끝났거나 닫힘
            }
        }, timeLimit.toMillis(), TimeUnit.MILLISECONDS);
        try {
            return job.run();
        } catch (PolyglotException e) {
            if (e.isResourceExhausted() || e.isCancelled() && !e.isInterrupted()) needsRebuild = true;   // 한도 초과면 컨텍스트가 닫힌다
            throw new ScriptException(describe(e, what), e);
        } finally {
            timer.cancel(false);
        }
    }

    private String describe(PolyglotException e, String what) {
        if (e.isResourceExhausted()) return "실행 한도를 넘었습니다(문장 수 " + statementLimit + ")";
        if (e.isCancelled() || e.isInterrupted()) return "실행 시간 한도를 넘었습니다(" + timeLimit.toMillis() + "ms)";
        if (e.isSyntaxError()) return "문법 오류: " + e.getMessage();
        return e.getMessage();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(GraalScriptRuntime.class.getClassLoader());
        try {
            context.close(true);
        } catch (RuntimeException ignored) {
            // 이미 닫혔거나 실행 중단 중
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }
}
