package dev.magyul.betteruiux.core.mcv;

/** 뷰어의 카메라. 기본값은 Ponder 같은 등각(ortho) 시점이다. 스크립트 스레드가 쓰고 렌더 스레드가 읽는다. */
public final class McvCamera {
    public enum Projection { ORTHO, PERSPECTIVE }

    private volatile float yaw = 225f, pitch = 30f;
    private volatile double x, y, z;
    private volatile Projection projection = Projection.ORTHO;
    /** ortho 일 때 화면 높이에 들어오는 블록 수의 역수 비슷한 배율(클수록 가깝다). */
    private volatile float zoom = 1f;
    private volatile float fov = 70f;
    private volatile boolean positioned;

    public float yaw() { return yaw; }

    public float pitch() { return pitch; }

    public double x() { return x; }

    public double y() { return y; }

    public double z() { return z; }

    public Projection projection() { return projection; }

    public float zoom() { return zoom; }

    public float fov() { return fov; }

    /** 위치가 스크립트로 지정되었는지. false 면 렌더러가 월드가 한가운데 오도록 자동 배치한다. */
    public boolean positioned() { return positioned; }

    public void setYaw(float v) { this.yaw = ((v % 360f) + 360f) % 360f; }

    public void setPitch(float v) { this.pitch = Math.max(-90f, Math.min(90f, v)); }

    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.positioned = true;
    }

    public void setProjection(Projection p) { this.projection = p; }

    public void setZoom(float v) { this.zoom = Math.max(0.05f, Math.min(64f, v)); }

    public void setFov(float v) { this.fov = Math.max(10f, Math.min(130f, v)); }
}
