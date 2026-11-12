package dev.magyul.betteruiux.core.mcv;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 화면의 {@code <mc-viewer>} 하나가 가진 상태: <b>자기 카메라</b>, 마지막으로 그려진 사각형(뷰포트), 지금 보여 주는 월드.
 * 같은 월드를 뷰어 여러 개가 다른 각도로 동시에 볼 수 있도록 카메라와 뷰포트는 월드가 아니라 뷰어에 붙는다.
 * 뷰어는 요소 id 로 구분한다(스크립트가 {@code world.attach('#id')} 로 연결하는 대상). 스크립트 스레드와 렌더 스레드가 함께 만진다.
 */
public final class McvViewer {
    /** 마지막으로 그려진 사각형(GUI 좌표)과 배율(월드 한 칸 = scale 픽셀). */
    public record Viewport(double x, double y, double w, double h, double scale) { }

    private final String id;
    private final McvCamera camera = new McvCamera();
    private volatile Viewport viewport;
    private volatile McvWorld shown;
    /** 카메라를 이어받은 적이 있는 월드들(같은 월드로 다시 붙을 때 카메라를 또 덮어쓰지 않는다). */
    private final Set<McvWorld> adopted = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public McvViewer(String id) { this.id = id; }

    public String id() { return id; }

    public McvCamera camera() { return camera; }

    /** 아직 한 번도 그려지지 않았으면 null. 화면 좌표 ↔ 월드 좌표 변환(project/raycast)이 읽는다. */
    public Viewport viewport() { return viewport; }

    public void setViewport(Viewport v) { this.viewport = v; }

    /** 지금 이 뷰어가 보여 주는 월드(아직 없으면 null). */
    public McvWorld shown() { return shown; }

    /**
     * 위젯이 그릴 때마다 지금 보여 줄 월드를 알린다. 월드가 처음 보이게 되면 그 월드에 스크립트가 미리 설정해 둔
     * 카메라(월드의 기본 카메라, 뷰어가 붙기 전의 {@code world.getCamera()} 설정)를 이어받는다. 같은 월드로 다시 붙을 때는 덮어쓰지 않는다.
     */
    public void show(McvWorld w) {
        if (w == shown) return;
        shown = w;
        if (w != null && adopted.add(w) && w.camera().touched()) camera.copyFrom(w.camera());
    }
}
