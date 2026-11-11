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
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
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
 *   <li>만들려면 {@link Level} 이 필요하다: 접속 중이면 진짜 클라이언트 레벨, 타이틀 화면이면 가상 레벨({@link McViewerFakeWorld}).</li>
 *   <li><b>동작</b>: 스크립트가 보낸 블록 이벤트({@code world.blockEvent}, {@code setOpen}, {@code ringBell})를 받아 주고, 월드 틱마다 바닐라 클라이언트 틱
 *       (상자 뚜껑, 셜커 상자, 종, 마법 부여대 ...)을 돌린다. 모든 블록 엔티티에 대해 하되 <b>격리된 가상 레벨</b>({@link McViewerFakeLevel})에서만 한다.
 *       진짜 월드에서는 가상 좌표의 이웃 갱신·충돌 검사가 월드를 건드릴 수 있어서 절대 돌리지 않는다.</li>
 * </ul>
 */
final class McViewerBlockEntities {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");
    private static final int MAX_TICKS_PER_FRAME = 5;

    /** 만들어 둔 블록 엔티티와 상태. 블록(상태+NBT)이 달라지면 다시 만든다. */
    private static final class Held {
        final String key;
        final BlockEntity entity;
        long appliedSeq;                                             // 여기까지의 블록 이벤트는 이미 보냈다
        long tickedTo;                                               // 여기(월드 틱)까지 클라이언트 틱을 돌렸다
        BlockEntityTicker<BlockEntity> ticker;                       // 이 블록 엔티티의 클라이언트 틱(없으면 null)
        boolean tickerResolved, disabled;                            // disabled: 동작(틱/이벤트)이 예외를 던져 더는 돌리지 않는다

        Held(String key, BlockEntity entity, long tickedTo) {
            this.key = key;
            this.entity = entity;
            this.tickedTo = tickedTo;
        }
    }

    private final Map<McvWorld, Map<Long, Held>> live = new WeakHashMap<>();
    private final Set<String> warned = new HashSet<>();
    private Map<Long, Held> current;
    private Set<Long> seen;
    private Level level;
    private CameraRenderState camera;

    /** 한 프레임의 시작. 월드에 접속하지 않았으면 false(그릴 수 없다). */
    boolean begin(McvWorld w, float yaw, float pitch) {
        Level now = McViewerFakeWorld.current();                     // 접속 중이면 진짜 클라이언트 레벨, 아니면 가상 레벨(준비되기 전에는 null)
        if (now != level) live.clear();                              // 진짜 ↔ 가상 레벨이 바뀌면 옛 레벨에 묶인 것들은 버린다
        level = now;
        if (level == null) return false;
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

    void render(McvWorld w, BlockState state, McvBlock b, int x, int y, int z, PoseStack pose, SubmitNodeCollector collector) {
        if (current == null || !(state.getBlock() instanceof EntityBlock)) return;
        long k = McvWorld.key(x, y, z);
        seen.add(k);
        String key = b.stateString() + "|" + b.nbt();
        Held h = current.get(k);
        if (h == null || !h.key.equals(key)) {
            BlockEntity created = create(state, b, new BlockPos(x, y, z));
            h = new Held(key, created, w.tickCount());
            current.put(k, h);
        }
        if (h.entity == null) return;
        BlockPos pos = new BlockPos(x, y, z);
        applyEvents(w, h, x, y, z);
        tick(w, h, pos, state);
        BlockEntityRenderDispatcher dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        dispatcher.prepare(new Vec3(x + 0.5, y + 0.5, z + 0.5));    // 거리 컬링이 "카메라 = 이 블록" 으로 판단하게(뷰어에는 거리 개념이 없다)
        BlockEntityRenderState rs = dispatcher.tryExtractRenderState(h.entity, w.partialTick(), null, false);   // 틱 사이를 보간해 뚜껑이 부드럽게 열린다
        if (rs == null) return;                                      // 렌더러가 없는 블록 엔티티
        rs.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        pose.pushPose();
        pose.translate(x, y, z);
        dispatcher.submit(rs, pose, collector, camera);
        pose.popPose();
    }

    /** 스크립트가 보낸 블록 이벤트를 아직 안 보낸 것만 순서대로 블록 엔티티에 전달한다(다시 만들어진 블록 엔티티는 처음부터 다시 받는다). 가상 레벨에서만. */
    private void applyEvents(McvWorld w, Held h, int x, int y, int z) {
        if (h.disabled || !McViewerFakeWorld.isolated(level)) return;
        for (McvWorld.BlockEvent e : w.blockEventsAt(x, y, z)) {
            if (e.seq() <= h.appliedSeq) continue;
            try {
                h.entity.triggerEvent(e.id(), e.param());
            } catch (RuntimeException ex) {
                disable(h, "블록 이벤트를 처리하지 못했습니다", ex);
                return;
            }
            h.appliedSeq = e.seq();
        }
    }

    /**
     * 월드 틱이 흐른 만큼 바닐라의 <b>클라이언트 틱</b>(상자 뚜껑, 셜커 상자 열림, 종 흔들림, 마법 부여대 책 ...)을 돌린다. 한 프레임에 최대 5틱.
     * 블록 엔티티 종류를 가리지 않고 {@code EntityBlock.getTicker} 가 주는 틱을 그대로 쓴다. 이 틱들은 이웃 갱신·충돌 검사·소리를
     * 부를 수 있지만 <b>격리된 가상 레벨</b>에서만 돌리므로 진짜 월드에는 아무 영향이 없다.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void tick(McvWorld w, Held h, BlockPos pos, BlockState state) {
        long now = w.tickCount();
        long n = now - h.tickedTo;
        h.tickedTo = now;
        if (n <= 0 || h.disabled || !(level instanceof McViewerFakeLevel fake)) return;
        try {
            if (!h.tickerResolved) {
                h.tickerResolved = true;
                h.ticker = ((EntityBlock) state.getBlock()).getTicker(level, state, (net.minecraft.world.level.block.entity.BlockEntityType) h.entity.getType());
            }
            if (h.ticker == null) return;
            fake.setTime(now);
            for (long i = Math.min(n, MAX_TICKS_PER_FRAME); i > 0; i--) h.ticker.tick(level, pos, state, h.entity);
        } catch (RuntimeException ex) {
            disable(h, "블록 엔티티 틱을 돌리지 못했습니다", ex);
        }
    }

    private void disable(Held h, String what, RuntimeException ex) {
        h.disabled = true;
        warnOnce(what + "(" + h.entity.getType() + "): " + ex);
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
