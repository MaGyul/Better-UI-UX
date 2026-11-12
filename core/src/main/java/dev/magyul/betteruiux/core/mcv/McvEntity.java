package dev.magyul.betteruiux.core.mcv;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** 가상 월드에 소환된 엔티티의 데이터 모델(렌더 스레드의 실제 엔티티 객체와는 저널로 동기화된다). */
public final class McvEntity {
    public static final int MAX_FIELDS = 32, MAX_STRING = 256, MAX_NBT = 4096, MAX_PENDING_NBT = 16;
    private static final Pattern TYPE = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern FIELD = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,31}");

    private final int id;
    private final String type;
    private volatile double x, y, z;
    private volatile float yaw, pitch;
    private final Map<String, Object> fields = new ConcurrentHashMap<>();
    private volatile double bbWidth, bbHeight;                       // 0 = 아직 렌더러가 크기를 알려 주지 않았다(맞힐 수 없다)
    private final java.util.concurrent.ConcurrentLinkedQueue<String> pendingNbt = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private volatile String nbtSnapshot;

    McvEntity(int id, String type, double x, double y, double z) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    static String normalizeType(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 128) throw new IllegalArgumentException("엔티티 타입이 없거나 너무 깁니다");
        String t = raw.trim();
        if (!t.contains(":")) t = "minecraft:" + t;
        if (!TYPE.matcher(t).matches()) throw new IllegalArgumentException("엔티티 타입이 올바르지 않습니다: " + raw);
        return t;
    }

    public int id() { return id; }

    public String type() { return type; }

    public double x() { return x; }

    public double y() { return y; }

    public double z() { return z; }

    public float yaw() { return yaw; }

    public float pitch() { return pitch; }

    public Map<String, Object> fields() { return Map.copyOf(fields); }

    /** 충돌 상자 크기(가로=세로 폭, 높이). 백엔드가 진짜 엔티티를 만든 뒤 알려 준다. 알려지기 전에는 {@link McvView#raycast} 가 맞히지 않는다. */
    public void setBounds(double width, double height) {
        this.bbWidth = width;
        this.bbHeight = height;
    }

    public double bbWidth() { return bbWidth; }

    public double bbHeight() { return bbHeight; }

    /** 스크립트가 요청한 NBT 병합(SNBT 글자)을 하나 꺼낸다(백엔드가 렌더 스레드에서 적용). 없으면 null. */
    public String pollPendingNbt() { return pendingNbt.poll(); }

    void queueNbt(String snbt) {
        if (pendingNbt.size() >= MAX_PENDING_NBT) throw new IllegalArgumentException("NBT 요청이 너무 많이 밀렸습니다(" + MAX_PENDING_NBT + ")");
        pendingNbt.add(snbt);
    }

    /** 백엔드가 마지막으로 본 진짜 엔티티의 NBT(SNBT). 만들어지기 전에는 null. */
    public String nbtSnapshot() { return nbtSnapshot; }

    public void setNbtSnapshot(String snbt) { this.nbtSnapshot = snbt; }

    void setPosition(double x, double y, double z) {
        synchronized (this) {
            this.x = this.prevX = x;
            this.y = this.prevY = y;
            this.z = this.prevZ = z;
        }
    }

    void setRotation(float yaw, float pitch) {
        synchronized (this) {
            this.yaw = this.prevYaw = yaw;
            this.pitch = this.prevPitch = pitch;
            this.headYaw = this.prevHeadYaw = Float.NaN;             // 몸을 돌리면 머리도 따라간다
        }
    }

    // ---- 이동 모션(틱마다 월드가 진행한다) ----

    public static final int MAX_MOVE_TICKS = 1_000_000;
    public static final double MAX_VELOCITY = 8.0;                   // 틱당 블록 수
    private static final float TURN_PER_TICK = 40f;                  // 이동 방향으로 몸을 돌리는 최대 속도(도/틱)

    private double prevX, prevY, prevZ;
    private float prevYaw, prevPitch, prevHeadYaw = Float.NaN, headYaw = Float.NaN;   // 머리 방향이 NaN 이면 몸을 따른다
    private double targetX, targetY, targetZ;
    private int remaining;                                           // moveTo 로 남은 틱(0 이면 계획 없음)
    private double vx, vy, vz;                                       // setVelocity 의 틱당 이동량
    private volatile float stepSpeed;                                // 지난 틱에 가로로 움직인 거리(걷기 애니메이션 속도)

    /** {@code ticks} 틱 동안 일정한 속도로 목표까지 이동한다(이전 이동 계획·속도는 대체). 0 이하면 그 자리로 순간이동. */
    synchronized void moveTo(double tx, double ty, double tz, int ticks) {
        if (ticks > MAX_MOVE_TICKS) throw new IllegalArgumentException("이동 틱이 너무 큽니다(" + MAX_MOVE_TICKS + ")");
        vx = vy = vz = 0;
        if (ticks <= 0) {
            remaining = 0;
            setPosition(tx, ty, tz);
            return;
        }
        targetX = tx;
        targetY = ty;
        targetZ = tz;
        remaining = ticks;
    }

    /** 이동 계획을 취소하고 속도를 0 으로 한다(그 자리에 멈춘다). */
    synchronized void stopMoving() {
        remaining = 0;
        vx = vy = vz = 0;
    }

    /** 틱당 이동량(블록/틱). 이동 계획은 대체되고, 멈추려면 stop. 물리를 켜면 세로 성분은 무시된다(점프는 jump). */
    synchronized void setVelocity(double dx, double dy, double dz) {
        if (!(Math.abs(dx) <= MAX_VELOCITY && Math.abs(dy) <= MAX_VELOCITY && Math.abs(dz) <= MAX_VELOCITY)) {
            throw new IllegalArgumentException("속도는 틱당 -" + MAX_VELOCITY + " ~ " + MAX_VELOCITY + " 블록이어야 합니다");
        }
        remaining = 0;
        vx = dx;
        vy = dy;
        vz = dz;
    }

    /** 머리만 그 지점을 바라보게 한다(몸은 그대로). pitch 도 함께 맞춘다. 몸을 돌리면(setRotation) 머리는 다시 몸을 따른다. */
    synchronized void lookAt(double px, double py, double pz, double eyeHeight) {
        double dx = px - x, dy = py - (y + eyeHeight), dz = pz - z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1e-6 && Math.abs(dy) < 1e-6) return;
        headYaw = flat < 1e-6 ? yaw : (float) Math.toDegrees(Math.atan2(-dx, dz));
        pitch = (float) -Math.toDegrees(Math.atan2(dy, flat));
    }

    public synchronized boolean moving() { return remaining > 0 || vx != 0 || vy != 0 || vz != 0; }

    /** 지난 틱에 가로로 움직인 거리(블록/틱). */
    public float stepSpeed() { return stepSpeed; }

    private double fallSpeed;                                        // 물리 켠 엔티티의 세로 속도(블록/틱)
    private boolean onGround;

    public synchronized boolean onGround() { return onGround; }

    synchronized double fallSpeed() { return fallSpeed; }

    /** 물리를 켠 엔티티가 땅에 서 있으면 위로 뛴다. */
    synchronized void jump() {
        if (onGround) {
            fallSpeed = McvPhysics.JUMP;
            onGround = false;
        }
    }

    /** 물리 한 틱의 결과를 반영하고 중력을 더한다(바닐라: 이동 후 (속도 - 0.08) * 0.98). */
    synchronized void applyPhysics(double nx, double ny, double nz, boolean ground, boolean hitY) {
        x = nx;
        y = ny;
        z = nz;
        if (y < McvPhysics.FLOOR) {                                   // 월드 아래로 끝없이 떨어지지 않게 바닥을 둔다
            y = McvPhysics.FLOOR;
            ground = true;
            hitY = true;
        }
        onGround = ground;
        if (hitY) fallSpeed = 0;
        fallSpeed = (fallSpeed - McvPhysics.GRAVITY) * McvPhysics.DRAG;
    }

    /** 한 틱 진행한다(월드의 시계가 부른다). 이전 틱 값을 남겨 렌더가 그 사이를 보간한다. */
    synchronized void tick(McvWorld world) {
        prevX = x;
        prevY = y;
        prevZ = z;
        prevYaw = yaw;
        prevPitch = pitch;
        prevHeadYaw = headYaw;
        boolean physics = Boolean.TRUE.equals(fields.get("physics"));
        double dx = 0, dy = 0, dz = 0;
        if (remaining > 0) {
            dx = (targetX - x) / remaining;
            dy = physics ? 0 : (targetY - y) / remaining;               // 물리를 켜면 높이는 중력과 충돌이 정한다
            dz = (targetZ - z) / remaining;
            if (--remaining == 0 && !physics) {
                dx = targetX - x;
                dy = targetY - y;
                dz = targetZ - z;
            }
        } else if (vx != 0 || vy != 0 || vz != 0) {
            dx = vx;
            dy = physics ? 0 : vy;
            dz = vz;
        }
        if (physics) {
            McvPhysics.step(this, world, dx, dz);
        } else {
            x += dx;
            y += dy;
            z += dz;
            onGround = false;
            fallSpeed = 0;
        }
        double ax = x - prevX, az = z - prevZ;                          // 실제로 움직인 만큼(막히면 줄어든다)
        double flat = Math.sqrt(ax * ax + az * az);
        stepSpeed = (float) flat;
        if (flat > 1e-4 && !Boolean.FALSE.equals(fields.get("faceMovement"))) {
            float want = (float) Math.toDegrees(Math.atan2(-ax, az));
            float diff = ((want - yaw + 540f) % 360f) - 180f;
            yaw += Math.max(-TURN_PER_TICK, Math.min(TURN_PER_TICK, diff));
        }
    }

    // ---- 그릴 때 쓰는 보간 값(partial = 지금 틱이 얼마나 진행됐는지, 0~1) ----

    public synchronized double renderX(float partial) { return prevX + (x - prevX) * partial; }

    public synchronized double renderY(float partial) { return prevY + (y - prevY) * partial; }

    public synchronized double renderZ(float partial) { return prevZ + (z - prevZ) * partial; }

    public synchronized float renderYaw(float partial) { return lerpAngle(prevYaw, yaw, partial); }

    public synchronized float renderPitch(float partial) { return prevPitch + (pitch - prevPitch) * partial; }

    /** 머리 방향. lookAt 을 안 했으면 몸 방향과 같다. */
    public synchronized float renderHeadYaw(float partial) {
        float prev = Float.isNaN(prevHeadYaw) ? prevYaw : prevHeadYaw, cur = Float.isNaN(headYaw) ? yaw : headYaw;
        return lerpAngle(prev, cur, partial);
    }

    private static float lerpAngle(float a, float b, float t) {
        float d = ((b - a + 540f) % 360f) - 180f;
        return a + d * t;
    }

    /** 이름이 올바르고 값이 숫자/불리언/짧은 글자일 때만 저장한다. 위치·회전은 필드가 아니라 전용 메서드로 바꾼다. */
    void setField(String name, Object value) {
        if (name == null || !FIELD.matcher(name).matches()) throw new IllegalArgumentException("필드 이름이 올바르지 않습니다");
        if (value == null) {
            fields.remove(name);
            return;
        }
        if (!(value instanceof Number || value instanceof Boolean
                || value instanceof String s && s.length() <= MAX_STRING)) {
            throw new IllegalArgumentException("필드 값은 숫자/불리언/짧은 글자만 가능합니다");
        }
        if (!fields.containsKey(name) && fields.size() >= MAX_FIELDS) throw new IllegalArgumentException("필드가 너무 많습니다");
        fields.put(name, value);
    }

    Object getField(String name) {
        return switch (name) {
            case "x" -> x;
            case "y" -> y;
            case "z" -> z;
            case "yaw" -> (double) yaw;
            case "pitch" -> (double) pitch;
            case "type" -> type;
            case "moving" -> moving();
            case "onGround" -> onGround();
            default -> fields.get(name);
        };
    }
}
