package dev.magyul.betteruiux.mc26;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * {@code <mc-viewer>} 의 블록 엔티티가 내는 소리(종, 상자 여닫기, 셜커 상자 ...)를 재생한다.
 *
 * <p>소리는 <b>위치 없이</b>(듣는 사람 기준, 거리 감쇠 없음) 나온다: 뷰어의 월드는 진짜 월드와 좌표가 아무 상관이 없어서, 위치 소리로 재생하면
 * 플레이어와의 거리에 따라 멋대로 작아지거나 안 들린다. 소리 종류는 {@link SoundSource#BLOCKS}(블록 소리 볼륨 설정을 따른다).
 * 월드마다 {@code world.setMuted(true)} 로 끌 수 있다.
 */
public final class McViewerSounds {
    private static final int RECENT_MAX = 32;
    private static final Deque<String> RECENT = new ArrayDeque<>();

    private McViewerSounds() {}

    /** 최근에 재생한 소리 id(오래된 것부터, 최대 32개). 소리를 직접 들을 수 없는 테스트/디버깅용. */
    public static List<String> recent() {
        synchronized (RECENT) {
            return new ArrayList<>(RECENT);
        }
    }

    /** 최근 소리 기록을 비운다(테스트에서 "이 장면이 낸 소리"만 보려고). */
    public static void clearRecent() {
        synchronized (RECENT) {
            RECENT.clear();
        }
    }

    static void play(SoundEvent event, float volume, float pitch) {
        synchronized (RECENT) {
            RECENT.addLast(event.location().toString());
            while (RECENT.size() > RECENT_MAX) RECENT.removeFirst();
        }
        Minecraft.getInstance().getSoundManager().play(new Instance(event, volume, pitch));
    }

    /** 듣는 사람 기준(relative)이고 거리 감쇠가 없는 소리. */
    private static final class Instance extends AbstractSoundInstance {
        Instance(SoundEvent event, float volume, float pitch) {
            super(event, SoundSource.BLOCKS, RandomSource.create());
            this.volume = volume;
            this.pitch = pitch;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }
    }
}
