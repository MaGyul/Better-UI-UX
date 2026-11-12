package dev.magyul.betteruiux.mc26;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import dev.magyul.betteruiux.core.mcv.McvBlock;
import dev.magyul.betteruiux.core.mcv.McvWorld;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.Scoreboard;

/**
 * 월드에 접속하지 않았을 때(타이틀 화면) 엔티티·블록 엔티티를 <b>만들고 그리기</b> 위한 가상 클라이언트 레벨.
 * 블록도 엔티티도 없는 빈 세계이고(모든 칸이 공기), 소리·폭발·블록 갱신 같은 것은 전부 무시한다. 레지스트리(데미지 종류, 소 변종 ...)는
 * 바닐라 데이터팩에서 불러온 것을 쓴다({@link McViewerFakeWorld}).
 *
 * <p>진짜 게임 상태와는 완전히 분리되어 있다: 어떤 렌더러/네트워크/서버에도 연결되지 않고, 여기서 만든 엔티티는 어디에도 등록되지 않는다.
 */
final class McViewerFakeLevel extends Level {
    private final Biome biomeForEmptyChunks;
    private final Holder<Biome> biome;
    private final FakeChunkSource chunkSource;
    private final Scoreboard scoreboard = new Scoreboard();
    private final TickRateManager tickRateManager = new TickRateManager();
    private LevelData.RespawnData respawn;

    McViewerFakeLevel(RegistryAccess access, Holder<DimensionType> dimensionType, Holder<Biome> biome) {
        super(new ClientLevel.ClientLevelData(Difficulty.NORMAL, false, true), Level.OVERWORLD, access, dimensionType, true, false, 0L, 1000000);
        this.biome = biome;
        this.biomeForEmptyChunks = biome.value();
        this.chunkSource = new FakeChunkSource(this);
        this.respawn = levelData.getRespawnData();
    }

    /** 가상 레벨의 게임 시간을 맞춘다(블록 엔티티의 틱이 {@code level.getGameTime()} 을 읽는다). 렌더 스레드에서만 쓰므로 월드마다 바꿔 가며 써도 된다. */
    void setTime(long gameTime) {
        if (levelData instanceof ClientLevel.ClientLevelData d) d.setGameTime(gameTime);
    }

    // ---- 뷰어 월드와의 연결: 블록 엔티티가 "월드를 읽는" 동작(비콘 피라미드·빔, 전도체 틀, 불 주변 ...)을 위해 ----

    private McvWorld bound;
    private final java.util.Map<McvBlock, BlockState> stateCache = new java.util.HashMap<>();

    /**
     * 지금 틱을 돌리는 뷰어 월드를 연결한다. 연결해 두면 {@code getBlockState}/{@code getFluidState}/{@code getHeight} 가 그 월드의 블록을 답한다
     * (비콘이 아래 피라미드와 위쪽 하늘을 보고 빔을 정하는 것처럼). 월드 밖은 공기다. 블록 엔티티는 따로 두지 않는다(null).
     * 렌더 스레드에서 월드마다 바꿔 가며 쓴다.
     */
    void bind(McvWorld world) { this.bound = world; }

    private BlockState stateAt(int x, int y, int z) {
        McvWorld w = bound;
        if (w == null || x < 0 || y < 0 || z < 0 || x >= w.sizeX() || y >= w.sizeY() || z >= w.sizeZ()) return Blocks.AIR.defaultBlockState();
        McvBlock b = w.getBlock(x, y, z);
        if (b.isAir()) return Blocks.AIR.defaultBlockState();
        BlockState s = stateCache.get(b);
        if (s == null) {
            s = McViewerPip.toState(b);
            if (s == null) s = Blocks.AIR.defaultBlockState();
            stateCache.put(b, s);
        }
        return s;
    }

    @Override public BlockState getBlockState(BlockPos pos) { return stateAt(pos.getX(), pos.getY(), pos.getZ()); }

    @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

    /** 기둥에서 가장 높은 블록의 위(바닐라 높이 지도와 같다: 가장 높은 블록 y + 1). 블록이 없으면 레벨의 최저 높이. */
    @Override
    public int getHeight(net.minecraft.world.level.levelgen.Heightmap.Types type, int x, int z) {
        McvWorld w = bound;
        if (w != null && x >= 0 && z >= 0 && x < w.sizeX() && z < w.sizeZ()) {
            for (int y = w.sizeY() - 1; y >= 0; y--) if (!w.getBlock(x, y, z).isAir()) return y + 1;
        }
        return getMinY();
    }

    /** 스포너가 안의 몹을 보여 주고 돌리려면 "근처에 플레이어가 있다"고 답해야 한다(가상 레벨에는 플레이어가 없다). */
    @Override public boolean hasNearbyAlivePlayer(double x, double y, double z, double range) { return true; }

    /** 모든 칸이 공기인 청크만 돌려주는 청크 공급자. */
    private final class FakeChunkSource extends ChunkSource {
        private final McViewerFakeLevel level;
        private final LevelLightEngine light;

        FakeChunkSource(McViewerFakeLevel level) {
            this.level = level;
            this.light = new LevelLightEngine(this, false, false);
        }

        @Override
        public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean load) {
            return new EmptyLevelChunk(level, new ChunkPos(x, z), biome);
        }

        @Override public void tick(java.util.function.BooleanSupplier hasTime, boolean tickChunks) { }

        @Override public String gatherStats() { return "fake"; }

        @Override public int getLoadedChunksCount() { return 0; }

        @Override public LevelLightEngine getLightEngine() { return light; }

        @Override public net.minecraft.world.level.BlockGetter getLevel() { return level; }

        @Override public void close() { }
    }

    @Override public ChunkSource getChunkSource() { return chunkSource; }

    @Override public LevelLightEngine getLightEngine() { return chunkSource.getLightEngine(); }

    @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) { }

    private boolean muted;

    /** 지금 틱을 돌리는 월드가 소리를 껐는지(렌더 스레드에서 월드마다 바꿔 가며 쓴다). */
    void setMuted(boolean muted) { this.muted = muted; }

    /** 블록 엔티티의 틱/이벤트가 내는 소리(종 공명, 비콘, 전도체 ...)는 위치 없이 그대로 재생한다(진짜 월드와 좌표가 무관하므로). */
    @Override public void playSeededSound(Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed) {
        if (!muted) McViewerSounds.play(sound.value(), volume, pitch);
    }

    @Override public void playSeededSound(Entity except, Entity sourceEntity, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed) {
        if (!muted) McViewerSounds.play(sound.value(), volume, pitch);
    }

    @Override public void playLocalSound(double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch, boolean distanceDelay) {
        if (!muted) McViewerSounds.play(sound, volume, pitch);
    }

    @Override public void playLocalSound(BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch, boolean distanceDelay) {
        if (!muted) McViewerSounds.play(sound, volume, pitch);
    }

    @Override
    public void explode(Entity source, DamageSource damageSource, ExplosionDamageCalculator calculator, double x, double y, double z, float radius, boolean fire,
                        Level.ExplosionInteraction interaction, net.minecraft.core.particles.ParticleOptions small, net.minecraft.core.particles.ParticleOptions large,
                        net.minecraft.util.random.WeightedList<net.minecraft.core.particles.ExplosionParticleInfo> particles, Holder<SoundEvent> sound) { }

    @Override public String gatherChunkSourceStats() { return "fake"; }

    @Override public void setRespawnData(LevelData.RespawnData data) { this.respawn = data; }

    @Override public LevelData.RespawnData getRespawnData() { return respawn; }

    @Override public Entity getEntity(int id) { return null; }

    @Override public Collection<EnderDragonPart> dragonParts() { return List.of(); }

    @Override public TickRateManager tickRateManager() { return tickRateManager; }

    @Override public MapItemSavedData getMapData(MapId id) { return null; }

    @Override public void destroyBlockProgress(int id, BlockPos pos, int progress) { }

    @Override public Scoreboard getScoreboard() { return scoreboard; }

    @Override
    public net.minecraft.world.item.crafting.RecipeAccess recipeAccess() { throw new UnsupportedOperationException("가상 레벨에는 레시피가 없습니다"); }

    @Override
    protected LevelEntityGetter<Entity> getEntities() {
        return new LevelEntityGetter<>() {
            @Override public Entity get(int id) { return null; }

            @Override public Entity get(UUID id) { return null; }

            @Override public Iterable<Entity> getAll() { return List.of(); }

            @Override public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AbortableIterationConsumer<U> consumer) { }

            @Override public void get(AABB box, Consumer<Entity> consumer) { }

            @Override public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AABB box, AbortableIterationConsumer<U> consumer) { }
        };
    }

    @Override
    public net.minecraft.world.clock.ClockManager clockManager() { throw new UnsupportedOperationException("가상 레벨에는 시계가 없습니다"); }

    @Override
    public net.minecraft.world.attribute.EnvironmentAttributeSystem environmentAttributes() { throw new UnsupportedOperationException("가상 레벨에는 환경 속성이 없습니다"); }

    @Override public List<? extends Player> players() { return List.of(); }

    @Override public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) { return biome; }

    @Override public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { return net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS; }

    @Override public void gameEvent(Holder<net.minecraft.world.level.gameevent.GameEvent> event, net.minecraft.world.phys.Vec3 pos, net.minecraft.world.level.gameevent.GameEvent.Context context) { }

    @Override public void levelEvent(Entity entity, int type, BlockPos pos, int data) { }

    @Override public int getSeaLevel() { return 63; }

    private final net.minecraft.world.level.border.WorldBorder border = new net.minecraft.world.level.border.WorldBorder();

    @Override public net.minecraft.world.level.border.WorldBorder getWorldBorder() { return border; }

    @Override public net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.material.Fluid> getFluidTicks() { return net.minecraft.world.ticks.BlackholeTickAccess.emptyLevelList(); }

    @Override public net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.block.Block> getBlockTicks() { return net.minecraft.world.ticks.BlackholeTickAccess.emptyLevelList(); }
}
