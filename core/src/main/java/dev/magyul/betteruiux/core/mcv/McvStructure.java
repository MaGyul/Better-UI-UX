package dev.magyul.betteruiux.core.mcv;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 마인크래프트 구조물 파일(스트럭처 블록이 저장하는 {@code .nbt})을 읽어 가상 월드에 놓는다.
 * 지원: {@code size}, {@code palette}(또는 {@code palettes} 의 첫 번째), {@code blocks}(위치·상태·블록 엔티티 NBT).
 * 엔티티({@code entities})는 무시한다. 공기/구조물 빈칸/구조물 블록/직소 블록은 놓지 않는다.
 */
public final class McvStructure {
    /** 놓을 블록 하나. */
    public record Placement(int x, int y, int z, McvBlock block) { }

    /** {@code placements} 는 공기를 뺀 블록들, {@code droppedNbt} 는 너무 길어 버린 블록 엔티티 NBT 의 수. */
    public record Parsed(int sizeX, int sizeY, int sizeZ, List<Placement> placements, int droppedNbt) { }

    /** 놓은 결과: 놓은 블록 수와 구조물 크기. */
    public record Result(int sizeX, int sizeY, int sizeZ, int blocks, int droppedNbt) { }

    private McvStructure() { }

    private static final java.util.Set<String> SKIP = java.util.Set.of(
            "minecraft:structure_void", "minecraft:structure_block", "minecraft:jigsaw");

    /** 구조물 파일 바이트를 읽는다. 형식이 맞지 않으면 {@link IllegalArgumentException}. */
    public static Parsed parse(byte[] file) {
        Map<String, Object> root = McvNbt.read(file);
        int[] size = intTriple(root.get("size"), "size");
        if (size[0] < 1 || size[1] < 1 || size[2] < 1 || size[0] > McvWorld.MAX_AXIS || size[1] > McvWorld.MAX_AXIS || size[2] > McvWorld.MAX_AXIS) {
            throw new IllegalArgumentException("구조물 크기가 올바르지 않습니다: " + size[0] + "x" + size[1] + "x" + size[2]);
        }
        Object paletteObj = root.get("palette");
        if (paletteObj == null && root.get("palettes") instanceof List<?> ps && !ps.isEmpty()) paletteObj = ps.get(0);
        if (!(paletteObj instanceof List<?> palette)) throw new IllegalArgumentException("구조물에 palette 가 없습니다");
        List<McvBlock> states = new ArrayList<>(palette.size());
        for (Object o : palette) {
            if (!(o instanceof Map<?, ?> entry) || !(entry.get("Name") instanceof String name)) {
                throw new IllegalArgumentException("palette 항목에 Name 이 없습니다");
            }
            StringBuilder text = new StringBuilder(name);
            if (entry.get("Properties") instanceof Map<?, ?> props && !props.isEmpty()) {
                text.append('[');
                boolean first = true;
                for (Map.Entry<?, ?> p : props.entrySet()) {
                    if (!first) text.append(',');
                    first = false;
                    text.append(p.getKey()).append('=').append(p.getValue());
                }
                text.append(']');
            }
            states.add(McvBlock.parse(text.toString(), null));
        }
        if (!(root.get("blocks") instanceof List<?> blocks)) throw new IllegalArgumentException("구조물에 blocks 가 없습니다");
        List<Placement> out = new ArrayList<>();
        int dropped = 0;
        for (Object o : blocks) {
            if (!(o instanceof Map<?, ?> b)) throw new IllegalArgumentException("blocks 항목이 올바르지 않습니다");
            int[] pos = intTriple(b.get("pos"), "pos");
            if (!(b.get("state") instanceof Number st) || st.intValue() < 0 || st.intValue() >= states.size()) {
                throw new IllegalArgumentException("blocks 의 state 가 palette 밖입니다");
            }
            if (pos[0] < 0 || pos[1] < 0 || pos[2] < 0 || pos[0] >= size[0] || pos[1] >= size[1] || pos[2] >= size[2]) {
                throw new IllegalArgumentException("블록 위치가 구조물 크기 밖입니다: " + pos[0] + "," + pos[1] + "," + pos[2]);
            }
            McvBlock state = states.get(st.intValue());
            if (state.isAir() || SKIP.contains(state.id())) continue;
            McvBlock block = state;
            if (b.get("nbt") instanceof Map<?, ?> nbt && !nbt.isEmpty()) {
                String snbt = McvNbt.toSnbt(nbt);
                if (snbt.length() <= McvBlock.MAX_NBT) block = new McvBlock(state.id(), state.state(), snbt);
                else dropped++;
            }
            out.add(new Placement(pos[0], pos[1], pos[2], block));
        }
        return new Parsed(size[0], size[1], size[2], out, dropped);
    }

    /**
     * 구조물을 월드의 (ox, oy, oz) 에 놓는다. 구조물이 월드 밖으로 나가면 <b>아무것도 놓지 않고</b> 거부한다.
     * 이미 있는 블록은 구조물의 블록으로 덮어쓰고, 구조물의 빈 칸은 건드리지 않는다.
     */
    public static Result place(McvWorld world, Parsed s, int ox, int oy, int oz) {
        if (ox < 0 || oy < 0 || oz < 0 || ox + s.sizeX() > world.sizeX() || oy + s.sizeY() > world.sizeY() || oz + s.sizeZ() > world.sizeZ()) {
            throw new IllegalArgumentException("구조물(" + s.sizeX() + "x" + s.sizeY() + "x" + s.sizeZ() + ")이 월드("
                    + world.sizeX() + "x" + world.sizeY() + "x" + world.sizeZ() + ") 밖으로 나갑니다: 위치 " + ox + "," + oy + "," + oz);
        }
        if ((long) world.blockCount() + s.placements().size() > McvWorld.MAX_BLOCKS) {
            throw new IllegalArgumentException("블록이 너무 많습니다(" + McvWorld.MAX_BLOCKS + ")");
        }
        for (Placement p : s.placements()) world.setBlock(ox + p.x(), oy + p.y(), oz + p.z(), p.block());
        return new Result(s.sizeX(), s.sizeY(), s.sizeZ(), s.placements().size(), s.droppedNbt());
    }

    private static int[] intTriple(Object o, String what) {
        if (o instanceof List<?> l && l.size() == 3 && l.get(0) instanceof Number && l.get(1) instanceof Number && l.get(2) instanceof Number) {
            return new int[] {((Number) l.get(0)).intValue(), ((Number) l.get(1)).intValue(), ((Number) l.get(2)).intValue()};
        }
        throw new IllegalArgumentException("구조물의 " + what + " 는 정수 3개여야 합니다");
    }
}
