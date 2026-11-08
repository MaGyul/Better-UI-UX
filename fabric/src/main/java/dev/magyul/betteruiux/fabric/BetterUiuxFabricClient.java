package dev.magyul.betteruiux.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.magyul.betteruiux.mc26.UiOpener;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.TitleScreen;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric 클라이언트 진입점. 현재: {@code /uiux open <id>} 로 XML 화면을 연다. */
public final class BetterUiuxFabricClient implements ClientModInitializer {
    public static final String MOD_ID = "better_uiux";

    @Override
    public void onInitializeClient() {
        Path baseDir = FabricLoader.getInstance().getConfigDir().resolve("better-uiux");
        // 개발용 자동 열기: 타이틀 화면이 뜬 뒤 한 번만 연다(시작 시점에 열면 타이틀 화면이 덮어쓴다).
        boolean[] opened = {false};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!opened[0] && client.gui.screen() instanceof TitleScreen) {
                opened[0] = true;
                UiOpener.autoOpenIfRequested(baseDir);
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("uiux")
                        .then(ClientCommands.literal("open")
                                .then(ClientCommands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            UiOpener.open(StringArgumentType.getString(ctx, "id"), baseDir);
                                            return 1;
                                        })))));
    }
}
