package dev.magyul.betteruiux.mc26;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.magyul.betteruiux.core.mcv.McvEntity;
import dev.magyul.betteruiux.core.mcv.McvWorld;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 가상 월드의 엔티티({@link McvEntity}, 데이터 모델)를 실제 마인크래프트 엔티티로 만들어 그린다.
 *
 * <ul>
 *   <li>엔티티 객체는 렌더 스레드에서만 만지고, 매 프레임 데이터 모델과 맞춘다(위치·회전·허용된 필드). 저널을 쓰지 않아 위젯이 여러 개여도 안전하다.</li>
 *   <li>엔티티는 <b>진짜 클라이언트 월드에 넣지 않는다</b>(만들기만 하고 월드에 추가하지 않으므로 게임에 영향이 없다).
 *       다만 만들려면 {@link ClientLevel} 이 필요해서, 월드에 접속하지 않은 상태(타이틀 화면)에서는 엔티티를 그리지 않는다.</li>
 * </ul>
 */
final class McViewerEntities {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");

    private final Map<McvWorld, Map<Integer, Optional<Entity>>> live = new WeakHashMap<>();
    private final Set<String> warned = new HashSet<>();

    void render(McvWorld w, float yaw, float pitch, PoseStack pose, SubmitNodeCollector collector) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Map<Integer, Optional<Entity>> entities = live.computeIfAbsent(w, k -> new HashMap<>());
        if (level == null) {
            entities.clear();                                                // 월드를 떠났다: 옛 레벨에 묶인 엔티티는 버린다
            return;
        }
        Set<Integer> present = new HashSet<>();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        CameraRenderState camera = new CameraRenderState();
        // 이름표처럼 카메라를 바라보는 것들이 보는 방향을 알도록, 뷰 회전의 역을 준다.
        camera.orientation = new Quaternionf().rotateY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(pitch))
                .rotateZ((float) Math.toRadians(-180.0));
        for (McvEntity me : w.entities()) {
            present.add(me.id());
            if (!w.inView(me.x(), me.y(), me.z())) continue;
            Optional<Entity> slot = entities.get(me.id());
            if (slot == null) {
                slot = Optional.ofNullable(create(level, me));
                entities.put(me.id(), slot);
            }
            if (slot.isEmpty()) continue;
            Entity e = slot.get();
            sync(e, me, w);
            EntityRenderState state = dispatcher.extractEntity(e, w.partialTick());   // 틱 사이를 보간해 부드럽게(ageInTicks = tickCount + partial)
            state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
            pose.pushPose();
            pose.translate(me.x(), me.y(), me.z());
            dispatcher.submit(state, camera, 0, 0, 0, pose, collector);
            pose.popPose();
        }
        entities.keySet().removeIf(id -> !present.contains(id));            // 스크립트가 지운 엔티티
    }

    private Entity create(ClientLevel level, McvEntity me) {
        try {
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(me.type()));
            if (type.isEmpty()) {
                warnOnce("알 수 없는 엔티티 타입: " + me.type());
                return null;
            }
            Entity e = type.get().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (e == null) warnOnce("이 엔티티는 뷰어에서 만들 수 없습니다: " + me.type());
            else e.setId(0x40000000 + me.id());                              // 렌더러가 id 를 읽는다(진짜 월드에는 없으니 겹쳐도 상관없다)
            return e;
        } catch (RuntimeException ex) {
            warnOnce("엔티티를 만들지 못했습니다(" + me.type() + "): " + ex);
            return null;
        }
    }

    /** 데이터 모델 → 엔티티. 이전/현재 값을 같게 해서 보간 없이 지금 값 그대로 그려진다. */
    private static void sync(Entity e, McvEntity me, McvWorld w) {
        e.setPos(me.x(), me.y(), me.z());
        e.xo = e.xOld = me.x();
        e.yo = e.yOld = me.y();
        e.zo = e.zOld = me.z();
        e.setYRot(me.yaw());
        e.setXRot(me.pitch());
        e.yRotO = me.yaw();
        e.xRotO = me.pitch();
        if (e instanceof LivingEntity le) {
            le.setYBodyRot(me.yaw());
            le.setYHeadRot(me.yaw());
            le.yBodyRotO = me.yaw();
            le.yHeadRotO = me.yaw();
        }
        e.tickCount = (int) w.tickCount();
        Map<String, Object> f = me.fields();
        Object name = f.get("customName");
        e.setCustomName(name instanceof String s && !s.isEmpty() ? Component.literal(s) : null);
        e.setCustomNameVisible(Boolean.TRUE.equals(f.get("customNameVisible")) || name instanceof String s2 && !s2.isEmpty() && f.get("customNameVisible") == null);
        e.setInvisible(Boolean.TRUE.equals(f.get("invisible")));
    }

    private void warnOnce(String msg) {
        if (warned.add(msg)) LOG.warn("[better-uiux] <mc-viewer> {}", msg);
    }
}
