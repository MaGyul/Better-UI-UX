package dev.magyul.betteruiux.mc26;

import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;

/**
 * 서버 쪽 권한 확인. 노드(퍼미션)를 먼저 보고, 권한 플러그인(LuckPerms 등)이 값을 주지 않으면 기본 권한 레벨로 판단한다.
 *
 * <ul>
 *   <li>{@code better_uiux:publish} — 에디터에서 서버로 발행(기본 레벨 4)</li>
 *   <li>{@code better_uiux:serverui} — {@code /serverui} 명령(기본 레벨 2)</li>
 * </ul>
 *
 * Fabric 은 Fabric API 의 permission-api-v1, NeoForge 는 PermissionAPI 로 연결한다(로더 모듈이 {@link #setChecker} 로 알려 준다).
 * 확인 중 예외가 나면 거부한다(안전한 쪽).
 */
public final class Perms {
    private Perms() {}

    public enum Node {
        PUBLISH("publish", PermissionLevel.OWNERS),
        SERVERUI("serverui", PermissionLevel.GAMEMASTERS);

        public final Identifier id;
        public final PermissionLevel level;

        Node(String path, PermissionLevel level) {
            this.id = Identifier.fromNamespaceAndPath("better_uiux", path);
            this.level = level;
        }
    }

    @FunctionalInterface
    public interface Checker {
        boolean has(ServerPlayer player, Node node);
    }

    private static volatile Checker checker = Perms::byLevel;

    /** 로더의 권한 API 로 확인하는 방법을 바꾼다(기본은 권한 레벨만). */
    public static void setChecker(Checker c) { checker = c; }

    public static boolean has(ServerPlayer player, Node node) {
        try {
            return checker.has(player, node);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 권한 플러그인 없이 기본 권한 레벨로만 판단한다(기본 규칙이자 플러그인이 값을 주지 않을 때의 대비). */
    public static boolean byLevel(ServerPlayer player, Node node) { return levelOk(player.permissions(), node.level); }

    public static boolean levelOk(PermissionSet set, PermissionLevel level) {
        return switch (level) {
            case ALL -> true;
            case MODERATORS -> Commands.LEVEL_MODERATORS.check(set);
            case GAMEMASTERS -> Commands.LEVEL_GAMEMASTERS.check(set);
            case ADMINS -> Commands.LEVEL_ADMINS.check(set);
            case OWNERS -> Commands.LEVEL_OWNERS.check(set);
        };
    }

    /** 명령의 {@code requires} 용. 플레이어는 노드로, 콘솔/명령 블록은 기본 레벨로 판단한다. */
    public static Predicate<CommandSourceStack> requires(Node node) {
        return src -> src.getEntity() instanceof ServerPlayer p ? has(p, node) : levelOk(src.permissions(), node.level);
    }
}
