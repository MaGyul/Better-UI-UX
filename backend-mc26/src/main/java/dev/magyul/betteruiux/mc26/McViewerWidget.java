package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.api.UiGfx;
import dev.magyul.betteruiux.core.doc.UiElement;
import dev.magyul.betteruiux.core.doc.UiWidget;
import dev.magyul.betteruiux.core.mcv.McvCamera;
import dev.magyul.betteruiux.core.mcv.McvHost;
import dev.magyul.betteruiux.core.mcv.McvView;
import dev.magyul.betteruiux.core.mcv.McvViewer;
import dev.magyul.betteruiux.core.mcv.McvWorld;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code <mc-viewer id="view"/>}: 스크립트가 만든 가상 마인크래프트 월드를 보여 준다.
 *
 * <p><b>연결</b>: 스크립트가 {@code world.attach('#view')}(또는 {@code mcv.createWorld(x, y, z, '#view')})로 요소 id 가 같은 뷰어에 월드를 붙인다.
 * 문서에 attach 가 하나도 없으면 가장 먼저 만든 월드를 보여 준다(간단한 문서용). 뷰어마다 <b>자기 카메라</b>를 가져서, 같은 월드를 뷰어 여러 개가
 * 다른 각도로 동시에 볼 수 있다. id 가 없는 뷰어도 쓸 수 있지만 스크립트가 가리킬 수 없다.
 *
 * <p>마우스 드래그로 카메라를 돌리고, 휠로 확대/축소한다. {@code light="3d|flat|entity"} 로 조명 방식을 고른다(기본 3d). 그리기는 {@link McViewerPip}.
 *
 * <p>스크립트로 가는 신호(전역 함수가 있을 때만 불린다):
 * <ul>
 *   <li>{@code onTick(tick, world)} — 월드 틱마다</li>
 *   <li>{@code onBlockHover(e)} — 마우스 아래의 블록이 바뀔 때. 블록이 없으면 {@code e.block == null}</li>
 *   <li>{@code onBlockClick(e)} — 드래그 없이 눌렀다 뗐을 때. {@code e = {world, viewer, kind, x, y, z, face, block, id, button}}</li>
 *   <li>{@code onEntityHover(e)}, {@code onEntityClick(e)} — 엔티티 위에서. {@code e = {world, viewer, kind, entity, type, x, y, z, face, button}}, 없으면 {@code e.entity == null}.
 *       엔티티가 블록보다 가까우면 엔티티 이벤트가 오고 블록 이벤트는 "블록 없음"이다.</li>
 * </ul>
 * 이벤트의 {@code viewer} 는 뷰어 요소의 id(id 가 없으면 null)다.
 */
final class McViewerWidget implements UiWidget {
    private static final int MAX_TICKS_PER_FRAME = 5, CLICK_SLOP = 4;
    private static final AtomicInteger ANONYMOUS = new AtomicInteger();

    private final UiElement el;
    private final String anonymousKey = "viewer-" + ANONYMOUS.incrementAndGet();
    private int lastX, lastY, pressX, pressY, pressButton;
    private boolean dragging, moved;
    private String hoverKey = "", hoverEntityKey = "";   // 직전에 알려 준 마우스 아래 블록/엔티티(같으면 다시 알리지 않는다)
    private String warned = "";                          // 마지막으로 문서에 알린 "연결 없음" 경고(같은 경고를 되풀이하지 않는다)

    McViewerWidget(UiElement el) { this.el = el; }

    @Override public int intrinsicWidth() { return 160; }

    @Override public int intrinsicHeight() { return 120; }

    /** 이 위젯의 뷰어 id: 요소 id(올바른 것만), 아니면 위젯마다 따로 붙인 이름. */
    private String key() {
        String id = el.id();
        if (id != null) {
            try {
                return McvHost.viewerId(id);
            } catch (IllegalArgumentException ignored) {
                // id 에 쓸 수 없는 글자가 있으면 id 없는 뷰어로 취급한다
            }
        }
        return anonymousKey;
    }

    private boolean anonymous() { return key().equals(anonymousKey); }

    private McvHost host() { return McViewerRegistry.get(el.document()); }

    /** 지금 이 뷰어가 보여 줄 월드(연결이 없으면 null). */
    private McvWorld world() {
        McvHost host = host();
        return host == null ? null : host.resolveWorld(key());
    }

    private McvViewer viewer() {
        McvHost host = host();
        if (host == null) return null;
        try {
            return host.viewer(key());
        } catch (IllegalArgumentException e) {
            return null;                                 // 뷰어가 너무 많다
        }
    }

    @Override
    public void paint(UiGfx gfx, int x, int y, int w, int h, Frame frame) {
        McvWorld world = world();
        McvViewer viewer = world == null ? null : viewer();
        if (world == null || viewer == null || w <= 0 || h <= 0) {
            gfx.fillRect(x, y, w, h, 0x55000000);                       // 아직 월드가 없다(스크립트가 만들기 전)
            warnIfUnbound();
            return;
        }
        warned = "";
        viewer.show(world);                                             // 월드가 처음 보일 때 월드의 기본 카메라를 이어받는다
        McvCamera cam = viewer.camera();
        int[] va = world.viewArea();
        double dx = va == null ? world.sizeX() : va[3] - va[0] + 1;
        double dy = va == null ? world.sizeY() : va[4] - va[1] + 1;
        double dz = va == null ? world.sizeZ() : va[5] - va[2] + 1;
        double diag = Math.sqrt(dx * dx + dy * dy + dz * dz);          // 월드가 어느 각도에서도 들어오도록 대각선에 맞춘다
        float scale = (float) (Math.min(w, h) / diag) * cam.zoom();
        viewer.setViewport(new McvViewer.Viewport(x, y, w, h, scale));  // project/raycast 가 이 사각형을 기준으로 계산한다
        advanceTicks(world);
        updateHover(world, viewer, frame);
        if (!(gfx instanceof McGfx mc)) return;
        McViewerRenderState.Perspective persp = null;
        if (cam.projection() == McvCamera.Projection.PERSPECTIVE) {
            double[] eye = McvView.eye(world, viewer);                  // 뷰포트(가로세로 비)를 정한 뒤에 계산한다
            persp = new McViewerRenderState.Perspective(cam.fov(), eye[0], eye[1], eye[2]);
        }
        mc.addPictureInPicture(new McViewerRenderState(world, cam.yaw(), cam.pitch(), el.attr("light"), persp, x, y, x + w, y + h, scale, null));
    }

    /** 문서에 attach 가 있는데 이 뷰어에는 연결된 월드가 없으면 한 번 알려 준다(id 를 잘못 썼을 때 빈 상자만 보이는 것을 막는다). */
    private void warnIfUnbound() {
        McvHost host = host();
        if (host == null || !host.hasBindings()) return;
        String msg = anonymous() ? "id 없는 <mc-viewer> 에는 월드를 연결할 수 없습니다(id 를 주고 world.attach('#id'))"
                : "<mc-viewer id=\"" + key() + "\"> 에 연결된 월드가 없습니다(스크립트에서 world.attach('#" + key() + "'))";
        if (msg.equals(warned)) return;
        warned = msg;
        el.document().warn(msg);
    }

    /**
     * 이 위젯이 그려질 때마다 월드 시계를 돌린다(안 보이는 월드는 멈춘다). 진행된 틱마다 스크립트의 onTick 을 부른다.
     * 한 월드를 위젯 여러 개가 그려도 {@link McvWorld#advance} 가 시간 기준이라 두 번 진행되지 않는다.
     */
    private void advanceTicks(McvWorld world) {
        int n = world.advance(System.nanoTime(), MAX_TICKS_PER_FRAME);
        if (n == 0) return;
        McViewerRegistry.ScriptEvents events = McViewerRegistry.events(el.document());
        McvHost host = host();
        if (events == null || host == null) return;
        int handle = host.handleOf(world);
        long last = world.tickCount();
        for (int i = n - 1; i >= 0; i--) events.tick(handle, last - i);
    }

    // ---- 블록/엔티티 호버·클릭 ----

    /** 스크립트에 줄 이벤트. 블록 이벤트에서 엔티티를 맞힌 경우는 "블록 없음", 엔티티 이벤트에서 블록을 맞힌 경우는 "엔티티 없음"이다. */
    private Map<String, Object> event(McvWorld world, McvView.Hit hit, int button, boolean entityEvent) {
        McvHost host = host();
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("world", host == null ? -1 : host.handleOf(world));
        e.put("viewer", anonymous() ? null : key());
        boolean match = hit != null && hit.isEntity() == entityEvent;
        Map<String, Object> m = match ? McvHost.hitToMap(hit) : null;
        if (m != null) e.putAll(m);
        else e.put(entityEvent ? "entity" : "block", null);
        e.put("button", button);
        return e;
    }

    private static String key(McvView.Hit hit, boolean entityEvent) {
        if (hit == null || hit.isEntity() != entityEvent) return "";
        return entityEvent ? "e" + hit.entity().id() : hit.x() + "," + hit.y() + "," + hit.z() + "," + hit.face();
    }

    private void updateHover(McvWorld world, McvViewer viewer, Frame frame) {
        McViewerRegistry.ScriptEvents events = McViewerRegistry.events(el.document());
        if (events == null) return;
        McvView.Hit hit = frame.hovered() && !dragging ? McvView.raycast(world, viewer, frame.mouseX(), frame.mouseY()) : null;
        String blockKey = key(hit, false), entityKey = key(hit, true);
        if (!Objects.equals(blockKey, hoverKey)) {
            hoverKey = blockKey;
            events.call("onBlockHover", event(world, hit, -1, false));
        }
        if (!Objects.equals(entityKey, hoverEntityKey)) {
            hoverEntityKey = entityKey;
            events.call("onEntityHover", event(world, hit, -1, true));
        }
    }

    // ---- 입력: 드래그로 돌리고 휠로 확대, 제자리에서 뗴면 클릭 ----

    @Override
    public boolean mousePressed(UiElement element, int x, int y, int button) {
        if (world() == null) return false;
        dragging = button == 0;
        moved = false;
        pressX = lastX = x;
        pressY = lastY = y;
        pressButton = button;
        return true;
    }

    @Override
    public void mouseDragged(UiElement element, int x, int y) {
        McvViewer v = viewer();
        if (v == null || world() == null) return;
        if (Math.abs(x - pressX) > CLICK_SLOP || Math.abs(y - pressY) > CLICK_SLOP) moved = true;
        if (!dragging || !moved) return;
        McvCamera c = v.camera();
        c.setYaw(c.yaw() + (x - lastX) * 0.6f);
        c.setPitch(c.pitch() + (y - lastY) * 0.6f);
        lastX = x;
        lastY = y;
    }

    @Override
    public void mouseReleased(UiElement element, int x, int y, int button) {
        dragging = false;
        McvWorld w = world();
        McvViewer v = viewer();
        McViewerRegistry.ScriptEvents events = McViewerRegistry.events(el.document());
        if (w == null || v == null || events == null || moved || button != pressButton) return;
        McvView.Hit hit = McvView.raycast(w, v, x, y);
        if (hit != null && hit.isEntity()) events.call("onEntityClick", event(w, hit, button, true));
        else events.call("onBlockClick", event(w, hit, button, false));
    }

    @Override
    public boolean mouseScrolled(UiElement element, int x, int y, double amount) {
        McvViewer v = viewer();
        if (v == null || world() == null) return false;
        McvCamera c = v.camera();
        c.setZoom((float) (c.zoom() * Math.pow(1.1, amount)));
        return true;
    }
}
