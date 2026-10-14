package com.noteduo.textcaddy;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;

/** Small, throttled mechanical sound mixer for the rail and card deck. */
public final class SoundEngine {
    private final SoundPool pool;
    private int rotateId;
    private int tickId;
    private int snapId;
    private int rotateStream;
    private int tickStream;
    private int snapStream;
    private boolean enabled;
    private boolean railEnabled = true;
    private boolean cardEnabled = true;
    private boolean rotatingRail;
    private boolean lastTickRail;
    private boolean snapRail;
    private float volume;
    private boolean rotateLoaded;
    private boolean tickLoaded;
    private boolean snapLoaded;
    private long lastTickAt;
    private boolean released;

    public SoundEngine(Context context, boolean enabled, float volume) {
        this.enabled = enabled;
        this.volume = clamp(volume);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build();
        pool.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            if (status != 0) return;
            if (sampleId == rotateId) rotateLoaded = true;
            if (sampleId == tickId) tickLoaded = true;
            if (sampleId == snapId) snapLoaded = true;
        });
        rotateId = pool.load(context, R.raw.rotate_loop, 1);
        tickId = pool.load(context, R.raw.mechanical_tick, 1);
        snapId = pool.load(context, R.raw.mechanical_snap, 1);
    }

    public synchronized void setEnabled(boolean value) {
        if (released) return;
        enabled = value;
        if (!enabled) stopAllMotionSounds();
    }

    public synchronized void setRailEnabled(boolean value) {
        if (released) return;
        railEnabled = value;
        if (!railEnabled && rotatingRail) stopRotate();
        if (!railEnabled && lastTickRail) stopTick();
        if (!railEnabled && snapRail) stopSnap();
    }

    public synchronized void setCardEnabled(boolean value) {
        if (released) return;
        cardEnabled = value;
        if (!cardEnabled && !rotatingRail) stopRotate();
        if (!cardEnabled && !lastTickRail) stopTick();
        if (!cardEnabled && !snapRail) stopSnap();
    }

    public synchronized void setVolume(float value) { if (!released) volume = clamp(value); }

    public synchronized void startRotate(boolean rail) {
        if (released || !sourceEnabled(rail) || !rotateLoaded) return;
        if (rotateStream != 0 && rotatingRail == rail) return;
        stopRotate();
        rotatingRail = rail;
        float gain = rail ? 0.52f : 0.61f;
        float rate = rail ? 0.88f : 1.12f;
        rotateStream = pool.play(rotateId, volume * gain, volume * gain, 1, -1, rate);
    }

    public synchronized void stopRotate() {
        if (rotateStream != 0) {
            if (!released) pool.stop(rotateStream);
            rotateStream = 0;
        }
    }

    public synchronized void playTick(boolean rail) {
        if (released || !sourceEnabled(rail) || !tickLoaded) return;
        long now = SystemClock.uptimeMillis();
        long throttleMs = rail ? 43L : 28L;
        if (now - lastTickAt < throttleMs) return;
        lastTickAt = now;
        stopTick();
        lastTickRail = rail;
        float gain = rail ? 0.40f : 0.37f;
        float rate = rail ? 0.91f : 1.16f;
        tickStream = pool.play(tickId, volume * gain, volume * gain, 2, 0, rate);
    }

    public synchronized void playSnap(boolean rail) {
        if (released) return;
        stopRotate();
        stopTick();
        if (!sourceEnabled(rail) || !snapLoaded) return;
        stopSnap();
        float gain = rail ? 0.53f : 0.57f;
        float rate = rail ? 0.95f : 1.08f;
        snapRail = rail;
        snapStream = pool.play(snapId, volume * gain, volume * gain, 3, 0, rate);
    }

    public synchronized void stopAllMotionSounds() {
        if (released) return;
        stopRotate();
        stopTick();
        stopSnap();
    }

    private void stopTick() {
        if (tickStream != 0) {
            if (!released) pool.stop(tickStream);
            tickStream = 0;
        }
    }

    private void stopSnap() {
        if (snapStream != 0) {
            if (!released) pool.stop(snapStream);
            snapStream = 0;
        }
    }

    private boolean sourceEnabled(boolean rail) {
        return enabled && (rail ? railEnabled : cardEnabled);
    }

    public synchronized void release() {
        if (released) return;
        stopAllMotionSounds();
        pool.release();
        released = true;
    }

    private float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}
