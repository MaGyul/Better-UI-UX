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
    public void clear() { worlds.clear(); }

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
            case "spawnEntity" -> {
                return w.spawnEntity(num(r, "x"), num(r, "y"), num(r, "z"), str(r, "type")).id();
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
            case "project" -> {
                double[] q = McvView.project(w, num(r, "x"), num(r, "y"), num(r, "z"));
                if (q == null) return null;                            // 아직 한 번도 그려지지 않았다
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("x", q[0]);
                m.put("y", q[1]);
                m.put("depth", q[2]);
                return m;
            }
            case "raycast" -> {
                return hitToMap(McvView.raycast(w, num(r, "x"), num(r, "y")));
            }
            case "camera" -> {
                return camera(w.camera(), str(r, "op"), r.get("args"));
            }
            default -> throw new IllegalArgumentException("알 수 없는 명령: " + cmd);
        }
    }

    /** 맞힌 블록을 스크립트에 줄 모양으로. 맞힌 게 없으면 null. */
    public static Map<String, Object> hitToMap(McvView.Hit h) {
        if (h == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
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
            case "remove" -> w.removeEntity(id);
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
