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
                  cow.setPosition(3, 1, 3);
                  cow.customName = 'Bessie';
                  var cam = w.getCamera();
                  cam.setYaw(90); cam.setProjection('perspective');
                  seen += '|' + cow.x + '|' + cow.customName + '|' + cam.get().yaw;
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
    void entityMotionIsDrivenByTheWorldClock() throws Exception {
        rt.load("t.uijs", """
                var cow;
                function go() {
                  var w = mcv.createWorld(12, 4, 4);
                  cow = w.spawnEntity(1, 1, 1, 'cow');
                  cow.moveTo(5, 1, 1, 8);
                  ui.log(String(cow.moving) + '|' + cow.x);
                }
                function after() { ui.log(String(cow.moving) + '|' + cow.x); }
                """);
        assertTrue(call("go").contains("true|1"), "이동 계획만 걸렸을 뿐 아직 한 틱도 안 갔다");
        var w = host.world(1);
        w.advance(0, 100);
        for (int i = 1; i <= 8; i++) w.advance(i * 50_000_000L, 100);
        String out = call("after");
        assertTrue(out.contains("false|5"), out);
        assertTrue(w.entities().iterator().next().x() == 5.0);
    }

    @Test
    void entitiesAreUsedDirectlyWithoutMethodsOrFieldsObjects() throws Exception {
        rt.load("t.uijs", """
                function go() {
                  var w = mcv.createWorld(6, 3, 6);
                  var cow = w.spawnEntity(1, 1, 1, 'cow', '{Age:-24000}');
                  cow.setPosition(2, 1, 3);
                  cow.x = 4;                                  // 한 축만 바꿔도 된다
                  cow.yaw = 90;
                  cow.customName = 'Bessie';
                  cow.mergeNbt('{Silent:1b}');
                  ui.log('pos=' + cow.x + ',' + cow.y + ',' + cow.z + ' yaw=' + cow.yaw + ' name=' + cow.customName
                         + ' type=' + cow.type + ' id=' + cow.id + ' str=' + String(cow) + ' json=' + JSON.stringify({e: cow}));
                  var pig = w.spawnEntity(0, 1, 0, 'pig');
                  pig.remove();
                }
                """);
        String out = call("go");
        assertTrue(out.contains("pos=4,1,3 yaw=90 name=Bessie type=minecraft:cow id=1 str=Entity#1 json={\\\"e\\\":{\\\"id\\\":1}}"), out);
        var w = host.world(1);
        assertEquals(1, w.entities().size(), "remove() 로 돼지는 사라졌다");
        var cow = w.entities().iterator().next();
        assertEquals("{Age:-24000}", cow.pollPendingNbt(), "소환할 때 준 NBT 가 먼저 걸린다");
        assertEquals("{Silent:1b}", cow.pollPendingNbt());
        assertEquals(null, cow.pollPendingNbt());
    }

    @Test
    void spawnWithInvalidNbtIsRejectedAndLeavesNoEntity() throws Exception {
        rt.load("t.uijs", """
                function go() {
                  var w = mcv.createWorld(3, 3, 3);
                  try { w.spawnEntity(1, 1, 1, 'cow', 'not nbt'); } catch (e) { ui.log('err:' + e.message); }
                }
                """);
        String out = call("go");
        assertTrue(out.contains("err:"), out);
        assertEquals(0, host.world(1).entities().size());
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
    void projectAndRaycastAreAvailableToScripts() throws Exception {
        rt.load("t.uijs", """
                var w;
                function setup() { w = mcv.createWorld(4, 4, 4); w.setBlock(1, 1, 1, 'stone'); }
                function attachIt() { w.attach('#v'); }
                function probe() {
                  var p = w.project(1.5, 1.5, 1.5);
                  var h = p ? w.raycast(p.x, p.y) : null;
                  ui.log('p=' + (p ? 'yes' : 'none') + ' hit=' + (h ? h.x + ',' + h.y + ',' + h.z + ',' + h.block : 'none'));
                }
                """);
        call("setup");
        assertTrue(call("probe").contains("p=none hit=none"), "아직 그려진 적이 없으면 null");
        host.viewer("v").setViewport(new dev.magyul.betteruiux.core.mcv.McvViewer.Viewport(0, 0, 200, 200, 20));
        call("attachIt");
        String out = call("probe");
        assertTrue(out.contains("p=yes hit=1,1,1,minecraft:stone"), out);
    }

    @Test
    void loadStructureIsAvailableToScriptsAndErrorsAreCatchable() throws Exception {
        host.setStructureLoader(name -> name.equals("one") ? ONE_STONE : null);
        rt.load("t.uijs", """
                function go() {
                  var w = mcv.createWorld(4, 4, 4);
                  var r = w.loadStructure('one', 1, 1, 1);
                  var msg = 'none';
                  try { w.loadStructure('nope'); } catch (e) { msg = e.message; }
                  ui.log('blocks=' + r.blocks + ' size=' + r.size.join('x') + ' at=' + w.getBlock(1, 1, 1).id + ' err=' + msg);
                }
                """);
        String out = call("go");
        assertTrue(out.contains("blocks=1 size=1x1x1 at=minecraft:stone err="), out);
        assertTrue(out.contains("nope"), "없는 파일은 이름이 담긴 오류: " + out);
    }

    /** size 1x1x1, palette [stone], blocks [{pos [0,0,0], state 0}] — 압축 안 한 NBT. */
    private static final byte[] ONE_STONE = oneStone();

    private static byte[] oneStone() {
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream o = new java.io.DataOutputStream(bos);
            o.writeByte(10);
            o.writeUTF("");
            o.writeByte(9);
            o.writeUTF("size");
            o.writeByte(3);
            o.writeInt(3);
            o.writeInt(1);
            o.writeInt(1);
            o.writeInt(1);
            o.writeByte(9);
            o.writeUTF("palette");
            o.writeByte(10);
            o.writeInt(1);
            o.writeByte(8);
            o.writeUTF("Name");
            o.writeUTF("minecraft:stone");
            o.writeByte(0);
            o.writeByte(9);
            o.writeUTF("blocks");
            o.writeByte(10);
            o.writeInt(1);
            o.writeByte(9);
            o.writeUTF("pos");
            o.writeByte(3);
            o.writeInt(3);
            o.writeInt(0);
            o.writeInt(0);
            o.writeInt(0);
            o.writeByte(3);
            o.writeUTF("state");
            o.writeInt(0);
            o.writeByte(0);
            o.writeByte(0);
            return bos.toByteArray();
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void blockEventAndSetOpenAreAvailableToScripts() throws Exception {
        rt.load("t.uijs", """
                function go() {
                  var w = mcv.createWorld(4, 4, 4);
                  w.setBlock(1, 1, 1, 'chest[facing=south]');
                  w.setOpen(1, 1, 1, true);
                  w.blockEvent(1, 1, 1, 1, 0);
                  w.setOpen(1, 1, 1);                       // 인자를 생략하면 열기
                  try { w.setOpen(3, 3, 3, true); } catch (e) { ui.log('err:' + e.message); }
                }
                """);
        String out = call("go");
        assertTrue(out.contains("err:"), "블록이 없는 곳은 오류: " + out);
        var log = host.world(1).blockEventsAt(1, 1, 1);
        assertEquals(3, log.size());
        assertEquals(1, log.get(0).param());
        assertEquals(0, log.get(1).param());
        assertEquals(1, log.get(2).param());
    }

    @Test
    void ringBellSendsTheBlockEventWithTheDirection() throws Exception {
        rt.load("t.uijs", """
                function go() {
                  var w = mcv.createWorld(4, 4, 4);
                  w.setBlock(1, 1, 1, 'bell[attachment=floor,facing=north]');
                  w.ringBell(1, 1, 1);                 // 기본 north
                  w.ringBell(1, 1, 1, 'east');
                  try { w.ringBell(1, 1, 1, 'sideways'); } catch (e) { ui.log('err:' + e.message); }
                }
                """);
        String out = call("go");
        assertTrue(out.contains("err:"), out);
        var log = host.world(1).blockEventsAt(1, 1, 1);
        assertEquals(2, log.size(), "잘못된 방향은 이벤트를 남기지 않는다");
        assertEquals(1, log.get(0).id());
        assertEquals(2, log.get(0).param(), "north = 2");
        assertEquals(5, log.get(1).param(), "east = 5");
    }

    @Test
    void tickMessageCallsOnTick() throws Exception {
        rt.load("t.uijs", "function onTick(n, w) { ui.log('tick ' + n + ' world ' + w); }");
        assertTrue(rt.call("{\"type\":\"tick\",\"tick\":7,\"world\":2}").contains("tick 7 world 2"));
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
