package dev.magyul.betteruiux.core.mcv;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 가상 마인크래프트 월드의 데이터 모델(순수 Java, 저장하지 않는다).
 * 스크립트 스레드가 쓰고 변경을 {@link #drain 저널}에 남기면, 렌더 스레드가 비우면서 실제 가상 Level 에 반영한다.
 */
public final class McvWorld {
    public static final int MAX_AXIS = 512, MAX_BLOCKS = 1_000_000, MAX_ENTITIES = 1024, MAX_JOURNAL = 1_000_000;
    public static final float MAX_TICK_RATE = 100f;

    /** 저널 항목. */
    public sealed interface Change {
        record BlockSet(int x, int y, int z, McvBlock block) implements Change { }

        record EntitySpawn(int id, String type, double x, double y, double z) implements Change { }

        record EntityMove(int id, double x, double y, double z) implements Change { }

        record EntityRotate(int id, float yaw, float pitch) implements Change { }

        record EntityField(int id, String name, Object value) implements Change { }

        record EntityRemove(int id) implements Change { }

        record ViewArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) implements Change { }

        record TickRate(float ticksPerSecond) implements Change { }
    }

    /** {@link #drain} 결과: 변경 목록, 그리고 저널이 넘쳐 변경을 잃었다면 전체를 다시 읽어야 한다는 표시. */
    public record Drained(List<Change> changes, boolean resync) { }

    private final int sizeX, sizeY, sizeZ;
    private final Map<Long, McvBlock> blocks = new ConcurrentHashMap<>();
    private final Map<Integer, McvEntity> entities = new ConcurrentHashMap<>();
    private final AtomicInteger nextEntity = new AtomicInteger(1);
    private final ConcurrentLinkedQueue<Change> journal = new ConcurrentLinkedQueue<>();
    private final AtomicInteger journalSize = new AtomicInteger();
    private volatile boolean resync;
    private final McvCamera camera = new McvCamera();
    private volatile int[] viewArea;               // null 이면 월드 전체
    private volatile float tickRate = 20f;
    private volatile Viewport viewport;
    private volatile long tickCount;                  // 쓰기는 advance()(synchronized)만 한다

    public McvWorld(int sizeX, int sizeY, int sizeZ) {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1 || sizeX > MAX_AXIS || sizeY > MAX_AXIS || sizeZ > MAX_AXIS) {
            throw new IllegalArgumentException("월드 크기는 각 축 1~" + MAX_AXIS + " 이어야 합니다");
        }
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    public int sizeX() { return sizeX; }

    public int sizeY() { return sizeY; }

    public int sizeZ() { return sizeZ; }

    public McvCamera camera() { return camera; }

    /**
     * 이 월드가 마지막으로 그려진 사각형(GUI 좌표)과 배율(월드 한 칸 = scale 픽셀). 위젯이 그릴 때마다 렌더 스레드가 갱신하고,
     * 화면 좌표 ↔ 월드 좌표 변환(project/raycast)이 읽는다. 아직 그려진 적이 없으면 null.
     */
    public record Viewport(double x, double y, double w, double h, double scale) { }

    public Viewport viewport() { return viewport; }

    public void setViewport(Viewport v) { this.viewport = v; }

    public float tickRate() { return tickRate; }

    public long tickCount() { return tickCount; }

    private long lastNanos = -1;
    private double tickFraction;

    /**
     * 시계를 {@code nowNanos} 까지 돌려 지난 만큼 틱을 진행한다(렌더 스레드가 위젯을 그릴 때 부른다). 몇 틱 진행했는지 돌려준다.
     * 틱 속도 0 이면 멈춘다. 한 번에 {@code maxTicks} 를 넘겨 따라잡지 않고, 1초 넘게 안 불렸다면(위젯이 안 보였다면) 그 시간은 건너뛴다.
     */
    public synchronized int advance(long nowNanos, int maxTicks) {
        float rate = tickRate;
        if (rate <= 0f || lastNanos < 0) {
            lastNanos = nowNanos;
            if (rate <= 0f) tickFraction = 0;
            return 0;
        }
        double dt = (nowNanos - lastNanos) / 1e9;
        lastNanos = nowNanos;
        if (dt < 0 || dt > 1.0) dt = 0;
        tickFraction += dt * rate;
        int n = (int) tickFraction;
        tickFraction -= n;
        if (n > maxTicks) n = Math.max(0, maxTicks);
        tickCount += n;
        return n;
    }

    /** 지금 틱이 얼마나 진행됐는지(0 이상 1 미만). 애니메이션 보간에 쓴다. */
    public synchronized float partialTick() { return (float) tickFraction; }

    /** 렌더 스레드가 전체 재동기화를 하려고 읽는다(스냅샷, 순서 없음). */
    public Map<Long, McvBlock> blockSnapshot() { return Map.copyOf(blocks); }

    public Collection<McvEntity> entities() { return List.copyOf(entities.values()); }

    /** {minX,minY,minZ,maxX,maxY,maxZ}(둘 다 포함) 또는 null(전체). */
    public int[] viewArea() { return viewArea == null ? null : viewArea.clone(); }

    public boolean inView(double x, double y, double z) {
        int[] a = viewArea;
        return a == null || x >= a[0] && x < a[3] + 1 && y >= a[1] && y < a[4] + 1 && z >= a[2] && z < a[5] + 1;
    }

    public static long key(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }

    public static int[] unkey(long k) {
        return new int[] {(int) (k >>> 40) & 0xFFFFF, (int) (k >>> 20) & 0xFFFFF, (int) k & 0xFFFFF};
    }

    private boolean inBounds(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    public int blockCount() { return blocks.size(); }

    // ---- 블록 ----

    public McvBlock getBlock(int x, int y, int z) {
        McvBlock b = inBounds(x, y, z) ? blocks.get(key(x, y, z)) : null;
        return b == null ? McvBlock.AIR : b;
    }

    /** 범위 밖이면 거부(IllegalArgumentException), 공기는 블록을 지운다. */
    public void setBlock(int x, int y, int z, McvBlock block) {
        if (!inBounds(x, y, z)) {
            throw new IllegalArgumentException("좌표가 월드 밖입니다: " + x + "," + y + "," + z
                    + " (크기 " + sizeX + "x" + sizeY + "x" + sizeZ + ")");
        }
        long k = key(x, y, z);
        if (block.isAir()) {
            if (blocks.remove(k) == null) return;
        } else {
            if (!blocks.containsKey(k) && blocks.size() >= MAX_BLOCKS) throw new IllegalArgumentException("블록이 너무 많습니다(" + MAX_BLOCKS + ")");
            McvBlock old = blocks.put(k, block);
            if (block.equals(old)) return;
        }
        log(new Change.BlockSet(x, y, z, block.isAir() ? McvBlock.AIR : block));
    }

    /** 두 모서리(포함)를 같은 블록으로 채운다. 칠한 칸 수를 돌려준다. */
    public int fill(int x1, int y1, int z1, int x2, int y2, int z2, McvBlock block) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        if (!inBounds(minX, minY, minZ) || !inBounds(maxX, maxY, maxZ)) throw new IllegalArgumentException("채울 영역이 월드 밖입니다");
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (!block.isAir() && volume + blocks.size() > MAX_BLOCKS) throw new IllegalArgumentException("블록이 너무 많습니다(" + MAX_BLOCKS + ")");
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) setBlock(x, y, z, block);
            }
        }
        return (int) volume;
    }

    // ---- 엔티티 ----

    public McvEntity spawnEntity(double x, double y, double z, String type) { return spawnEntity(x, y, z, type, null); }

    /**
     * 엔티티를 소환한다. {@code nbt}(SNBT, 예: {@code {Age:-24000,CustomName:"Moo"}})를 주면 소환과 함께 병합 요청으로 걸린다
     * ({@link #mergeEntityNbt} 와 같은 규칙). NBT 가 올바르지 않으면 엔티티를 만들지 않고 거부한다.
     */
    public McvEntity spawnEntity(double x, double y, double z, String type, String nbt) {
        if (entities.size() >= MAX_ENTITIES) throw new IllegalArgumentException("엔티티가 너무 많습니다(" + MAX_ENTITIES + ")");
        String t = McvEntity.normalizeType(type);
        String checked = nbt == null || nbt.isBlank() ? null : checkNbt(nbt);
        McvEntity e = new McvEntity(nextEntity.getAndIncrement(), t, x, y, z);
        if (checked != null) e.queueNbt(checked);
        entities.put(e.id(), e);
        log(new Change.EntitySpawn(e.id(), t, x, y, z));
        return e;
    }

    public McvEntity entity(int id) {
        McvEntity e = entities.get(id);
        if (e == null) throw new IllegalArgumentException("없는 엔티티입니다: " + id);
        return e;
    }

    public void moveEntity(int id, double x, double y, double z) {
        entity(id).setPosition(x, y, z);
        log(new Change.EntityMove(id, x, y, z));
    }

    public void rotateEntity(int id, float yaw, float pitch) {
        entity(id).setRotation(yaw, pitch);
        log(new Change.EntityRotate(id, yaw, pitch));
    }

    public void setEntityField(int id, String name, Object value) {
        McvEntity e = entity(id);
        if (java.util.Set.of("x", "y", "z", "yaw", "pitch", "type").contains(name)) {
            throw new IllegalArgumentException("'" + name + "' 은(는) 필드로 바꿀 수 없습니다(setPosition/setRotation 사용)");
        }
        e.setField(name, value);
        log(new Change.EntityField(id, name, value));
    }

    /**
     * 엔티티의 NBT 에 {@code snbt}(예: {@code {Invisible:1b,CustomName:"Hi"}})를 <b>병합</b>해 달라고 요청한다(/data merge 와 같다).
     * 실제 적용은 렌더 스레드가 진짜 엔티티에 한다(월드에 접속하지 않았다면 적용되지 않는다). 위치/회전은 setPosition/setRotation 이 매 프레임 이긴다.
     */
    public void mergeEntityNbt(int id, String snbt) {
        entity(id).queueNbt(checkNbt(snbt));
    }

    private static String checkNbt(String snbt) {
        if (snbt == null) throw new IllegalArgumentException("NBT 글자가 필요합니다");
        String t = snbt.trim();
        if (t.length() > McvEntity.MAX_NBT) throw new IllegalArgumentException("NBT 가 너무 깁니다(" + McvEntity.MAX_NBT + "자)");
        if (!t.startsWith("{") || !t.endsWith("}")) throw new IllegalArgumentException("NBT 는 {...} 형식이어야 합니다");
        return t;
    }

    public Object getEntityField(int id, String name) {
        return entity(id).getField(name);
    }

    public void removeEntity(int id) {
        entity(id);
        entities.remove(id);
        log(new Change.EntityRemove(id));
    }

    // ---- 보기 영역 / 틱 ----

    public void setViewArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int[] a = {Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ),
                Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ)};
        this.viewArea = a;
        log(new Change.ViewArea(a[0], a[1], a[2], a[3], a[4], a[5]));
    }

    public void clearViewArea() { this.viewArea = null; }

    /** 초당 틱 수. 0 이면 멈춤. */
    public void setTickRate(float ticksPerSecond) {
        if (!(ticksPerSecond >= 0f) || ticksPerSecond > MAX_TICK_RATE) {
            throw new IllegalArgumentException("틱 속도는 0~" + (int) MAX_TICK_RATE + " 이어야 합니다");
        }
        this.tickRate = ticksPerSecond;
        log(new Change.TickRate(ticksPerSecond));
    }

    // ---- 저널 ----

    private void log(Change c) {
        if (journalSize.get() >= MAX_JOURNAL) {
            journal.clear();
            journalSize.set(0);
            resync = true;
            return;
        }
        journal.add(c);
        journalSize.incrementAndGet();
    }

    /** 렌더 스레드가 쌓인 변경을 가져간다. {@code resync} 가 참이면 변경 목록 대신 스냅샷으로 전부 다시 만들어야 한다. */
    public Drained drain() {
        boolean r = resync;
        resync = false;
        List<Change> out = new ArrayList<>();
        Change c;
        while ((c = journal.poll()) != null) {
            out.add(c);
            journalSize.decrementAndGet();
        }
        return new Drained(out, r);
    }
}
