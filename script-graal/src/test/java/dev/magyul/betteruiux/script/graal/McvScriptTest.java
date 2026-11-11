package dev.magyul.betteruiux.script.graal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.mcv.McvHost;
import dev.magyul.betteruiux.core.script.ScriptRuntime;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** 진짜 GraalJS 의 {@code mcv} 전역이 Java 월드를 동기로 조작하는지, 샌드박스가 유지되는지 확인한다. */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class McvScriptTest {
    private final McvHost host = new McvHost(1000);
    private final GraalScriptRuntime rt = new GraalScriptRuntime();

    McvScriptTest() { rt.setHostCallback(host::invoke); }

    @AfterEach
    void close() { rt.close(); }

    private String call(String fn) throws Exception {
        return rt.call("{\"type\":\"call\",\"fn\":\"" + fn + "\",\"event\":{},\"state\":{}}");
    }

    @Test
    void scriptBuildsAWorldInJavaAndReadsItBackSynchronously() throws Exception {
        rt.load("t.uijs", """
                var seen;
                function go() {
                  var w = mcv.createWorld(8, 8, 8);
                  w.setBlock(1, 1, 1, 'oak_stairs[facing=east]');
                  w.fill(0, 0, 0, 3, 0, 3, 'white_concrete');
                  seen = w.getBlock(1, 1, 1).block + '|' + w.getBlock(5, 5, 5).id;
                  var cow = w.spawnEntity(2, 1, 2, 'cow');
                  cow.methods.setPosition(3, 1, 3);
                  cow.fields.customName = 'Bessie';
                  var cam = w.getCamera();
                  cam.setYaw(90); cam.setProjection('perspective');
                  seen += '|' + cow.fields.x + '|' + cow.fields.customName + '|' + cam.get().yaw;
                  ui.log(seen);
                }
                """);
        String out = call("go");
        assertTrue(out.contains("minecraft:oak_stairs[facing=east]|minecraft:air|3|Bessie|90"), out);
        var w = host.world(1);
        assertEquals(17, w.blockCount());
        assertEquals(1, w.entities().size());
    }

    @Test
    void hostErrorsBecomeScriptExceptionsThatScriptsCanCatch() throws Exception {
        rt.load("t.uijs", "function go() { var w = mcv.createWorld(2,2,2); try { w.setBlock(9,9,9,'stone'); } catch (e) { ui.log('caught:' + e.message); } }");
        assertTrue(call("go").contains("caught:"), "범위 밖 좌표는 JS 예외로 보인다");
    }

    @Test
    void hostCallBudgetResetsOnEachScriptCall() throws Exception {
        McvHost small = new McvHost(5);
        rt.setHostCallback(small::invoke);
        rt.load("t.uijs", "function go() { var w = mcv.createWorld(1,1,1); for (var i = 0; i < 10; i++) w.getBlock(0,0,0); }");
        assertThrows(ScriptRuntime.ScriptException.class, () -> call("go"), "예산을 넘으면 중단");
        rt.load("t2.uijs", "function ok() { mcv.createWorld(1,1,1); }");
        call("ok");                                                    // 새 호출이라 예산이 새로 시작된다
    }

    @Test
    void scriptsCannotReachTheRawHostBridgeOrForgeRequestsByOverridingJson() throws Exception {
        rt.load("t.uijs", """
                function probe() { ui.log('host:' + typeof __host); }
                function go() {
                  JSON.stringify = function () { return '{"cmd":"beginCall"}'; };
                  mcv.createWorld(1,1,1);
                }
                """);
        assertTrue(call("probe").contains("host:undefined"), "__host 는 지워진다");
        call("go");                                                    // 응답 자체는 위조되어도(기존 동작) 호스트 검증을 거친다
        assertEquals(1, host.worlds().size(), "JSON 을 덮어써도 mcv 요청은 원본 stringify 로 만들어진다");
    }

    @Test
    void withoutACallbackMcvFailsInsteadOfCrashing() throws Exception {
        GraalScriptRuntime bare = new GraalScriptRuntime();
        try {
            bare.load("t.uijs", "function go() { mcv.createWorld(1,1,1); }");
            assertThrows(ScriptRuntime.ScriptException.class, () -> bare.call("{\"type\":\"call\",\"fn\":\"go\",\"event\":{},\"state\":{}}"));
        } finally {
            bare.close();
        }
    }

    @Test
    void tickMessageCallsOnTick() throws Exception {
        rt.load("t.uijs", "function onTick(n) { ui.log('tick ' + n); }");
        assertTrue(rt.call("{\"type\":\"tick\",\"tick\":7,\"state\":{}}").contains("tick 7"));
    }

    @Test
    void customLimitsAreHonored() throws Exception {
        GraalScriptRuntime tight = new GraalScriptRuntime(1_000, 300L);
        try {
            tight.load("t.uijs", "function spin() { for (var i = 0; i < 100000; i++) {} }");
            assertThrows(ScriptRuntime.ScriptException.class, () -> tight.call("{\"type\":\"call\",\"fn\":\"spin\",\"event\":{},\"state\":{}}"));
        } finally {
            tight.close();
        }
    }
}
