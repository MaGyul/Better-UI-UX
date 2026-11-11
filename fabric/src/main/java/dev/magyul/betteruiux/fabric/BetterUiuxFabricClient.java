package dev.magyul.betteruiux.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.magyul.betteruiux.mc26.ClientSync;
import dev.magyul.betteruiux.mc26.HudManager;
import dev.magyul.betteruiux.mc26.SyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import dev.magyul.betteruiux.mc26.UiApi;
import dev.magyul.betteruiux.mc26.UiOpener;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.TitleScreen;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric 클라이언트 진입점. 현재: {@code /uiux open <id>} 로 XML 화면을 연다. */
public final class BetterUiuxFabricClient implements ClientModInitializer {
    public static final String MOD_ID = "better_uiux";

    @Override
    public void onInitializeClient() {
        Path baseDir = FabricLoader.getInstance().getConfigDir().resolve("better-uiux");
        UiApi.init(baseDir);
        // <mc-viewer>: 가상 월드를 GUI Picture-in-Picture 로 그리는 렌더러를 등록한다.
        net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry.register(ctx -> new dev.magyul.betteruiux.mc26.McViewerPip());
        // 서버 동기화(채널은 공통 진입점이 등록): 접속하면 인사를 보내고, 서버 메시지는 렌더 스레드에서 처리한다.
        ClientSync.init(baseDir);
        ClientSync.setSender(ClientPlayNetworking::send);
        // 개발용(-Dbetteruiux.noclientsync=true): 서버 동기화 채널을 받지 않는 클라이언트를 흉내 낸다(이 모드가 없는 클라이언트가 접속해도 서버가 멀쩡한지 확인).
        if (!Boolean.getBoolean("betteruiux.noclientsync")) {
            ClientPlayNetworking.registerGlobalReceiver(SyncPayload.TYPE, (payload, context) -> ClientSync.onPayload(payload));
        }
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientPlayNetworking.canSend(SyncPayload.TYPE) && !Boolean.getBoolean("betteruiux.noclientsync")) ClientSync.onJoin();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientSync.onDisconnect());
        // XML HUD: 설정(config.toml 의 [hud] ids)에 적은 문서를 인게임 HUD 맨 위에 그린다.
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), HudManager::render);
        // 개발용 자동 열기: 타이틀 화면이 뜬 뒤 한 번만 연다(시작 시점에 열면 타이틀 화면이 덮어쓴다).
        boolean[] opened = {false};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!opened[0] && client.gui.screen() instanceof TitleScreen) {
                opened[0] = true;
                UiOpener.autoOpenIfRequested(baseDir);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientSync.tick());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("uiux")
                        .then(ClientCommands.literal("open")
                                .then(ClientCommands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            UiApi.open(StringArgumentType.getString(ctx, "id"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("publish")
                                .then(ClientCommands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> ClientSync.publishUi(StringArgumentType.getString(ctx, "id")) ? 1 : 0)))
                        .then(ClientCommands.literal("editor")                       // 열린 화면 없는 빈 에디터: 안에서 편집할 화면을 고른다
                                .executes(ctx -> {
                                    UiApi.editor();
                                    return 1;
                                }))
                        .then(ClientCommands.literal("edit")
                                .then(ClientCommands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            UiApi.edit(StringArgumentType.getString(ctx, "id"));
                                            return 1;
                                        })))));
    }
}
