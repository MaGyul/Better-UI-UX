package dev.magyul.betteruiux.mc26;

import dev.magyul.betteruiux.core.doc.UiDocument;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 읽기 전용 데이터 소스: 문서 어디서나 {@code {player.health}} 처럼 쓸 수 있는 값들. 플레이어/월드가 없으면(타이틀 화면 등) 빈 값이나 0.
 * 쓰기는 지원하지 않는다(값을 바꾸려면 등록된 액션만 가능). 모드는 {@link UiApi#registerDataSource} 로 자기 값을 추가한다.
 *
 * <p>내장 값: fps, player.{health,maxHealth,food,saturation,air,maxAir,armor,xpLevel,xpProgress,slot,x,y,z,yaw,pitch,name,gamemode},
 * world.{time,day,dimension}.
 */
final class DataSources {
    private static final Map<String, Supplier<Object>> SOURCES = new ConcurrentHashMap<>();

    static {
        register("fps", () -> Minecraft.getInstance().getFps());
        player("player.health", p -> num(p.getHealth()));
        player("player.maxHealth", p -> num(p.getMaxHealth()));
        player("player.food", p -> p.getFoodData().getFoodLevel());
        player("player.saturation", p -> num(p.getFoodData().getSaturationLevel()));
        player("player.air", LocalPlayer::getAirSupply);
        player("player.maxAir", LocalPlayer::getMaxAirSupply);
        player("player.armor", LocalPlayer::getArmorValue);
        player("player.xpLevel", p -> p.experienceLevel);
        player("player.xpProgress", p -> Math.round(p.experienceProgress * 100));
        player("player.slot", p -> p.getInventory().getSelectedSlot() + 1);
        player("player.x", p -> (int) Math.floor(p.getX()));
        player("player.y", p -> (int) Math.floor(p.getY()));
        player("player.z", p -> (int) Math.floor(p.getZ()));
        player("player.yaw", p -> Math.round(p.getYRot()));
        player("player.pitch", p -> Math.round(p.getXRot()));
        player("player.name", p -> p.getName().getString());
        register("player.gamemode", () -> {
            var mc = Minecraft.getInstance();
            return mc.gameMode == null ? "" : mc.gameMode.getPlayerMode().getName();
        });
        register("world.time", () -> {
            var level = Minecraft.getInstance().level;
            return level == null ? 0 : level.getDefaultClockTime() % 24000L;
        });
        register("world.day", () -> {
            var level = Minecraft.getInstance().level;
            return level == null ? 0 : level.getDefaultClockTime() / 24000L;
        });
        register("world.dimension", () -> {
            var level = Minecraft.getInstance().level;
            return level == null ? "" : level.dimension().identifier().toString();
        });
    }

    private DataSources() {}

    /** 이름은 {@code [A-Za-z0-9_.-]} 만 쓸 수 있다(문서의 {이름} 규칙). */
    static void register(String name, Supplier<?> value) {
        if (!name.matches("[A-Za-z0-9_.\\-]+")) throw new IllegalArgumentException("데이터 소스 이름이 올바르지 않습니다: " + name);
        SOURCES.put(name, () -> {
            try {
                return value.get();
            } catch (RuntimeException e) {
                return "";
            }
        });
    }

    private static void player(String name, Function<LocalPlayer, Object> read) {
        SOURCES.put(name, () -> {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null) return "";
            try {
                return read.apply(p);
            } catch (RuntimeException e) {
                return "";
            }
        });
    }

    /** 정수면 정수로, 아니면 소수 한 자리로(예: 20, 14.5). */
    private static Object num(double d) {
        return d == Math.rint(d) ? (Object) (long) d : (Object) (Math.round(d * 10.0) / 10.0);
    }

    /** 모든 데이터 소스를 문서의 바인딩으로 등록한다(값은 문서가 그려질 때마다 읽는다). */
    static void bindTo(UiDocument doc) {
        SOURCES.forEach((name, supplier) -> doc.bind(name, supplier));
    }
}
