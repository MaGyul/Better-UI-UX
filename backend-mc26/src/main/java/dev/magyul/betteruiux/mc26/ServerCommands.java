package dev.magyul.betteruiux.mc26;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.magyul.betteruiux.core.sync.ActionSchema;
import dev.magyul.betteruiux.core.sync.SyncException;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 서버 명령: {@code /serverui open|close <플레이어> <id>}, {@code /serverui state <플레이어> <키> <값>} (권한 레벨 2 이상). */
public final class ServerCommands {
    private ServerCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("serverui").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("open").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                            String id = StringArgumentType.getString(c, "id");
                            try {
                                boolean ok = ServerSync.open(p, id, Map.of());
                                reply(c.getSource(), ok ? id + " 화면을 열었습니다" : p.getName().getString() + " 의 클라이언트에 서버 UI 기능이 없습니다");
                                return ok ? 1 : 0;
                            } catch (SyncException e) {
                                reply(c.getSource(), e.getMessage());
                                return 0;
                            }
                        }))))
                .then(Commands.literal("close").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("id", StringArgumentType.word()).executes(c -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                            return ServerSync.close(p, StringArgumentType.getString(c, "id")) ? 1 : 0;
                        }))))
                .then(Commands.literal("state").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("value", StringArgumentType.greedyString()).executes(c -> {
                                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                                    String raw = StringArgumentType.getString(c, "value");
                                    Object v = raw;
                                    try { v = Long.parseLong(raw); } catch (NumberFormatException ignored) { }
                                    try {
                                        ServerSync.set(p, StringArgumentType.getString(c, "key"), v);
                                        return 1;
                                    } catch (SyncException e) {
                                        reply(c.getSource(), e.getMessage());
                                        return 0;
                                    }
                                }))))));
    }

    private static void reply(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal("[better-uiux] " + msg), false);
    }

    /** 샘플 서버 동작(XML 의 {@code onClick="server:demo:ping"}): 눌린 횟수를 {@code {server.clicks}} 로 돌려준다. */
    public static void registerDemoActions() {
        int[] clicks = {0};
        ServerSync.core().registerAction("demo:ping", ActionSchema.none(), (playerId, args) -> {
            var s = ServerSync.sessionById(playerId);
            if (s == null) return;
            try { s.set("clicks", (long) ++clicks[0]); } catch (SyncException ignored) { }
        });
        ServerSync.core().registerAction("demo:say",
                ActionSchema.none().with("text", ActionSchema.Arg.string(64)).with("n", ActionSchema.Arg.integer(1, 5).optional()),
                (playerId, args) -> {
                    var s = ServerSync.sessionById(playerId);
                    if (s == null) return;
                    try { s.set("said", String.valueOf(args.get("text")) + " x" + args.getOrDefault("n", 1L)); } catch (SyncException ignored) { }
                });
    }
}
