package dev.magyul.betteruiux.core.mcv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.script.Json;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McvHostTest {
    @SuppressWarnings("unchecked")
    private static Map<String, Object> call(McvHost h, String json) {
        return (Map<String, Object>) Json.parse(h.invoke(json));
    }

    private static int world(McvHost h, int x, int y, int z) {
        return ((Double) call(h, "{\"cmd\":\"createWorld\",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + "}").get("value")).intValue();
    }

    @Test
    void blocksRoundTripAndAirErases() {
        McvHost h = new McvHost();
        int w = world(h, 8, 8, 8);
        assertTrue((Boolean) call(h, "{\"cmd\":\"setBlock\",\"world\":" + w + ",\"x\":1,\"y\":2,\"z\":3,\"block\":\"oak_stairs[half=bottom,facing=east]\"}").get("ok"));
        @SuppressWarnings("unchecked")
        Map<String, Object> b = (Map<String, Object>) call(h, "{\"cmd\":\"getBlock\",\"world\":" + w + ",\"x\":1,\"y\":2,\"z\":3}").get("value");
        assertEquals("minecraft:oak_stairs[facing=east,half=bottom]", b.get("block"), "상태는 정렬되고 네임스페이스가 붙는다");
        call(h, "{\"cmd\":\"setBlock\",\"world\":" + w + ",\"x\":1,\"y\":2,\"z\":3,\"block\":\"air\"}");
        @SuppressWarnings("unchecked")
        Map<String, Object> gone = (Map<String, Object>) call(h, "{\"cmd\":\"getBlock\",\"world\":" + w + ",\"x\":1,\"y\":2,\"z\":3}").get("value");
        assertEquals("minecraft:air", gone.get("id"));
        assertEquals(0, h.world(w).blockCount());
    }

    @Test
    void outOfBoundsAndBadInputAreRejectedNotThrown() {
        McvHost h = new McvHost();
        int w = world(h, 4, 4, 4);
        assertFalse((Boolean) call(h, "{\"cmd\":\"setBlock\",\"world\":" + w + ",\"x\":9,\"y\":0,\"z\":0,\"block\":\"stone\"}").get("ok"));
        assertFalse((Boolean) call(h, "{\"cmd\":\"setBlock\",\"world\":" + w + ",\"x\":0,\"y\":0,\"z\":0,\"block\":\"Bad Id!\"}").get("ok"));
        assertFalse((Boolean) call(h, "{\"cmd\":\"createWorld\",\"x\":9999,\"y\":1,\"z\":1}").get("ok"));
        assertFalse((Boolean) call(h, "{\"cmd\":\"nope\",\"world\":" + w + "}").get("ok"));
        assertFalse((Boolean) call(h, "not json").get("ok"));
    }

    @Test
    void fillAndJournalDrain() {
        McvHost h = new McvHost();
        int w = world(h, 4, 4, 4);
        Map<String, Object> r = call(h, "{\"cmd\":\"fill\",\"world\":" + w + ",\"x1\":0,\"y1\":0,\"z1\":0,\"x2\":3,\"y2\":0,\"z2\":3,\"block\":\"white_concrete\"}");
        assertEquals(16.0, r.get("value"));
        McvWorld.Drained d = h.world(w).drain();
        assertEquals(16, d.changes().size());
        assertFalse(d.resync());
        assertEquals(0, h.world(w).drain().changes().size(), "한 번 비우면 비어 있다");
    }

    @Test
    void entitiesAreWhitelisted() {
        McvHost h = new McvHost();
        int w = world(h, 4, 4, 4);
        int e = ((Double) call(h, "{\"cmd\":\"spawnEntity\",\"world\":" + w + ",\"x\":1,\"y\":1,\"z\":1,\"type\":\"cow\"}").get("value")).intValue();
        assertTrue((Boolean) call(h, "{\"cmd\":\"entityCall\",\"world\":" + w + ",\"entity\":" + e + ",\"method\":\"setPosition\",\"args\":[2,1,2]}").get("ok"));
        assertEquals(2.0, call(h, "{\"cmd\":\"entityGet\",\"world\":" + w + ",\"entity\":" + e + ",\"name\":\"x\"}").get("value"));
        assertFalse((Boolean) call(h, "{\"cmd\":\"entityCall\",\"world\":" + w + ",\"entity\":" + e + ",\"method\":\"getClass\"}").get("ok"));
        assertFalse((Boolean) call(h, "{\"cmd\":\"entitySet\",\"world\":" + w + ",\"entity\":" + e + ",\"name\":\"x\",\"value\":5}").get("ok"), "위치는 setPosition 으로만");
        assertTrue((Boolean) call(h, "{\"cmd\":\"entitySet\",\"world\":" + w + ",\"entity\":" + e + ",\"name\":\"customName\",\"value\":\"Bessie\"}").get("ok"));
        assertEquals("minecraft:cow", h.world(w).entity(e).type());
    }

    @Test
    void callBudgetIsEnforcedAndResetPerScriptCall() {
        McvHost h = new McvHost(3);
        h.beginCall();
        for (int i = 0; i < 3; i++) assertTrue((Boolean) call(h, "{\"cmd\":\"createWorld\",\"x\":1,\"y\":1,\"z\":1}").get("ok"));
        assertFalse((Boolean) call(h, "{\"cmd\":\"createWorld\",\"x\":1,\"y\":1,\"z\":1}").get("ok"));
        h.beginCall();
        assertTrue((Boolean) call(h, "{\"cmd\":\"createWorld\",\"x\":1,\"y\":1,\"z\":1}").get("ok"));
    }

    @Test
    void cameraAndViewArea() {
        McvHost h = new McvHost();
        int w = world(h, 8, 8, 8);
        call(h, "{\"cmd\":\"camera\",\"world\":" + w + ",\"op\":\"setYaw\",\"args\":[-90]}");
        call(h, "{\"cmd\":\"camera\",\"world\":" + w + ",\"op\":\"setProjection\",\"args\":[\"perspective\"]}");
        assertEquals(270f, h.world(w).camera().yaw());
        assertEquals(McvCamera.Projection.PERSPECTIVE, h.world(w).camera().projection());
        call(h, "{\"cmd\":\"setViewArea\",\"world\":" + w + ",\"x1\":0,\"y1\":0,\"z1\":0,\"x2\":3,\"y2\":3,\"z2\":3}");
        assertTrue(h.world(w).inView(3.5, 0, 0));
        assertFalse(h.world(w).inView(4.5, 0, 0));
        assertEquals(List.of(0, 0, 0), List.of(0, 0, 0));
    }

    @Test
    void entityNbtMergeRequestsAreValidatedQueuedAndBounded() {
        McvHost h = new McvHost();
        int w = world(h, 4, 4, 4);
        int e = ((Double) call(h, "{\"cmd\":\"spawnEntity\",\"world\":" + w + ",\"x\":1,\"y\":1,\"z\":1,\"type\":\"cow\"}").get("value")).intValue();
        String base = "{\"cmd\":\"entityCall\",\"world\":" + w + ",\"entity\":" + e + ",\"method\":";
        assertTrue((Boolean) call(h, base + "\"mergeNbt\",\"args\":[\"{Invisible:1b}\"]}").get("ok"));
        assertEquals("{Invisible:1b}", h.world(w).entity(e).pollPendingNbt());
        assertEquals(null, h.world(w).entity(e).pollPendingNbt());
        assertFalse((Boolean) call(h, base + "\"mergeNbt\",\"args\":[\"not nbt\"]}").get("ok"), "{...} 가 아니면 거부");
        assertFalse((Boolean) call(h, base + "\"mergeNbt\",\"args\":[5]}").get("ok"), "글자가 아니면 거부");
        assertFalse((Boolean) call(h, base + "\"mergeNbt\",\"args\":[\"{a:\\\"" + "x".repeat(5000) + "\\\"}\"]}").get("ok"), "너무 길면 거부");
        for (int i = 0; i < McvEntity.MAX_PENDING_NBT; i++) assertTrue((Boolean) call(h, base + "\"mergeNbt\",\"args\":[\"{}\"]}").get("ok"));
        assertFalse((Boolean) call(h, base + "\"mergeNbt\",\"args\":[\"{}\"]}").get("ok"), "밀린 요청이 너무 많으면 거부");
        assertEquals(null, call(h, base + "\"getNbt\"}").get("value"), "스냅샷은 백엔드가 채우기 전엔 null");
        h.world(w).entity(e).setNbtSnapshot("{Age:0}");
        assertEquals("{Age:0}", call(h, base + "\"getNbt\"}").get("value"));
    }

    @Test
    void spawnEntityAcceptsNbtAndRefusesBadNbtWithoutCreatingAnything() {
        McvHost h = new McvHost();
        int w = world(h, 4, 4, 4);
        String spawn = "{\"cmd\":\"spawnEntity\",\"world\":" + w + ",\"x\":1,\"y\":1,\"z\":1,\"type\":\"cow\",\"nbt\":";
        Map<String, Object> ok = call(h, spawn + "\"{Age:-24000}\"}");
        assertTrue((Boolean) ok.get("ok"));
        int id = ((Double) ok.get("value")).intValue();
        assertEquals("{Age:-24000}", h.world(w).entity(id).pollPendingNbt());
        assertFalse((Boolean) call(h, spawn + "\"nope\"}").get("ok"));
        assertFalse((Boolean) call(h, spawn + "\"{" + "x".repeat(5000) + "}\"}").get("ok"));
        assertEquals(1, h.world(w).entities().size(), "잘못된 NBT 로는 엔티티가 만들어지지 않는다");
        assertTrue((Boolean) call(h, spawn + "null}").get("ok"), "nbt 를 안 줘도 된다");
    }

    @Test
    void viewersBindToWorldsById() {
        McvHost h = new McvHost();
        int w1 = world(h, 4, 4, 4), w2 = world(h, 4, 4, 4);
        assertEquals(h.world(w1), h.resolveWorld("any"), "연결이 하나도 없으면 첫 월드(간단한 문서)");
        assertTrue((Boolean) call(h, "{\"cmd\":\"attach\",\"world\":" + w2 + ",\"viewer\":\"#right\"}").get("ok"));
        assertEquals(h.world(w2), h.resolveWorld("right"), "# 는 있어도 없어도 된다");
        assertEquals(null, h.resolveWorld("left"), "다른 뷰어만 연결되어 있으면 연결 안 된 뷰어는 아무것도 안 보인다");
        call(h, "{\"cmd\":\"attach\",\"world\":" + w1 + ",\"viewer\":\"left\"}");
        call(h, "{\"cmd\":\"attach\",\"world\":" + w1 + ",\"viewer\":\"right\"}");   // 다시 붙이면 옮긴다
        assertEquals(h.world(w1), h.resolveWorld("right"));
        call(h, "{\"cmd\":\"detach\",\"world\":" + w1 + ",\"viewer\":\"left\"}");
        assertEquals(null, h.resolveWorld("left"));
        for (String bad : new String[] {"", "a b", "#", "x".repeat(70), "<script>"}) {
            assertFalse((Boolean) call(h, "{\"cmd\":\"attach\",\"world\":" + w1 + ",\"viewer\":\"" + bad + "\"}").get("ok"), bad);
        }
    }

    @Test
    void cameraCommandsGoToTheViewerOrTheWorldDefaultUntilOneExists() {
        McvHost h = new McvHost();
        int w = world(h, 4, 4, 4);
        String cam = "{\"cmd\":\"camera\",\"world\":" + w + ",\"op\":\"setYaw\",\"args\":[100]";
        call(h, cam + "}");
        assertEquals(100f, h.world(w).camera().yaw(), "뷰어가 없으면 월드의 기본 카메라");
        call(h, "{\"cmd\":\"attach\",\"world\":" + w + ",\"viewer\":\"a\"}");
        call(h, "{\"cmd\":\"attach\",\"world\":" + w + ",\"viewer\":\"b\"}");
        call(h, cam.replace("100", "200") + "}");
        assertEquals(200f, h.viewer("a").camera().yaw(), "선택자가 없으면 월드에 가장 먼저 연결된 뷰어");
        assertEquals(225f, h.viewer("b").camera().yaw());
        call(h, cam.replace("100", "300") + ",\"viewer\":\"#b\"}");
        assertEquals(300f, h.viewer("b").camera().yaw(), "선택자를 주면 그 뷰어");
        assertEquals(200f, h.viewer("a").camera().yaw());
        assertEquals(100f, h.world(w).camera().yaw(), "월드의 기본 카메라는 그대로");
    }

    @Test
    void projectAndRaycastNeedAViewerThatWasDrawn() {
        McvHost h = new McvHost();
        int w = world(h, 4, 4, 4);
        h.world(w).setBlock(1, 1, 1, McvBlock.parse("stone", null));
        String proj = "{\"cmd\":\"project\",\"world\":" + w + ",\"x\":1.5,\"y\":1.5,\"z\":1.5";
        assertEquals(null, call(h, proj + "}").get("value"), "뷰어가 없다");
        call(h, "{\"cmd\":\"attach\",\"world\":" + w + ",\"viewer\":\"v\"}");
        assertEquals(null, call(h, proj + "}").get("value"), "아직 그려지지 않았다");
        h.viewer("v").setViewport(new McvViewer.Viewport(0, 0, 100, 100, 10));
        assertTrue(call(h, proj + "}").get("value") instanceof Map<?, ?>);
        assertTrue(call(h, proj + ",\"viewer\":\"v\"}").get("value") instanceof Map<?, ?>);
        Map<String, Object> p = call(h, proj + ",\"viewer\":\"nope\"}");
        assertEquals(null, p.get("value"), "만들어졌지만 그려진 적 없는 뷰어");
    }

    @Test
    void blockEventsAreLoggedPerBlockInOrderAndDroppedWhenTheBlockChanges() {
        McvHost h = new McvHost();
        int w = world(h, 4, 4, 4);
        String ev = "{\"cmd\":\"blockEvent\",\"world\":" + w + ",\"x\":1,\"y\":1,\"z\":1,";
        assertFalse((Boolean) call(h, ev + "\"id\":1,\"param\":1}").get("ok"), "블록이 없으면 보낼 수 없다");
        h.world(w).setBlock(1, 1, 1, McvBlock.parse("chest", null));
        assertTrue((Boolean) call(h, ev + "\"id\":1,\"param\":1}").get("ok"));
        assertTrue((Boolean) call(h, ev + "\"id\":1,\"param\":0}").get("ok"));
        var log = h.world(w).blockEventsAt(1, 1, 1);
        assertEquals(2, log.size());
        assertEquals(1, log.get(0).param());
        assertEquals(0, log.get(1).param());
        assertTrue(log.get(0).seq() < log.get(1).seq(), "기록은 순서대로");
        assertEquals(0, h.world(w).blockEventsAt(2, 1, 1).size(), "다른 블록의 것은 섞이지 않는다");
        for (int i = 0; i < 50; i++) call(h, ev + "\"id\":1,\"param\":" + i + "}");
        assertEquals(McvWorld.MAX_EVENTS_PER_BLOCK, h.world(w).blockEventsAt(1, 1, 1).size(), "블록마다 최근 것만 남긴다");
        assertEquals(49, h.world(w).blockEventsAt(1, 1, 1).get(McvWorld.MAX_EVENTS_PER_BLOCK - 1).param());
        h.world(w).setBlock(1, 1, 1, McvBlock.parse("chest", null));
        assertEquals(McvWorld.MAX_EVENTS_PER_BLOCK, h.world(w).blockEventsAt(1, 1, 1).size(), "같은 블록을 다시 놓아도(변화 없음) 기록은 그대로");
        h.world(w).setBlock(1, 1, 1, McvBlock.parse("stone", null));
        assertEquals(0, h.world(w).blockEventsAt(1, 1, 1).size(), "다른 블록이 되면 기록을 버린다");
        assertFalse((Boolean) call(h, ev.replace("\"x\":1", "\"x\":9") + "\"id\":1,\"param\":1}").get("ok"), "범위 밖");
    }
}
