package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.api.UiGfx;
import dev.magyul.betteruiux.core.doc.UiElement;
import dev.magyul.betteruiux.core.doc.UiWidget;
import dev.magyul.betteruiux.core.mcv.McvCamera;
import dev.magyul.betteruiux.core.mcv.McvHost;
import dev.magyul.betteruiux.core.mcv.McvView;
import dev.magyul.betteruiux.core.mcv.McvWorld;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * {@code <mc-viewer world="1"/>}: 스크립트가 만든 가상 마인크래프트 월드를 보여 준다({@code world} 를 생략하면 가장 먼저 만든 월드).
 * 마우스 드래그로 카메라를 돌리고, 휠로 확대/축소한다. {@code light="3d|flat|entity"} 로 조명 방식을 고른다(기본 3d). 그리기는 {@link McViewerPip}(GUI Picture-in-Picture)가 맡는다.
 *
 * <p>스크립트로 가는 신호(전역 함수가 있을 때만 불린다):
 * <ul>
 *   <li>{@code onTick(tick, world)} — 월드 틱마다</li>
 *   <li>{@code onBlockHover(e)} — 마우스 아래의 블록이 바뀔 때. 블록이 없으면 {@code e.block == null}</li>
 *   <li>{@code onBlockClick(e)} — 드래그 없이 눌렀다 뗐을 때. {@code e = {world, kind, x, y, z, face, block, id, button}}</li>
 *   <li>{@code onEntityHover(e)}, {@code onEntityClick(e)} — 엔티티 위에서. {@code e = {world, kind, entity, type, x, y, z, face, button}}, 없으면 {@code e.entity == null}.
 *       엔티티가 블록보다 가까우면 엔티티 이벤트가 오고 블록 이벤트는 "블록 없음"이다.</li>
 * </ul>
 */
final class McViewerWidget implements UiWidget {
    private static final int MAX_TICKS_PER_FRAME = 5, CLICK_SLOP = 4;
    private final UiElement el;
    private int lastX, lastY, pressX, pressY, pressButton;
    private boolean dragging, moved;
    private String hoverKey = "", hoverEntityKey = "";   // 직전에 알려 준 마우스 아래 블록/엔티티(같으면 다시 알리지 않는다)

    McViewerWidget(UiElement el) { this.el = el; }

    @Override public int intrinsicWidth() { return 160; }

    @Override public int intrinsicHeight() { return 120; }

    private McvWorld world() {
        McvHost host = McViewerRegistry.get(el.document());
        if (host == null) return null;
        String attr = el.attr("world");
        if (attr != null) {
            try {
                return host.world(Integer.parseInt(attr.trim()));
            } catch (RuntimeException e) {
                return null;
            }
        }
        return host.primaryWorld();
    }

    @Override
    public void paint(UiGfx gfx, int x, int y, int w, int h, Frame frame) {
        McvWorld world = world();
        if (world == null || w <= 0 || h <= 0) {
            gfx.fillRect(x, y, w, h, 0x55000000);                       // 아직 월드가 없다(스크립트가 만들기 전)
            return;
        }
        McvCamera cam = world.camera();
        int[] va = world.viewArea();
        double dx = va == null ? world.sizeX() : va[3] - va[0] + 1;
        double dy = va == null ? world.sizeY() : va[4] - va[1] + 1;
        double dz = va == null ? world.sizeZ() : va[5] - va[2] + 1;
        double diag = Math.sqrt(dx * dx + dy * dy + dz * dz);          // 월드가 어느 각도에서도 들어오도록 대각선에 맞춘다
        float scale = (float) (Math.min(w, h) / diag) * cam.zoom();
        world.setViewport(new McvWorld.Viewport(x, y, w, h, scale));   // project/raycast 가 이 사각형을 기준으로 계산한다
        advanceTicks(world);
        updateHover(world, frame);
        if (!(gfx instanceof McGfx mc)) return;
        mc.addPictureInPicture(new McViewerRenderState(world, cam.yaw(), cam.pitch(), el.attr("light"), x, y, x + w, y + h, scale, null));
    }

    /**
     * 이 위젯이 그려질 때마다 월드 시계를 돌린다(안 보이는 월드는 멈춘다). 진행된 틱마다 스크립트의 onTick 을 부른다.
     * 한 월드를 위젯 여러 개가 그려도 {@link McvWorld#advance} 가 시간 기준이라 두 번 진행되지 않는다.
     */
    private void advanceTicks(McvWorld world) {
        int n = world.advance(System.nanoTime(), MAX_TICKS_PER_FRAME);
        if (n == 0) return;
        McViewerRegistry.ScriptEvents events = McViewerRegistry.events(el.document());
        McvHost host = McViewerRegistry.get(el.document());
        if (events == null || host == null) return;
        int handle = host.handleOf(world);
        long last = world.tickCount();
        for (int i = n - 1; i >= 0; i--) events.tick(handle, last - i);
    }

    // ---- 블록 호버/클릭 ----

    /** 스크립트에 줄 이벤트. 블록 이벤트에서 엔티티를 맞힌 경우는 "블록 없음", 엔티티 이벤트에서 블록을 맞힌 경우는 "엔티티 없음"이다. */
    private Map<String, Object> event(McvWorld world, McvView.Hit hit, int button, boolean entityEvent) {
        McvHost host = McViewerRegistry.get(el.document());
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("world", host == null ? -1 : host.handleOf(world));
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

    private void updateHover(McvWorld world, Frame frame) {
        McViewerRegistry.ScriptEvents events = McViewerRegistry.events(el.document());
        if (events == null) return;
        McvView.Hit hit = frame.hovered() && !dragging ? McvView.raycast(world, frame.mouseX(), frame.mouseY()) : null;
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
        McvWorld w = world();
        if (w == null) return;
        if (Math.abs(x - pressX) > CLICK_SLOP || Math.abs(y - pressY) > CLICK_SLOP) moved = true;
        if (!dragging || !moved) return;
        McvCamera c = w.camera();
        c.setYaw(c.yaw() + (x - lastX) * 0.6f);
        c.setPitch(c.pitch() + (y - lastY) * 0.6f);
        lastX = x;
        lastY = y;
    }

    @Override
    public void mouseReleased(UiElement element, int x, int y, int button) {
        dragging = false;
        McvWorld w = world();
        McViewerRegistry.ScriptEvents events = McViewerRegistry.events(el.document());
        if (w == null || events == null || moved || button != pressButton) return;
        McvView.Hit hit = McvView.raycast(w, x, y);
        if (hit != null && hit.isEntity()) events.call("onEntityClick", event(w, hit, button, true));
        else events.call("onBlockClick", event(w, hit, button, false));
    }

    @Override
    public boolean mouseScrolled(UiElement element, int x, int y, double amount) {
        McvWorld w = world();
        if (w == null) return false;
        McvCamera c = w.camera();
        c.setZoom((float) (c.zoom() * Math.pow(1.1, amount)));
        return true;
    }
}
