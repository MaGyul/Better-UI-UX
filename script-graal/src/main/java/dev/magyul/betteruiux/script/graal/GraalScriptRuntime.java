package dev.magyul.betteruiux.script.graal;

import dev.magyul.betteruiux.core.script.ScriptRuntime;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.graalvm.polyglot.Context;
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
              globalThis.console = Object.freeze({log: ui.log, warn: ui.log, error: ui.log, info: ui.log});
              globalThis.__dispatch = function (json) {
                ops = [];
                var msg = JSON.parse(json);
                if (msg.type === 'init') {
                  if (typeof globalThis.onInit === 'function') globalThis.onInit(msg.state);
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

    public GraalScriptRuntime() {
        create();
    }

    private void create() {
        ResourceLimits limits = ResourceLimits.newBuilder().statementLimit(STATEMENT_LIMIT, null).build();
        this.context = Context.newBuilder("js")
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
                .option("engine.WarnInterpreterOnly", "false")
                .option("js.string-length-limit", Integer.toString(STRING_LIMIT))
                .build();
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
        Value result = run(() -> dispatch.execute(messageJson), "call");
        return result.isString() ? result.asString() : "[]";
    }

    private interface Job {
        Value run();
    }

    private Value run(Job job, String what) throws ScriptException {
        if (closed) throw new ScriptException("스크립트 런타임이 닫혔습니다");
        if (needsRebuild) rebuild();
        context.resetLimits();
        ScheduledFuture<?> timer = WATCHDOG.schedule(() -> {
            try {
                context.interrupt(Duration.ofSeconds(1));                  // 실행 중인 호출만 끊고 컨텍스트는 계속 쓴다
            } catch (java.util.concurrent.TimeoutException | RuntimeException ignored) {
                // 이미 끝났거나 닫힘
            }
        }, TIME_LIMIT.toMillis(), TimeUnit.MILLISECONDS);
        try {
            return job.run();
        } catch (PolyglotException e) {
            if (e.isResourceExhausted() || e.isCancelled() && !e.isInterrupted()) needsRebuild = true;   // 한도 초과면 컨텍스트가 닫힌다
            throw new ScriptException(describe(e, what), e);
        } finally {
            timer.cancel(false);
        }
    }

    private static String describe(PolyglotException e, String what) {
        if (e.isResourceExhausted()) return "실행 한도를 넘었습니다(문장 수 " + STATEMENT_LIMIT + ")";
        if (e.isCancelled() || e.isInterrupted()) return "실행 시간 한도를 넘었습니다(" + TIME_LIMIT.toMillis() + "ms)";
        if (e.isSyntaxError()) return "문법 오류: " + e.getMessage();
        return e.getMessage();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            context.close(true);
        } catch (RuntimeException ignored) {
            // 이미 닫혔거나 실행 중단 중
        }
    }
}
