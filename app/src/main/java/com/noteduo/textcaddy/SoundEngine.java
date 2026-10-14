package com.noteduo.textcaddy;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

public final class SoundEngine {
    private final SoundPool pool;
    private int rotateId;
    private int tickId;
    private int snapId;
    private int rotateStream;
    private boolean enabled;
    private float volume;
    private boolean rotateLoaded;
    private boolean tickLoaded;
    private boolean snapLoaded;

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
        enabled = value;
        if (!enabled) stopRotate();
    }

    public synchronized void setVolume(float value) { volume = clamp(value); }

    public synchronized void startRotate() {
        if (!enabled || !rotateLoaded || rotateStream != 0) return;
        rotateStream = pool.play(rotateId, volume * 0.68f, volume * 0.68f, 1, -1, 1.0f);
    }

    public synchronized void stopRotate() {
        if (rotateStream != 0) {
            pool.stop(rotateStream);
            rotateStream = 0;
        }
    }

    public synchronized void playTick() {
        if (enabled && tickLoaded) pool.play(tickId, volume * 0.46f, volume * 0.46f, 2, 0, 1.0f);
    }

    public synchronized void playSnap() {
        stopRotate();
        if (enabled && snapLoaded) pool.play(snapId, volume * 0.64f, volume * 0.64f, 3, 0, 1.0f);
    }

    public synchronized void release() {
        stopRotate();
        pool.release();
    }

    private float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}
