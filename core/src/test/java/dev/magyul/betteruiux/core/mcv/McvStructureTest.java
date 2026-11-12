package dev.magyul.betteruiux.core.mcv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.magyul.betteruiux.core.script.Json;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

/** 구조물(.nbt) 읽기와 놓기. 테스트용 NBT 는 아래 작은 쓰기 도구로 직접 만든다. */
class McvStructureTest {
    // ---- 테스트용 NBT 쓰기 ----

    private static void name(DataOutputStream o, int type, String key) throws IOException {
        o.writeByte(type);
        o.writeUTF(key);
    }

    private static void intList(DataOutputStream o, String key, int... v) throws IOException {
        name(o, 9, key);
        o.writeByte(3);
        o.writeInt(v.length);
        for (int x : v) o.writeInt(x);
    }

    /** size 3x2x3: (0,0,0) stone, (1,0,0) 두 번째 state, (2,0,0) air, (0,1,0) chest + nbt. */
    private static byte[] build(boolean gzip, int stateForSecond) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bos);
        o.writeByte(10);
        o.writeUTF("");
        intList(o, "size", 3, 2, 3);
        name(o, 3, "DataVersion");
        o.writeInt(4000);
        name(o, 9, "palette");
        o.writeByte(10);
        o.writeInt(4);
        paletteEntry(o, "minecraft:stone", null, null);
        paletteEntry(o, "minecraft:oak_stairs", "facing", "east");
        paletteEntry(o, "minecraft:air", null, null);
        paletteEntry(o, "minecraft:chest", null, null);
        name(o, 9, "blocks");
        o.writeByte(10);
        o.writeInt(4);
        block(o, 0, 0, 0, 0, false);
        block(o, 1, 0, 0, stateForSecond, false);
        block(o, 2, 0, 0, 2, false);
        block(o, 0, 1, 0, 3, true);
        o.writeByte(0);                                               // 루트 끝
        o.flush();
        byte[] raw = bos.toByteArray();
        if (!gzip) return raw;
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        try (GZIPOutputStream g = new GZIPOutputStream(z)) {
            g.write(raw);
        }
        return z.toByteArray();
    }

    private static void paletteEntry(DataOutputStream o, String nameValue, String prop, String value) throws IOException {
        name(o, 8, "Name");
        o.writeUTF(nameValue);
        if (prop != null) {
            name(o, 10, "Properties");
            name(o, 8, prop);
            o.writeUTF(value);
            o.writeByte(0);
        }
        o.writeByte(0);
    }

    private static void block(DataOutputStream o, int x, int y, int z, int state, boolean withNbt) throws IOException {
        intList(o, "pos", x, y, z);
        name(o, 3, "state");
        o.writeInt(state);
        if (withNbt) {
            name(o, 10, "nbt");
            name(o, 8, "id");
            o.writeUTF("minecraft:chest");
            name(o, 1, "Lock");
            o.writeByte(1);
            o.writeByte(0);
        }
        o.writeByte(0);
    }

    // ---- 테스트 ----

    @Test
    void parsesGzipAndPlainFilesAndSkipsAir() throws IOException {
        for (boolean gzip : new boolean[] {true, false}) {
            McvStructure.Parsed p = McvStructure.parse(build(gzip, 1));
            assertEquals(3, p.sizeX());
            assertEquals(2, p.sizeY());
            assertEquals(3, p.sizeZ());
            assertEquals(3, p.placements().size(), "공기는 놓지 않는다");
            McvBlock stairs = p.placements().get(1).block();
            assertEquals("minecraft:oak_stairs[facing=east]", stairs.stateString());
            McvBlock chest = p.placements().get(2).block();
            assertEquals("minecraft:chest", chest.id());
            assertEquals("{id:\"minecraft:chest\",Lock:1b}", chest.nbt(), "블록 엔티티 NBT 는 SNBT 로 보관한다");
        }
    }

    @Test
    void placesAtAnOffsetAndRefusesToOverflowTheWorld() throws IOException {
        McvStructure.Parsed p = McvStructure.parse(build(true, 1));
        McvWorld w = new McvWorld(6, 4, 6);
        McvStructure.Result r = McvStructure.place(w, p, 2, 1, 1);
        assertEquals(3, r.blocks());
        assertEquals("minecraft:stone", w.getBlock(2, 1, 1).id());
        assertEquals("minecraft:oak_stairs[facing=east]", w.getBlock(3, 1, 1).stateString());
        assertEquals("minecraft:chest", w.getBlock(2, 2, 1).id());
        assertTrue(w.getBlock(4, 1, 1).isAir());

        McvWorld small = new McvWorld(3, 1, 3);
        assertThrows(IllegalArgumentException.class, () -> McvStructure.place(small, p, 0, 0, 0), "높이가 모자라면 거부");
        assertEquals(0, small.blockCount(), "거부되면 아무것도 놓지 않는다");
        assertThrows(IllegalArgumentException.class, () -> McvStructure.place(w, p, 5, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> McvStructure.place(w, p, -1, 0, 0));
    }

    @Test
    void rejectsBrokenFiles() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> McvStructure.parse(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> McvStructure.parse("not nbt at all".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> McvStructure.parse(build(true, 9)), "palette 밖의 state");
        byte[] truncated = build(false, 1);
        assertThrows(IllegalArgumentException.class, () -> McvStructure.parse(java.util.Arrays.copyOf(truncated, truncated.length / 2)));
        byte[] bomb = new byte[3 * 1024 * 1024];                      // 압축 전 크기 상한
        assertThrows(IllegalArgumentException.class, () -> McvNbt.read(bomb));
    }

    @Test
    void hostLoadsByNameThroughTheLoaderAndValidatesNames() throws IOException {
        byte[] file = build(true, 1);
        McvHost h = new McvHost();
        h.setStructureLoader(name -> name.equals("house") ? file : null);
        int w = ((Double) ((Map<?, ?>) Json.parse(h.invoke("{\"cmd\":\"createWorld\",\"x\":6,\"y\":4,\"z\":6}"))).get("value")).intValue();
        Map<?, ?> ok = (Map<?, ?>) Json.parse(h.invoke("{\"cmd\":\"loadStructure\",\"world\":" + w + ",\"name\":\"house\",\"x\":1,\"y\":1,\"z\":1}"));
        assertEquals(Boolean.TRUE, ok.get("ok"), ok.toString());
        Map<?, ?> value = (Map<?, ?>) ok.get("value");
        assertEquals(3.0, value.get("blocks"));
        assertEquals(List.of(3.0, 2.0, 3.0), value.get("size"));
        assertEquals("minecraft:stone", h.world(w).getBlock(1, 1, 1).id());

        for (String bad : new String[] {"missing", "../secret", "/abs", "a b", ""}) {
            Map<?, ?> r = (Map<?, ?>) Json.parse(h.invoke("{\"cmd\":\"loadStructure\",\"world\":" + w + ",\"name\":\"" + bad + "\"}"));
            assertEquals(Boolean.FALSE, r.get("ok"), bad);
        }
        McvHost bare = new McvHost();
        int w2 = ((Double) ((Map<?, ?>) Json.parse(bare.invoke("{\"cmd\":\"createWorld\",\"x\":2,\"y\":2,\"z\":2}"))).get("value")).intValue();
        assertFalse((Boolean) ((Map<?, ?>) Json.parse(bare.invoke("{\"cmd\":\"loadStructure\",\"world\":" + w2 + ",\"name\":\"house\"}"))).get("ok"),
                "로더가 없으면 오류");
    }

    @Test
    void snbtWritesAllTagKinds() {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("b", (byte) 1);
        m.put("s", (short) 2);
        m.put("i", 3);
        m.put("l", 4L);
        m.put("f", 1.5f);
        m.put("d", 2.5);
        m.put("str", "a\"b");
        m.put("list", List.of(1, 2));
        m.put("ba", new byte[] {1, 2});
        m.put("ia", new int[] {3});
        m.put("la", new long[] {5});
        m.put("weird key", 0);
        assertEquals("{b:1b,s:2s,i:3,l:4L,f:1.5f,d:2.5d,str:\"a\\\"b\",list:[1,2],ba:[B;1b,2b],ia:[I;3],la:[L;5L],\"weird key\":0}", McvNbt.toSnbt(m));
    }
}
