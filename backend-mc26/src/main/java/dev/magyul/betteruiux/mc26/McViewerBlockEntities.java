package dev.magyul.betteruiux.mc26;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.magyul.betteruiux.core.mcv.McvBlock;
import dev.magyul.betteruiux.core.mcv.McvWorld;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 상자·표지판·침대·배너·머리 같은 <b>블록 엔티티</b>를 그린다(이 블록들은 블록 모델이 비어 있고 전용 렌더러가 그린다).
 *
 * <ul>
 *   <li>블록 엔티티 객체는 렌더 스레드에서 필요할 때 만들어 두고(진짜 월드에는 넣지 않는다), 블록이 바뀌면 다시 만든다.</li>
 *   <li>블록에 준 NBT(표지판 글자 등)는 SNBT 로 읽어 블록 엔티티에 불러 준다. 실패하면 한 번만 경고하고 NBT 없이 그린다.</li>
 *   <li>만들려면 {@link ClientLevel} 이 필요해서, 월드에 접속하지 않은 상태(타이틀 화면)에서는 그리지 않는다(엔티티와 같은 제약).</li>
 *   <li>상자 뚜껑 열림 같은 동작은 아직 없다(항상 기본 상태).</li>
 * </ul>
 */
final class McViewerBlockEntities {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");

    /** 만들어 둔 블록 엔티티와, 어떤 블록(상태+NBT)으로 만들었는지. 블록이 달라지면 다시 만든다. */
    private record Held(String key, BlockEntity entity) { }

    private final Map<McvWorld, Map<Long, Held>> live = new WeakHashMap<>();
    private final Set<String> warned = new HashSet<>();
    private Map<Long, Held> current;
    private Set<Long> seen;
    private ClientLevel level;
    private CameraRenderState camera;

    /** 한 프레임의 시작. 월드에 접속하지 않았으면 false(그릴 수 없다). */
    boolean begin(McvWorld w, float yaw, float pitch) {
        level = Minecraft.getInstance().level;
        if (level == null) {
            live.remove(w);                                          // 월드를 떠났다: 옛 레벨에 묶인 것들은 버린다
            return false;
        }
        current = live.computeIfAbsent(w, k -> new HashMap<>());
        seen = new HashSet<>();
        camera = McViewerEntities.camera(yaw, pitch);
        return true;
    }

    /** 스크립트가 지운 블록의 엔티티를 버린다. */
    void end() {
        if (current != null) current.keySet().removeIf(k -> !seen.contains(k));
        current = null;
        seen = null;
    }

    void render(BlockState state, McvBlock b, int x, int y, int z, PoseStack pose, SubmitNodeCollector collector) {
        if (current == null || !(state.getBlock() instanceof EntityBlock)) return;
        long k = McvWorld.key(x, y, z);
        seen.add(k);
        String key = b.stateString() + "|" + b.nbt();
        Held h = current.get(k);
        if (h == null || !h.key().equals(key)) {
            BlockEntity created = create(state, b, new BlockPos(x, y, z));
            h = created == null ? new Held(key, null) : new Held(key, created);
            current.put(k, h);
        }
        if (h.entity() == null) return;
        BlockEntityRenderDispatcher dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        dispatcher.prepare(new Vec3(x + 0.5, y + 0.5, z + 0.5));    // 거리 컬링이 "카메라 = 이 블록" 으로 판단하게(뷰어에는 거리 개념이 없다)
        BlockEntityRenderState rs = dispatcher.tryExtractRenderState(h.entity(), 0f, null, false);
        if (rs == null) return;                                      // 렌더러가 없는 블록 엔티티
        rs.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        pose.pushPose();
        pose.translate(x, y, z);
        dispatcher.submit(rs, pose, collector, camera);
        pose.popPose();
    }

    private BlockEntity create(BlockState state, McvBlock b, BlockPos pos) {
        try {
            BlockEntity be = ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
            if (be == null) return null;
            be.setLevel(level);
            if (b.nbt() != null) {
                try {
                    CompoundTag tag = TagParser.parseCompoundFully(b.nbt());
                    be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                } catch (Exception e) {
                    warnOnce("블록 NBT 를 읽지 못했습니다(" + b.id() + "): " + e.getMessage());
                }
            }
            return be;
        } catch (RuntimeException e) {
            warnOnce("블록 엔티티를 만들지 못했습니다(" + b.id() + "): " + e);
            return null;
        }
    }

    private void warnOnce(String msg) {
        if (warned.add(msg)) LOG.warn("[better-uiux] <mc-viewer> {}", msg);
    }
}
