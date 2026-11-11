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
}
