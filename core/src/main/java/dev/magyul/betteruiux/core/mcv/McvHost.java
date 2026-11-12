package dev.magyul.betteruiux.core.mcv;

import dev.magyul.betteruiux.core.script.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 스크립트의 {@code mcv} 호출을 받는 호스트. 스크립트 런타임은 JSON 글자 하나를 보내고 JSON 글자 하나를 받는다:
 * 요청 {@code {"cmd":"setBlock","world":1,"x":0,...}}, 응답 {@code {"ok":true,"value":...}} 또는 {@code {"ok":false,"error":"..."}}.
 * 월드를 만지는 모든 일은 여기서 검증되고, 한 번의 스크립트 호출에서 부를 수 있는 횟수에도 상한이 있다({@link #beginCall}).
 */
public final class McvHost {
    public static final int MAX_WORLDS = 8, DEFAULT_MAX_CALLS = 200_000, MAX_BATCH = 100_000;

    private final Map<Integer, McvWorld> worlds = new ConcurrentHashMap<>();
    private final AtomicInteger nextWorld = new AtomicInteger(1);
    private int maxCalls = DEFAULT_MAX_CALLS;
    private int calls;

    /** 구조물 파일을 이름으로 찾아 바이트를 돌려주는 쪽(백엔드가 파일/리소스에서 읽는다). 없으면 null. */
    @FunctionalInterface
    public interface StructureLoader {
        byte[] load(String name) throws java.io.IOException;
    }

    private volatile StructureLoader structureLoader;

    /** 구조물 파일 조회를 연결한다. 연결하지 않으면 {@code loadStructure} 는 오류가 된다. */
    public void setStructureLoader(StructureLoader loader) { this.structureLoader = loader; }

    public McvHost() { }

    public McvHost(int maxCallsPerScriptCall) { this.maxCalls = maxCallsPerScriptCall; }

    /** 스크립트 호출 하나가 시작될 때 부른다(호출 횟수 예산을 되돌린다). */
    public void beginCall() { calls = 0; }

    public McvWorld world(int handle) {
        McvWorld w = worlds.get(handle);
        if (w == null) throw new IllegalArgumentException("없는 월드입니다: " + handle);
        return w;
    }

    public java.util.Collection<McvWorld> worlds() { return List.copyOf(worlds.values()); }

    // ---- 뷰어(<mc-viewer>) 연결 ----

    private static final int MAX_VIEWERS = 32;
    private static final java.util.regex.Pattern VIEWER_ID = java.util.regex.Pattern.compile("[A-Za-z0-9_.:-]{1,64}");
    private final Object viewerLock = new Object();
    /** 뷰어 id → 뷰어(만든 순서). 스크립트가 먼저 부르든 위젯이 먼저 그리든 같은 객체를 쓴다. */
    private final Map<String, McvViewer> viewers = new LinkedHashMap<>();
    /** 뷰어 id → 월드 핸들(world.attach 로 연결한 것, 연결한 순서). */
    private final Map<String, Integer> bindings = new LinkedHashMap<>();

    /** 선택자({@code '#view'} 또는 {@code 'view'})에서 뷰어 id 를 꺼낸다. 올바르지 않으면 {@link IllegalArgumentException}. */
    public static String viewerId(String selector) {
        if (selector == null) throw new IllegalArgumentException("뷰어 id 가 필요합니다");
        String id = selector.trim();
        if (id.startsWith("#")) id = id.substring(1);
        if (!VIEWER_ID.matcher(id).matches()) throw new IllegalArgumentException("뷰어 id 가 올바르지 않습니다(영문/숫자/_ . : - 64자 이하): " + selector);
        return id;
    }

    /** id 의 뷰어(없으면 만든다). */
    public McvViewer viewer(String id) {
        synchronized (viewerLock) {
            McvViewer v = viewers.get(id);
            if (v == null) {
                if (viewers.size() >= MAX_VIEWERS) throw new IllegalArgumentException("뷰어가 너무 많습니다(" + MAX_VIEWERS + ")");
                v = new McvViewer(id);
                viewers.put(id, v);
            }
            return v;
        }
    }

    /** {@code world.attach('#id')}: id 가 같은 {@code <mc-viewer>} 가 이 월드를 보여 주게 한다(이미 다른 월드에 붙어 있었다면 옮긴다). */
    public void attach(McvWorld w, String selector) {
        String id = viewerId(selector);
        int handle = handleOf(w);
        if (handle < 0) throw new IllegalArgumentException("이 호스트의 월드가 아닙니다");
        synchronized (viewerLock) {
            viewer(id);
            bindings.remove(id);                                       // 순서를 "마지막으로 붙인 것" 으로
            bindings.put(id, handle);
        }
    }

    public void detach(String selector) {
        synchronized (viewerLock) {
            bindings.remove(viewerId(selector));
        }
    }

    /**
     * 위젯(뷰어 id)이 보여 줄 월드. 연결(attach)이 있으면 그 월드(월드가 없어졌으면 null), 문서에 연결이 하나도 없으면 가장 먼저 만든 월드
     * (간단한 문서는 attach 없이 동작), 다른 뷰어만 연결되어 있으면 null.
     */
    public McvWorld resolveWorld(String viewerId) {
        synchronized (viewerLock) {
            Integer h = bindings.get(viewerId);
            if (h != null) return worlds.get(h);
            return bindings.isEmpty() ? primaryWorld() : null;
        }
    }

    /** 이 문서에 attach 로 연결된 것이 하나라도 있는지. */
    public boolean hasBindings() {
        synchronized (viewerLock) {
            return !bindings.isEmpty();
        }
    }

    /** 월드의 "대표 뷰어": 가장 먼저 연결된 뷰어, 없으면 지금 이 월드를 보여 주는 첫 뷰어. 없으면 null. */
    public McvViewer primaryViewer(McvWorld w) {
        synchronized (viewerLock) {
            for (Map.Entry<String, Integer> e : bindings.entrySet()) {
                if (worlds.get(e.getValue()) == w) return viewers.get(e.getKey());
            }
            for (McvViewer v : viewers.values()) if (v.shown() == w) return v;
            return null;
        }
    }

    /** 선택자를 주면 그 뷰어, 안 주면 월드의 대표 뷰어. */
    private McvViewer viewerFor(McvWorld w, String selector) {
        return selector != null ? viewer(viewerId(selector)) : primaryViewer(w);
    }

    /** 월드의 대표 뷰어 카메라, 뷰어가 아직 없으면 월드의 기본 카메라(나중에 붙는 뷰어가 이어받는다). */
    private McvCamera cameraFor(McvWorld w, String selector) {
        McvViewer v = viewerFor(w, selector);
        return v != null ? v.camera() : w.camera();
    }

    /** 월드의 핸들(스크립트가 쓰는 번호). 이 호스트의 월드가 아니면 -1. */
    public int handleOf(McvWorld w) {
        for (Map.Entry<Integer, McvWorld> e : worlds.entrySet()) if (e.getValue() == w) return e.getKey();
        return -1;
    }

    /** 가장 먼저 만든 월드(핸들이 가장 작은 것). 없으면 null. */
    public McvWorld primaryWorld() {
        return worlds.entrySet().stream().min(Map.Entry.comparingByKey()).map(Map.Entry::getValue).orElse(null);
    }

    /** 월드를 버린다(문서가 닫힐 때). */
    public void clear() {
        worlds.clear();
        synchronized (viewerLock) {
            bindings.clear();
            viewers.clear();
        }
    }

    public String invoke(String requestJson) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            if ("{\"cmd\":\"beginCall\"}".equals(requestJson)) {          // 런타임이 보내는 신호(스크립트는 이 통로에 닿지 못한다)
                beginCall();
                return "{\"ok\":true}";
            }
            if (++calls > maxCalls) throw new IllegalArgumentException("한 번의 실행에서 mcv 호출을 너무 많이 했습니다(" + maxCalls + ")");
            Object parsed = Json.parse(requestJson);
            if (!(parsed instanceof Map<?, ?> raw)) throw new IllegalArgumentException("요청은 객체여야 합니다");
            @SuppressWarnings("unchecked")
            Map<String, Object> req = (Map<String, Object>) raw;
            out.put("value", dispatch(req));
            out.put("ok", true);
        } catch (IllegalArgumentException e) {
            out.clear();
            out.put("ok", false);
            out.put("error", e.getMessage());
        }
        return Json.write(out);
    }

    private Object dispatch(Map<String, Object> r) {
        String cmd = str(r, "cmd");
        if (cmd.equals("createWorld")) {
            if (worlds.size() >= MAX_WORLDS) throw new IllegalArgumentException("월드를 너무 많이 만들었습니다(" + MAX_WORLDS + ")");
            McvWorld w = new McvWorld(integer(r, "x"), integer(r, "y"), integer(r, "z"));
            int id = nextWorld.getAndIncrement();
            worlds.put(id, w);
            return id;
        }
        McvWorld w = world(integer(r, "world"));
        switch (cmd) {
            case "size" -> {
                return List.of(w.sizeX(), w.sizeY(), w.sizeZ());
            }
            case "setBlock" -> {
                w.setBlock(integer(r, "x"), integer(r, "y"), integer(r, "z"), block(r));
                return null;
            }
            case "getBlock" -> {
                McvBlock b = w.getBlock(integer(r, "x"), integer(r, "y"), integer(r, "z"));
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", b.id());
                m.put("block", b.stateString());
                m.put("nbt", b.nbt());
                return m;
            }
            case "fill" -> {
                return w.fill(integer(r, "x1"), integer(r, "y1"), integer(r, "z1"),
                        integer(r, "x2"), integer(r, "y2"), integer(r, "z2"), block(r));
            }
            case "setBlocks" -> {
                if (!(r.get("blocks") instanceof List<?> list)) throw new IllegalArgumentException("blocks 는 배열이어야 합니다");
                if (list.size() > MAX_BATCH) throw new IllegalArgumentException("한 번에 보낼 수 있는 블록 수를 넘었습니다(" + MAX_BATCH + ")");
                for (Object o : list) {
                    if (!(o instanceof List<?> t) || t.size() < 4) throw new IllegalArgumentException("블록 항목은 [x,y,z,block,nbt?] 입니다");
                    w.setBlock(toInt(t.get(0)), toInt(t.get(1)), toInt(t.get(2)),
                            McvBlock.parse(t.get(3) instanceof String s ? s : null, t.size() > 4 && t.get(4) instanceof String n ? n : null));
                }
                return list.size();
            }
            case "loadStructure" -> {
                return loadStructure(w, str(r, "name"), r.get("x") == null ? 0 : integer(r, "x"),
                        r.get("y") == null ? 0 : integer(r, "y"), r.get("z") == null ? 0 : integer(r, "z"));
            }
            case "setMuted" -> {
                w.setMuted(!Boolean.FALSE.equals(r.get("muted")));
                return null;
            }
            case "setBiome" -> {
                w.setBiome(r.get("biome") instanceof String b ? b : null);
                return null;
            }
            case "fillBiome" -> {
                w.fillBiome(integer(r, "x1"), integer(r, "y1"), integer(r, "z1"), integer(r, "x2"), integer(r, "y2"), integer(r, "z2"), str(r, "biome"));
                return null;
            }
            case "getBiome" -> {
                return w.biomeAt(integer(r, "x"), integer(r, "y"), integer(r, "z"));
            }
            case "blockEvent" -> {
                w.blockEvent(integer(r, "x"), integer(r, "y"), integer(r, "z"), integer(r, "id"), integer(r, "param"));
                return null;
            }
            case "spawnEntity" -> {
                return w.spawnEntity(num(r, "x"), num(r, "y"), num(r, "z"), str(r, "type"), r.get("nbt") instanceof String n ? n : null).id();
            }
            case "entityGet" -> {
                return w.getEntityField(integer(r, "entity"), str(r, "name"));
            }
            case "entitySet" -> {
                w.setEntityField(integer(r, "entity"), str(r, "name"), r.get("value"));
                return null;
            }
            case "entityCall" -> {
                return entityCall(w, integer(r, "entity"), str(r, "method"), r.get("args"));
            }
            case "setViewArea" -> {
                w.setViewArea(integer(r, "x1"), integer(r, "y1"), integer(r, "z1"), integer(r, "x2"), integer(r, "y2"), integer(r, "z2"));
                return null;
            }
            case "clearViewArea" -> {
                w.clearViewArea();
                return null;
            }
            case "setTickRate" -> {
                w.setTickRate((float) num(r, "rate"));
                return null;
            }
            case "attach" -> {
                attach(w, str(r, "viewer"));
                return null;
            }
            case "detach" -> {
                detach(str(r, "viewer"));
                return null;
            }
            case "project" -> {
                McvViewer v = viewerFor(w, r.get("viewer") instanceof String s ? s : null);
                double[] q = v == null ? null : McvView.project(w, v, num(r, "x"), num(r, "y"), num(r, "z"));
                if (q == null) return null;                            // 뷰어가 없거나 아직 한 번도 그려지지 않았다
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("x", q[0]);
                m.put("y", q[1]);
                m.put("depth", q[2]);
                return m;
            }
            case "raycast" -> {
                McvViewer v = viewerFor(w, r.get("viewer") instanceof String s ? s : null);
                return v == null ? null : hitToMap(McvView.raycast(w, v, num(r, "x"), num(r, "y")));
            }
            case "camera" -> {
                return camera(cameraFor(w, r.get("viewer") instanceof String s ? s : null), str(r, "op"), r.get("args"));
            }
            default -> throw new IllegalArgumentException("알 수 없는 명령: " + cmd);
        }
    }

    private Object loadStructure(McvWorld w, String name, int x, int y, int z) {
        StructureLoader loader = structureLoader;
        if (loader == null) throw new IllegalArgumentException("구조물 파일을 읽을 수 없는 환경입니다");
        if (name.isBlank() || name.length() > 128 || name.contains("..") || name.startsWith("/") || name.startsWith("\\")
                || !name.matches("[A-Za-z0-9_./-]+")) {
            throw new IllegalArgumentException("구조물 이름이 올바르지 않습니다(영문/숫자/_ . / - 만, ..·절대경로 불가): " + name);
        }
        byte[] bytes;
        try {
            bytes = loader.load(name);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("구조물 파일을 읽지 못했습니다: " + name);
        }
        if (bytes == null) throw new IllegalArgumentException("구조물 파일을 찾을 수 없습니다: " + name);
        McvStructure.Result res = McvStructure.place(w, McvStructure.parse(bytes), x, y, z);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("size", List.of(res.sizeX(), res.sizeY(), res.sizeZ()));
        m.put("blocks", res.blocks());
        m.put("droppedNbt", res.droppedNbt());
        return m;
    }

    /** 맞힌 블록을 스크립트에 줄 모양으로. 맞힌 게 없으면 null. */
    public static Map<String, Object> hitToMap(McvView.Hit h) {
        if (h == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        if (h.isEntity()) {
            m.put("kind", "entity");
            m.put("entity", h.entity().id());
            m.put("type", h.entity().type());
            m.put("x", h.x());
            m.put("y", h.y());
            m.put("z", h.z());
            m.put("face", h.face());
            return m;
        }
        m.put("kind", "block");
        m.put("x", h.x());
        m.put("y", h.y());
        m.put("z", h.z());
        m.put("face", h.face());
        m.put("block", h.block().stateString());
        m.put("id", h.block().id());
        return m;
    }

    /** 엔티티 메서드: 이 호스트가 직접 처리하는 것은 데이터 모델 메서드뿐이다(백엔드가 렌더 쪽 동작을 저널로 받아 처리). */
    private static Object entityCall(McvWorld w, int id, String method, Object argsObj) {
        List<?> a = argsObj instanceof List<?> l ? l : List.of();
        switch (method) {
            case "setPosition" -> {
                need(a, 3);
                w.moveEntity(id, toDouble(a.get(0)), toDouble(a.get(1)), toDouble(a.get(2)));
            }
            case "setRotation" -> {
                need(a, 2);
                w.rotateEntity(id, (float) toDouble(a.get(0)), (float) toDouble(a.get(1)));
            }
            case "moveTo" -> {
                need(a, 4);
                w.moveEntityTo(id, toDouble(a.get(0)), toDouble(a.get(1)), toDouble(a.get(2)), (int) toDouble(a.get(3)));
            }
            case "walkTo" -> {
                need(a, 4);
                w.walkEntityTo(id, toDouble(a.get(0)), toDouble(a.get(1)), toDouble(a.get(2)), toDouble(a.get(3)));
            }
            case "setVelocity" -> {
                need(a, 3);
                w.setEntityVelocity(id, toDouble(a.get(0)), toDouble(a.get(1)), toDouble(a.get(2)));
            }
            case "stop" -> w.stopEntity(id);
            case "lookAt" -> {
                need(a, 3);
                w.entityLookAt(id, toDouble(a.get(0)), toDouble(a.get(1)), toDouble(a.get(2)));
            }
            case "remove" -> w.removeEntity(id);
            case "mergeNbt" -> {
                need(a, 1);
                if (!(a.get(0) instanceof String snbt)) throw new IllegalArgumentException("mergeNbt 는 SNBT 글자를 받습니다");
                w.mergeEntityNbt(id, snbt);
            }
            case "getNbt" -> {
                return w.entity(id).nbtSnapshot();
            }
            default -> throw new IllegalArgumentException("허용되지 않은 엔티티 메서드: " + method);
        }
        return null;
    }

    private static Object camera(McvCamera c, String op, Object argsObj) {
        List<?> a = argsObj instanceof List<?> l ? l : List.of();
        switch (op) {
            case "setYaw" -> {
                need(a, 1);
                c.setYaw((float) toDouble(a.get(0)));
            }
            case "setPitch" -> {
                need(a, 1);
                c.setPitch((float) toDouble(a.get(0)));
            }
            case "setPosition" -> {
                need(a, 3);
                c.setPosition(toDouble(a.get(0)), toDouble(a.get(1)), toDouble(a.get(2)));
            }
            case "setZoom" -> {
                need(a, 1);
                c.setZoom((float) toDouble(a.get(0)));
            }
            case "setFov" -> {
                need(a, 1);
                c.setFov((float) toDouble(a.get(0)));
            }
            case "setProjection" -> {
                need(a, 1);
                String p = String.valueOf(a.get(0)).toLowerCase(java.util.Locale.ROOT);
                if (p.equals("ortho") || p.equals("orthographic")) c.setProjection(McvCamera.Projection.ORTHO);
                else if (p.equals("perspective")) c.setProjection(McvCamera.Projection.PERSPECTIVE);
                else throw new IllegalArgumentException("투영은 ortho 또는 perspective 입니다");
            }
            case "get" -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("yaw", (double) c.yaw());
                m.put("pitch", (double) c.pitch());
                m.put("x", c.x());
                m.put("y", c.y());
                m.put("z", c.z());
                m.put("zoom", (double) c.zoom());
                m.put("fov", (double) c.fov());
                m.put("projection", c.projection() == McvCamera.Projection.ORTHO ? "ortho" : "perspective");
                return m;
            }
            default -> throw new IllegalArgumentException("알 수 없는 카메라 연산: " + op);
        }
        return null;
    }

    // ---- 인자 읽기 ----

    private static McvBlock block(Map<String, Object> r) {
        return McvBlock.parse(r.get("block") instanceof String s ? s : null, r.get("nbt") instanceof String n ? n : null);
    }

    private static void need(List<?> a, int n) {
        if (a.size() < n) throw new IllegalArgumentException("인자가 " + n + "개 필요합니다");
    }

    private static String str(Map<String, Object> r, String k) {
        if (r.get(k) instanceof String s) return s;
        throw new IllegalArgumentException("'" + k + "' 글자가 필요합니다");
    }

    private static double num(Map<String, Object> r, String k) {
        return toDouble(r.get(k));
    }

    private static int integer(Map<String, Object> r, String k) {
        return toInt(r.get(k));
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n && !Double.isNaN(n.doubleValue()) && !Double.isInfinite(n.doubleValue())) return n.doubleValue();
        throw new IllegalArgumentException("숫자가 필요합니다");
    }

    private static int toInt(Object o) {
        double d = toDouble(o);
        if (d != Math.rint(d) || Math.abs(d) > 1_000_000) throw new IllegalArgumentException("정수가 필요합니다(범위 ±1000000)");
        return (int) d;
    }
}
