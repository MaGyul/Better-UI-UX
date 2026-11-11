package dev.magyul.betteruiux.mc26;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 엔티티·블록 엔티티를 만들 {@link Level} 을 정한다.
 *
 * <ul>
 *   <li>월드에 접속해 있으면 진짜 클라이언트 레벨을 쓴다(엔티티는 거기에 추가하지 않고 만들기만 한다).</li>
 *   <li>타이틀 화면처럼 접속하지 않았으면 {@link McViewerFakeLevel}(빈 가상 레벨)을 쓴다. 이 레벨의 레지스트리(데미지 종류, 소 변종 ...)는
 *       바닐라 데이터팩에서 <b>백그라운드 스레드로 한 번</b> 불러온다(월드 생성 화면이 하는 것과 같은 방식, 1~2초). 준비되는 동안은
 *       {@code null} 을 돌려주고, 호출하는 쪽은 그 프레임의 엔티티를 건너뛴다.</li>
 * </ul>
 * 진짜 게임의 레지스트리/태그에는 아무것도 적용하지 않는다(읽기만 한다).
 */
final class McViewerFakeWorld {
    private static final Logger LOG = LoggerFactory.getLogger("better_uiux");

    /** 불러오기 결과(레지스트리 + 필요한 홀더). 준비되면 렌더 스레드가 가상 레벨을 만든다(레벨은 만든 스레드를 "자기 스레드"로 기억한다). */
    private record Loaded(RegistryAccess access, Holder<DimensionType> dimension, Holder<Biome> biome) { }

    private static volatile Loaded loaded;
    private static volatile boolean loading, failed;
    private static McViewerFakeLevel fake;

    private McViewerFakeWorld() {}

    /** 지금 엔티티를 만들 레벨. 아직 준비 중이거나 만들 수 없으면 null. 렌더 스레드에서만 부른다. */
    static Level current() {
        Level real = Minecraft.getInstance().level;
        if (real != null) return real;
        if (fake != null) return fake;
        Loaded l = loaded;
        if (l != null) {
            fake = new McViewerFakeLevel(l.access(), l.dimension(), l.biome());
            return fake;
        }
        startLoading();
        return null;
    }

    /** 타이틀 화면 등에서 쓰는 가상 레벨이 아직 불러오는 중인지(화면에 "준비 중" 표시 같은 것을 할 때 쓴다). */
    static boolean loading() { return loading && loaded == null && !failed; }

    private static synchronized void startLoading() {
        if (loading || failed) return;
        loading = true;
        Thread t = new Thread(() -> {
            long t0 = System.nanoTime();
            try {
                loaded = load();
                LOG.info("[better-uiux] <mc-viewer> virtual level registries loaded in {} ms", (System.nanoTime() - t0) / 1_000_000);
            } catch (Throwable e) {
                failed = true;
                LOG.warn("[better-uiux] <mc-viewer> 월드에 접속하지 않은 상태에서는 엔티티를 그릴 수 없습니다(가상 레벨 준비 실패): {}", e.toString());
            }
        }, "better-uiux-fake-level-loader");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    /** 바닐라 데이터팩에서 월드 레지스트리를 읽는다(바닐라 {@code WorldLoader} 가 하는 것의 필요한 부분만). */
    private static Loaded load() throws Exception {
        try (MultiPackResourceManager rm = new MultiPackResourceManager(PackType.SERVER_DATA, List.<net.minecraft.server.packs.PackResources>of(ServerPacksSource.createVanillaPackSource().fullResources()))) {
            LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
            // 태그는 "읽어서 조회용으로만" 쓴다: pending.apply() 를 부르면 진짜 게임의 정적 레지스트리에 태그를 다시 묶게 되므로 부르지 않는다.
            List<Registry.PendingTags<?>> pending = TagLoader.loadTagsForExistingRegistries(rm, layers.getLayer(RegistryLayer.STATIC));
            List<HolderLookup.RegistryLookup<?>> lookups = TagLoader.buildUpdatedLookups(layers.getAccessForLoading(RegistryLayer.WORLD), pending);
            RegistryAccess.Frozen world = RegistryDataLoader.load(rm, lookups, RegistryDataLoader.WORLD_REGISTRIES, Runnable::run).join();
            RegistryAccess.Frozen access = layers.replaceFrom(RegistryLayer.WORLD, world).compositeAccess();
            Holder<DimensionType> dimension = access.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
            Holder<Biome> biome = access.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
            return new Loaded(access, dimension, biome);
        }
    }
}
