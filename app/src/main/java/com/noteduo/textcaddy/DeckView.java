package com.noteduo.textcaddy;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A lightweight touch-driven carousel. Text itself remains level while card panels follow an arc. */
public final class DeckView extends View {
    public interface Listener {
        void onCardChanged(CardRecord card);
        void onCardTap(CardRecord card);
        void onCardLongPress(CardRecord card);
        void onSettled(CardRecord card);
    }

    private final Listener listener;
    private final SoundEngine sound;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int touchSlop;
    private List<CardRecord> cards = new ArrayList<>();
    private float progress;
    private float downY;
    private float downX;
    private float downProgress;
    private float lastProgress;
    private float step;
    private boolean dragging;
    private boolean longPressed;
    private boolean arcEnabled = true;
    private boolean hapticsEnabled;
    private boolean motionSoundStarted;
    private VelocityTracker velocityTracker;
    private ValueAnimator animator;
    private Runnable longPressRunnable;
    private int lastRoundedIndex = -1;
    private final float density;

    public DeckView(Context context, Listener listener, SoundEngine sound) {
        super(context);
        this.listener = listener;
        this.sound = sound;
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setFocusable(true);
        setClickable(true);
    }

    public void setCards(List<CardRecord> next, String preferredId) {
        if (animator != null) animator.cancel();
        sound.stopRotate();
        cards = new ArrayList<>(next);
        progress = 0f;
        if (preferredId != null) {
            for (int i = 0; i < cards.size(); i++) {
                if (preferredId.equals(cards.get(i).id)) { progress = i; break; }
            }
        }
        lastRoundedIndex = Math.round(progress);
        invalidate();
        if (listener != null) listener.onCardChanged(getSelectedCard());
    }

    public CardRecord getSelectedCard() {
        if (cards.isEmpty()) return null;
        int index = Math.max(0, Math.min(cards.size() - 1, Math.round(progress)));
        return cards.get(index);
    }

    public void setArcEnabled(boolean enabled) { arcEnabled = enabled; invalidate(); }
    public void setHapticsEnabled(boolean enabled) { hapticsEnabled = enabled; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        paint.clearShadowLayer();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(35, 119, 105, 178));
        rect.set(w * 0.05f, h * 0.10f, w * 0.95f, h * 0.90f);
        canvas.drawArc(rect, 208, 124, false, paint);
        rect.set(w * 0.11f, h * 0.19f, w * 0.89f, h * 0.81f);
        paint.setColor(Color.argb(20, 119, 105, 178));
        canvas.drawArc(rect, 210, 120, false, paint);
        paint.setStyle(Paint.Style.FILL);

        if (cards.isEmpty()) {
            drawEmptyState(canvas, w, h);
            return;
        }
        float cardHeight = Math.min(dp(280), h * 0.57f);
        cardHeight = Math.max(dp(190), cardHeight);
        float cardWidth = Math.min(w - dp(27), dp(410));
        cardWidth = Math.max(dp(180), cardWidth);
        step = arcEnabled ? Math.min(cardHeight * 0.77f, h * 0.365f) : Math.min(cardHeight * 0.82f, h * 0.40f);
        float cx = w / 2f;
        float cy = h / 2f + dp(2);
        int base = (int) Math.floor(progress);
        List<Integer> drawOrder = new ArrayList<>();
        for (int index = Math.max(0, base - 3); index <= Math.min(cards.size() - 1, base + 3); index++) {
            if (Math.abs(index - progress) <= 2.65f) drawOrder.add(index);
        }
        java.util.Collections.sort(drawOrder, (a, b) -> Float.compare(Math.abs(b - progress), Math.abs(a - progress)));
        for (int index : drawOrder) {
            float diff = index - progress;
            float offsetY = diff * step;
            float offsetX = arcEnabled ? (float) Math.sin(diff * 0.76f) * dp(13) : 0f;
            float scale = arcEnabled ? 1f - Math.min(Math.abs(diff) * 0.075f, 0.22f) : 1f - Math.min(Math.abs(diff) * 0.035f, 0.08f);
            float rotation = arcEnabled ? (float) (Math.sin(diff * 0.60f) * 4.3f) : 0f;
            int alpha = (int) (255 - Math.min(Math.abs(diff) * 55f, 150f));
            float left = cx - cardWidth / 2f + offsetX;
            float top = cy - cardHeight / 2f + offsetY;
            drawCard(canvas, cards.get(index), index, left, top, cardWidth, cardHeight, scale, rotation, alpha, cx + offsetX, cy + offsetY);
        }
        paint.clearShadowLayer();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(150, 131, 125, 158));
        paint.setTextSize(dp(10));
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("上下拨动 · 轻触阅读 · 长按操作", w / 2f, h - dp(11), paint);
    }

    private void drawCard(Canvas canvas, CardRecord card, int index, float left, float top,
                          float width, float height, float scale, float rotation, int alpha,
                          float pivotX, float pivotY) {
        int save = canvas.save();
        canvas.rotate(rotation, pivotX, pivotY);
        canvas.scale(scale, scale, pivotX, pivotY);
        rect.set(left, top, left + width, top + height);
        int bg = card.color;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(darken(bg, 0.96f), alpha));
        paint.setShadowLayer(dp(index == Math.round(progress) ? 17 : 7), 0, dp(5), Color.argb(alpha / 3, 52, 45, 83));
        canvas.drawRoundRect(rect, dp(22), dp(22), paint);
        paint.clearShadowLayer();
        paint.setColor(withAlpha(lighten(bg, 1.045f), alpha));
        rect.inset(dp(1), dp(1));
        canvas.drawRoundRect(rect, dp(21), dp(21), paint);
        rect.inset(-dp(1), -dp(1));
        paint.setColor(withAlpha(Color.argb(30, 255, 255, 255), alpha));
        rect.set(left + dp(13), top + dp(13), left + width - dp(13), top + dp(16));
        canvas.drawRoundRect(rect, dp(2), dp(2), paint);
        canvas.restoreToCount(save);

        // Keep lettering level for legibility even while the card panel tilts underneath it.
        float scaledWidth = width * scale;
        float scaledHeight = height * scale;
        float inset = dp(21) * scale;
        float textLeft = pivotX - scaledWidth / 2f + inset;
        float right = pivotX + scaledWidth / 2f - inset;
        float topText = pivotY - scaledHeight / 2f + dp(33) * scale;
        paint.setColor(withAlpha(contrastInk(bg), alpha));
        paint.setTextSize(dp(10) * scale);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.LEFT);
        String badge = card.pinned ? "置顶 · " : "";
        if (card.favorite) badge += "收藏 · ";
        badge += "文字记录";
        canvas.drawText(badge, textLeft, topText, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(dp(10) * scale);
        paint.setColor(withAlpha(Color.argb(150, 53, 49, 70), alpha));
        canvas.drawText(String.format(Locale.getDefault(), "%02d / %02d", index + 1, cards.size()), right, topText, paint);

        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(dp(18) * scale);
        paint.setColor(withAlpha(contrastInk(bg), alpha));
        String title = card.displayTitle();
        if (title.length() > 25) title = title.substring(0, 24) + "…";
        canvas.drawText(title, textLeft, pivotY - scaledHeight / 2f + dp(67) * scale, paint);

        float bodyTop = pivotY - scaledHeight / 2f + dp(97) * scale;
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(dp(14) * scale);
        paint.setColor(withAlpha(Color.rgb(58, 55, 73), alpha));
        float available = scaledWidth - inset * 2f;
        String bodyText = card.body == null ? "" : card.body.trim();
        if (bodyText.isEmpty()) bodyText = "（空白记录）";
        List<String> lines = wrap(bodyText, paint, available, 5);
        float lineHeight = dp(22) * scale;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i == lines.size() - 1 && bodyText.length() > joinedLength(lines)) {
                if (line.length() > 1) line = line.substring(0, Math.max(1, line.length() - 1)) + "…";
            }
            canvas.drawText(line, textLeft, bodyTop + i * lineHeight, paint);
        }

        paint.setColor(withAlpha(Color.argb(85, 46, 41, 63), alpha));
        rect.set(textLeft, pivotY + scaledHeight / 2f - dp(34) * scale,
                textLeft + dp(28) * scale, pivotY + scaledHeight / 2f - dp(31) * scale);
        canvas.drawRoundRect(rect, dp(2) * scale, dp(2) * scale, paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(dp(10) * scale);
        paint.setColor(withAlpha(Color.argb(155, 56, 51, 74), alpha));
        canvas.drawText(card.pinned ? "优先显示" : (card.favorite ? "已收藏" : "文字卡片"), textLeft + dp(36) * scale,
                pivotY + scaledHeight / 2f - dp(24) * scale, paint);
    }

    private void drawEmptyState(Canvas canvas, float w, float h) {
        float cx = w / 2f;
        float cy = h / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(232, 226, 250));
        rect.set(cx - dp(49), cy - dp(102), cx + dp(49), cy + dp(102));
        canvas.drawRoundRect(rect, dp(24), dp(24), paint);
        paint.setColor(Color.rgb(249, 246, 255));
        rect.set(cx - dp(40), cy - dp(112), cx + dp(40), cy + dp(91));
        canvas.drawRoundRect(rect, dp(21), dp(21), paint);
        paint.setColor(Color.rgb(197, 185, 239));
        rect.set(cx - dp(26), cy - dp(73), cx + dp(26), cy - dp(68));
        canvas.drawRoundRect(rect, dp(3), dp(3), paint);
        for (int i = 0; i < 4; i++) {
            rect.set(cx - dp(25), cy - dp(40) + i * dp(23), cx + dp(24 - (i % 2) * 8), cy - dp(35) + i * dp(23));
            paint.setColor(Color.rgb(221, 215, 241));
            canvas.drawRoundRect(rect, dp(3), dp(3), paint);
        }
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(dp(16));
        paint.setColor(Color.rgb(63, 58, 92));
        canvas.drawText("这里还没有文字卡片", cx, cy + dp(145), paint);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(dp(12));
        paint.setColor(Color.rgb(132, 126, 155));
        canvas.drawText("粘贴一段文字，开始建立你的卡匣", cx, cy + dp(170), paint);
        paint.setTextSize(dp(10));
        paint.setColor(Color.rgb(157, 151, 177));
        canvas.drawText("卡片将保存在本机", cx, cy + dp(190), paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (cards.isEmpty()) {
            if (event.getAction() == MotionEvent.ACTION_UP && listener != null) listener.onCardTap(null);
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (animator != null) animator.cancel();
                downY = event.getY();
                downX = event.getX();
                downProgress = progress;
                lastProgress = progress;
                dragging = false;
                longPressed = false;
                motionSoundStarted = false;
                if (velocityTracker != null) velocityTracker.recycle();
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(event);
                scheduleLongPress();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (velocityTracker != null) velocityTracker.addMovement(event);
                float dy = event.getY() - downY;
                float dx = event.getX() - downX;
                if (!dragging && (Math.abs(dy) > touchSlop || Math.abs(dx) > touchSlop)) {
                    dragging = Math.abs(dy) > Math.abs(dx) * 0.85f;
                    if (dragging) cancelDeckLongPress();
                }
                if (dragging && !longPressed) {
                    float localStep = step > dp(1) ? step : Math.max(dp(130), getHeight() * 0.34f);
                    progress = clamp(downProgress - dy / localStep, 0f, Math.max(0, cards.size() - 1));
                    if (!motionSoundStarted && Math.abs(dy) > dp(5)) {
                        sound.startRotate();
                        motionSoundStarted = true;
                    }
                    int rounded = Math.round(progress);
                    if (rounded != lastRoundedIndex) {
                        lastRoundedIndex = rounded;
                        if (motionSoundStarted) sound.playTick();
                        if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                    lastProgress = progress;
                    invalidate();
                    if (listener != null) listener.onCardChanged(getSelectedCard());
                }
                return true;
            case MotionEvent.ACTION_UP:
                cancelDeckLongPress();
                if (velocityTracker != null) {
                    velocityTracker.addMovement(event);
                    velocityTracker.computeCurrentVelocity(1000);
                }
                if (dragging && !longPressed) {
                    float velocityY = velocityTracker == null ? 0f : velocityTracker.getYVelocity();
                    float localStep = step > dp(1) ? step : Math.max(dp(130), getHeight() * 0.34f);
                    float projected = progress - (velocityY / localStep) * 0.14f;
                    int target = Math.round(projected);
                    target = Math.max(0, Math.min(cards.size() - 1, target));
                    animateTo(target, true);
                } else if (!longPressed && listener != null) {
                    listener.onCardTap(getSelectedCard());
                }
                recycleVelocity();
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelDeckLongPress();
                if (dragging) animateTo(Math.round(progress), true);
                recycleVelocity();
                return true;
            default: return true;
        }
    }

    private void scheduleLongPress() {
        cancelDeckLongPress();
        longPressRunnable = () -> {
            if (!dragging && !cards.isEmpty()) {
                longPressed = true;
                if (listener != null) listener.onCardLongPress(getSelectedCard());
                if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        };
        handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
    }

    private void cancelDeckLongPress() {
        if (longPressRunnable != null) handler.removeCallbacks(longPressRunnable);
        longPressRunnable = null;
    }

    private void recycleVelocity() {
        if (velocityTracker != null) { velocityTracker.recycle(); velocityTracker = null; }
    }

    private void animateTo(int target, boolean withFeedback) {
        target = Math.max(0, Math.min(cards.size() - 1, target));
        if (animator != null) animator.cancel();
        final float start = progress;
        final float end = target;
        long duration = Math.min(520L, 190L + (long) (Math.abs(end - start) * 72L));
        animator = ValueAnimator.ofFloat(start, end);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator(1.55f));
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            int rounded = Math.round(progress);
            if (rounded != lastRoundedIndex) {
                lastRoundedIndex = rounded;
                if (withFeedback && motionSoundStarted) sound.playTick();
            }
            invalidate();
            if (listener != null) listener.onCardChanged(getSelectedCard());
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean canceled;
            @Override public void onAnimationCancel(android.animation.Animator animation) { canceled = true; }
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (canceled) return;
                progress = end;
                lastRoundedIndex = Math.round(progress);
                sound.stopRotate();
                if (withFeedback && motionSoundStarted) sound.playSnap();
                motionSoundStarted = false;
                invalidate();
                if (listener != null) {
                    listener.onCardChanged(getSelectedCard());
                    listener.onSettled(getSelectedCard());
                }
            }
        });
        animator.start();
    }

    private List<String> wrap(String value, Paint textPaint, float maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        String remaining = value.replace('\r', ' ');
        while (!remaining.isEmpty() && lines.size() < maxLines) {
            int newline = remaining.indexOf('\n');
            String paragraph = newline >= 0 ? remaining.substring(0, newline) : remaining;
            if (paragraph.isEmpty()) {
                lines.add(" ");
                remaining = newline >= 0 ? remaining.substring(newline + 1) : "";
                continue;
            }
            int count = textPaint.breakText(paragraph, true, maxWidth, null);
            if (count <= 0) count = 1;
            if (count < paragraph.length()) {
                int space = paragraph.lastIndexOf(' ', count);
                if (space > Math.max(0, count - 7)) count = space;
                if (count <= 0) count = Math.min(1, paragraph.length());
                lines.add(paragraph.substring(0, count).trim());
                remaining = paragraph.substring(count) + (newline >= 0 ? remaining.substring(newline) : "");
                if (remaining.startsWith(" ")) remaining = remaining.substring(1);
            } else {
                lines.add(paragraph);
                remaining = newline >= 0 ? remaining.substring(newline + 1) : "";
            }
        }
        if (!remaining.isEmpty() && !lines.isEmpty()) {
            String last = lines.get(lines.size() - 1);
            if (last.length() > 1) lines.set(lines.size() - 1, last.substring(0, last.length() - 1) + "…");
            else lines.set(lines.size() - 1, last + "…");
        }
        if (lines.isEmpty()) lines.add(" ");
        return lines;
    }

    private int joinedLength(List<String> lines) {
        int n = 0; for (String line : lines) n += line.length(); return n;
    }

    private int withAlpha(int color, int alpha) { return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color)); }
    private int darken(int color, float factor) { return Color.rgb((int)(Color.red(color)*factor), (int)(Color.green(color)*factor), (int)(Color.blue(color)*factor)); }
    private int lighten(int color, float factor) { return Color.rgb(Math.min(255,(int)(Color.red(color)*factor)), Math.min(255,(int)(Color.green(color)*factor)), Math.min(255,(int)(Color.blue(color)*factor))); }
    private int contrastInk(int background) {
        double luminance = (0.299 * Color.red(background) + 0.587 * Color.green(background) + 0.114 * Color.blue(background)) / 255.0;
        return luminance < 0.52 ? Color.WHITE : Color.rgb(43, 39, 59);
    }
    private float dp(float value) { return value * density; }
    private float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
