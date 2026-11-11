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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
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
        CameraRenderState camera = camera(yaw, pitch);
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
            applyNbt(e, me, level);
            me.setBounds(e.getBbWidth(), e.getBbHeight());               // 이제 화면의 엔티티를 맞힐 수 있다(core 의 raycast 가 이 크기를 쓴다)
            EntityRenderState state = dispatcher.extractEntity(e, w.partialTick());   // 틱 사이를 보간해 부드럽게(ageInTicks = tickCount + partial)
            state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
            showNameTag(e, state);
            pose.pushPose();
            pose.translate(me.x(), me.y(), me.z());
            dispatcher.submit(state, camera, 0, 0, 0, pose, collector);
            pose.popPose();
        }
        entities.keySet().removeIf(id -> !present.contains(id));            // 스크립트가 지운 엔티티
    }

    /**
     * 이름표. 바닐라는 "실제 플레이어 카메라와의 거리"로 표시 여부를 정하는데 뷰어의 엔티티는 월드 밖(가상 좌표)에 있어서 이 값이 의미가 없다
     * (멀리 있다고 판단해 숨긴다). 그래서 이름이 있고 보이게 설정된 엔티티는 렌더 상태의 이름표를 직접 채운다.
     */
    private static void showNameTag(Entity e, EntityRenderState state) {
        if (state.nameTag != null || !e.hasCustomName() || !e.isCustomNameVisible()) return;
        state.nameTag = e.getCustomName();
        Vec3 attach = e.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, e.getYRot());
        state.nameTagAttachment = attach != null ? attach : new Vec3(0, e.getBbHeight() + 0.5, 0);
        state.distanceToCameraSq = 0;
    }

    /**
     * 이름표처럼 카메라를 바라보는 것들이 쓰는 방향. 이름표는 "위치로 이동 → 이 방향으로 회전 → (0.025, -0.025, 0.025) 배율" 로 그려지는데,
     * 뷰어의 변환은 [화면 y 가 아래로 자라도록 Z 로 180° 뒤집고 → 카메라 회전] 이다(그 바깥에 z 를 뒤집는 배율이 있다).
     * 글자가 똑바로(좌우 반전 없이) 서려면 회전의 곱이 X 축 180° 가 되어야 하고, 그것은 뷰 회전의 역에 Y 축 180° 를 더한 것과 같다
     * (바닐라가 GUI 엔티티에서 쓰는 rotateY(π) 와 같은 보정).
     */
    static CameraRenderState camera(float yaw, float pitch) {
        CameraRenderState camera = new CameraRenderState();
        camera.orientation = new Quaternionf().rotateY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.PI);
        return camera;
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
        // 필드는 스크립트가 값을 준 것만 적용한다(안 준 것은 NBT 병합 등으로 바뀐 값을 그대로 둔다).
        Map<String, Object> f = me.fields();
        if (f.containsKey("customName")) {
            Object name = f.get("customName");
            boolean has = name instanceof String s && !s.isEmpty();
            e.setCustomName(has ? Component.literal((String) name) : null);
            if (!f.containsKey("customNameVisible")) e.setCustomNameVisible(has);
        }
        if (f.containsKey("customNameVisible")) e.setCustomNameVisible(Boolean.TRUE.equals(f.get("customNameVisible")));
        if (f.containsKey("invisible")) e.setInvisible(Boolean.TRUE.equals(f.get("invisible")));
    }

    /**
     * 스크립트가 요청한 NBT 병합을 진짜 엔티티에 적용한다(/data merge 처럼: 현재 NBT 를 저장해 요청 값을 덮어씌우고 다시 불러온다).
     * 처음 한 번과 병합할 때마다 지금 NBT 를 스냅샷으로 남겨 스크립트의 getNbt() 가 읽는다. 위치/회전은 매 프레임 데이터 모델이 다시 이긴다.
     */
    private void applyNbt(Entity e, McvEntity me, ClientLevel level) {
        String pending = me.pollPendingNbt();
        if (pending == null && me.nbtSnapshot() != null) return;
        try {
            CompoundTag current = save(e, level);
            while (pending != null) {
                try {
                    current.merge(TagParser.parseCompoundFully(pending));
                    e.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), current));
                    current = save(e, level);                              // 불러온 뒤의 실제 값(검증/보정된 것)
                } catch (Exception ex) {
                    warnOnce("엔티티 NBT 를 적용하지 못했습니다(" + me.type() + "): " + ex.getMessage());
                }
                pending = me.pollPendingNbt();
            }
            String snbt = current.toString();
            me.setNbtSnapshot(snbt.length() <= 65536 ? snbt : "{}");
        } catch (RuntimeException ex) {
            warnOnce("엔티티 NBT 를 읽지 못했습니다(" + me.type() + "): " + ex);
            me.setNbtSnapshot("{}");                                        // 다시 시도하지 않는다
        }
    }

    private static CompoundTag save(Entity e, ClientLevel level) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        e.saveWithoutId(out);
        return out.buildResult();
    }

    private void warnOnce(String msg) {
        if (warned.add(msg)) LOG.warn("[better-uiux] <mc-viewer> {}", msg);
    }
}
