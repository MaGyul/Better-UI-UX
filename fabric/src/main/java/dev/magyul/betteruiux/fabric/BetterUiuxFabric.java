package dev.magyul.betteruiux.fabric;

import dev.magyul.betteruiux.core.sync.SyncLimits;
import dev.magyul.betteruiux.mc26.Perms;
import dev.magyul.betteruiux.mc26.ServerCommands;
import net.fabricmc.fabric.api.permission.v1.PermissionContext;
import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.fabricmc.fabric.api.permission.v1.PermissionEvents;
import net.fabricmc.fabric.api.permission.v1.PermissionNode;
import dev.magyul.betteruiux.mc26.ServerSync;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import dev.magyul.betteruiux.mc26.SyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric 공통 진입점(전용 서버/클라이언트 모두). 서버 동기화 채널을 등록한다.
 * 이 모드가 없는 쪽과는 채널이 없어서 서로 아무것도 보내지 않는다(서버 기능만 꺼진다).
 */
public final class BetterUiuxFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(SyncPayload.TYPE, toPlay(SyncPayload.CODEC));
        PayloadTypeRegistry.serverboundPlay().registerLarge(SyncPayload.TYPE, toPlay(SyncPayload.CODEC), SyncLimits.MAX_MESSAGE + 8);

        ServerSync.setSender((player, payload) -> {
            if (ServerPlayNetworking.canSend(player, SyncPayload.TYPE)) ServerPlayNetworking.send(player, payload);
        });
        ServerPlayNetworking.registerGlobalReceiver(SyncPayload.TYPE, (payload, context) -> ServerSync.onPayload(context.player(), payload));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerSync.onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerSync.onDisconnect(handler.getPlayer()));
        ServerTickEvents.END_SERVER_TICK.register(server -> ServerSync.tick());
        // 권한: Fabric API 의 permission-api-v1(LuckPerms 등 권한 플러그인이 제공). 값이 없으면 기본 권한 레벨.
        Perms.setChecker((player, node) -> ((PermissionContextOwner) player).checkPermission(node.id, node.level));
        if (System.getProperty("betteruiux.perm.file") != null) PermissionEvents.ON_REQUEST.register(new DevPermissions());
        ServerCommands.registerDemoActions();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ServerCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> ServerSync.loadBundle(FabricLoader.getInstance().getConfigDir().resolve("better-uiux")));
    }

    /** 공용 코덱(ByteBuf)을 play 단계 버퍼용 코덱으로 본다. */
    private static net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, SyncPayload> toPlay(
            net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, SyncPayload> c) {
        return net.minecraft.network.codec.StreamCodec.of((buf, v) -> c.encode(buf, v), buf -> c.decode(buf));
    }

    /**
     * 개발용 가짜 권한 제공자(-Dbetteruiux.perm.file=경로): 파일의 {@code allow <노드>} / {@code deny <노드>} 줄을 매번 읽어 답한다.
     * 권한 플러그인이 있을 때와 같은 경로(PermissionEvents.ON_REQUEST)로 확인되는지 자동 시험에서 보려는 것이다.
     */
    private static final class DevPermissions implements PermissionEvents.OnRequest {
        @Override
        public <T> T handlePermissionRequest(PermissionContext ctx, PermissionNode<T> node) {
            try {
                var file = java.nio.file.Path.of(System.getProperty("betteruiux.perm.file"));
                if (!java.nio.file.Files.exists(file)) return null;
                for (String line : java.nio.file.Files.readAllLines(file)) {
                    String[] t = line.trim().split("\\s+");
                    if (t.length == 2 && t[1].equals(node.key().toString())) return node.cast(t[0].equals("allow"));
                }
            } catch (java.io.IOException | RuntimeException e) {
                // 파일을 못 읽으면 판단하지 않는다(기본 규칙)
            }
            return null;
        }
    }
}
