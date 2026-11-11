package dev.magyul.betteruiux.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.doc.UiDocument;
import dev.magyul.betteruiux.core.layout.FixedMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** 스크립트 전용 스레드 모드: 스크립트는 다른 스레드에서 돌고, 문서 변경은 "메인" 큐를 비울 때만 일어난다. */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class ScriptHostThreadingTest {
    private final ExecutorService scriptThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "test-script"));
    private final Queue<Runnable> mainQueue = new ConcurrentLinkedQueue<>();
    private final List<String> threads = new ArrayList<>();

    @AfterEach
    void stop() { scriptThread.shutdownNow(); }

    private void drainMain() {
        Runnable r;
        while ((r = mainQueue.poll()) != null) r.run();
    }

    private void awaitScript() throws Exception { scriptThread.submit(() -> { }).get(5, TimeUnit.SECONDS); }

    private ScriptRuntime runtime(java.util.function.Function<String, String> responder) {
        return new ScriptRuntime() {
            @Override public void load(String name, String source) { synchronized (threads) { threads.add(Thread.currentThread().getName()); } }

            @Override public String call(String m) {
                synchronized (threads) { threads.add(Thread.currentThread().getName()); }
                return responder.apply(m);
            }

            @Override public void close() { }
        };
    }

    @Test
    void scriptsRunOffThreadAndTheirRequestsApplyOnlyWhenTheMainQueueDrains() throws Exception {
        UiDocument d = UiDocument.parse("<screen><script>x</script><label id=\"out\" text=\"-\"/></screen>", new FixedMetrics());
        d.layout(400, 300);
        ScriptHost host = new ScriptHost(d, runtime(m -> "[{\"op\":\"setText\",\"target\":\"#out\",\"value\":\"done\"}]"), CommandPolicy.denyAll(), null);
        host.setThreading(scriptThread, mainQueue::add);
        host.loadScripts(n -> null);
        host.init();
        awaitScript();
        assertEquals("-", d.query("#out").text(), "메인 큐를 비우기 전에는 문서가 바뀌지 않는다");
        drainMain();
        assertEquals("done", d.query("#out").text());
        synchronized (threads) {
            assertTrue(threads.stream().allMatch("test-script"::equals), "로드와 호출 모두 스크립트 스레드: " + threads);
        }
    }

    @Test
    void ticksAreDroppedWhileTheScriptIsStillBusy() throws Exception {
        UiDocument d = UiDocument.parse("<screen/>", new FixedMetrics());
        CountDownLatch inside = new CountDownLatch(1), release = new CountDownLatch(1);
        List<String> seen = new ArrayList<>();
        ScriptRuntime rt = new ScriptRuntime() {
            @Override public void load(String n, String s) { }

            @Override public String call(String m) throws ScriptException {
                synchronized (seen) { seen.add(m); }
                inside.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new ScriptException("interrupted");
                }
                return "[]";
            }

            @Override public void close() { }
        };
        ScriptHost host = new ScriptHost(d, rt, CommandPolicy.denyAll(), null);
        host.setThreading(scriptThread, mainQueue::add);
        host.tick(1);
        assertTrue(inside.await(5, TimeUnit.SECONDS));
        host.tick(2);
        host.tick(3);                                                  // 아직 1번 틱 처리 중이라 버려진다
        release.countDown();
        awaitScript();
        host.tick(4);
        awaitScript();
        synchronized (seen) {
            assertEquals(2, seen.size(), seen.toString());
            assertTrue(seen.get(0).contains("\"tick\":1") && seen.get(1).contains("\"tick\":4"), seen.toString());
        }
    }

    @Test
    void closeStopsFurtherCallsWithoutBlocking() throws Exception {
        UiDocument d = UiDocument.parse("<screen/>", new FixedMetrics());
        List<String> seen = new ArrayList<>();
        ScriptRuntime rt = new ScriptRuntime() {
            @Override public void load(String n, String s) { }

            @Override public String call(String m) { synchronized (seen) { seen.add(m); } return "[]"; }

            @Override public void close() { }
        };
        ScriptHost host = new ScriptHost(d, rt, CommandPolicy.denyAll(), null);
        host.setThreading(scriptThread, mainQueue::add);
        host.close();
        host.tick(1);
        host.init();
        awaitScript();
        synchronized (seen) { assertFalse(!seen.isEmpty(), "닫은 뒤에는 호출하지 않는다"); }
    }
}
