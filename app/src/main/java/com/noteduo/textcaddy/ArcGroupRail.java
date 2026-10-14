package com.noteduo.textcaddy;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

/** Compact, touch-driven circular group rail. Group labels stay level and readable. */
public final class ArcGroupRail extends View {
    public interface Listener {
        void onGroupSelected(String groupId);
        void onOpenGroupList();
        void onAddGroup();
    }

    public static final class Tab {
        public final String id;
        public final String shortLabel;
        public final String name;
        public final int color;
        public final int count;

        public Tab(String id, String shortLabel, String name, int color, int count) {
            this.id = id;
            this.shortLabel = shortLabel == null || shortLabel.trim().isEmpty() ? "组" : shortLabel.trim();
            this.name = name == null ? "未命名组别" : name;
            this.color = color;
            this.count = Math.max(0, count);
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path panelPath = new Path();
    private final Path guidePath = new Path();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float density;
    private final int touchSlop;
    private final SoundEngine sound;
    private final List<Tab> tabs = new ArrayList<>();
    private Listener listener;
    private ValueAnimator highlightAnimator;
    private Runnable longPressRunnable;
    private int selectedIndex;
    private int targetIndex;
    private int visibleStart;
    private int visibleCount;
    private float visualIndex;
    private float downX;
    private float downY;
    private boolean dragging;
    private boolean longPressed;
    private boolean downOnAdd;
    private boolean railSoundStarted;
    private boolean hapticsEnabled;

    public ArcGroupRail(Context context, SoundEngine sound, Listener listener) {
        super(context);
        this.sound = sound;
        this.listener = listener;
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setFocusable(true);
        setClickable(true);
        setContentDescription("彩色圆弧组别转盘；滑动选择组别，长按打开完整列表，底部加号新建组别");
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void setHapticsEnabled(boolean enabled) { hapticsEnabled = enabled; }

    public void setTabs(List<Tab> next, String selectedId) {
        int previousIndex = selectedIndex;
        int previousStart = visibleStart;
        String previousId = previousIndex >= 0 && previousIndex < tabs.size() ? tabs.get(previousIndex).id : null;
        float previousVisualIndex = visualIndex;
        tabs.clear();
        if (next != null) tabs.addAll(next);
        int nextIndex = indexOf(selectedId);
        visibleCount = Math.min(9, tabs.size());
        visibleStart = windowStartFor(nextIndex, tabs.size(), visibleCount);
        boolean sameSelectedTab = selectedId != null && selectedId.equals(previousId);
        if (sameSelectedTab && previousIndex == nextIndex && previousStart == visibleStart) {
            // Keep an in-flight highlight animation alive when the selected group is re-rendered.
            selectedIndex = nextIndex;
            targetIndex = nextIndex;
        } else {
            selectedIndex = nextIndex;
            targetIndex = nextIndex;
            if (highlightAnimator != null) highlightAnimator.cancel();
            if (previousStart != visibleStart) {
                // Recenter the visible window only when a group list is longer than the rail can show.
                visualIndex = selectedIndex;
            } else if (previousIndex >= 0 && previousIndex < tabs.size() && previousIndex != selectedIndex) {
                visualIndex = previousVisualIndex;
                animateHighlightTo(selectedIndex, 130L);
            } else {
                visualIndex = selectedIndex;
            }
        }
        invalidate();
        StringBuilder description = new StringBuilder("组别：");
        for (int i = 0; i < Math.min(tabs.size(), 10); i++) {
            if (i > 0) description.append("、");
            description.append(tabs.get(i).name);
        }
        if (tabs.size() > 10) description.append("等").append(tabs.size()).append("个组别");
        setContentDescription(description.toString());
    }

    private int indexOf(String id) {
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).id.equals(id)) return i;
        return tabs.isEmpty() ? -1 : 0;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        drawCurvedPanel(canvas, w, h);
        if (tabs.isEmpty()) {
            drawAddButton(canvas, w, h);
            return;
        }

        RailGeometry geometry = geometry(w, h);
        // The faint curved guide makes the icon path read as part of a large circular dial.
        guidePath.reset();
        int visibleEnd = Math.min(tabs.size(), visibleStart + visibleCount);
        for (int i = visibleStart; i < visibleEnd; i++) {
            Point point = pointFor(i, geometry);
            if (i == visibleStart) guidePath.moveTo(point.x, point.y);
            else guidePath.lineTo(point.x, point.y);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.argb(62, 111, 101, 183));
        canvas.drawPath(guidePath, paint);
        paint.setStyle(Paint.Style.FILL);

        Point activePoint = pointForProgress(visualIndex, geometry);
        float selectedRadius = geometry.iconRadius;
        paint.setColor(Color.argb(28, 111, 94, 222));
        canvas.drawCircle(activePoint.x, activePoint.y, selectedRadius + dp(9), paint);
        paint.setColor(Color.argb(17, 111, 94, 222));
        canvas.drawCircle(activePoint.x, activePoint.y, selectedRadius + dp(14), paint);

        for (int i = visibleStart; i < visibleEnd; i++) {
            Tab tab = tabs.get(i);
            Point point = pointFor(i, geometry);
            float closeness = Math.max(0f, 1f - Math.min(1f, Math.abs(visualIndex - i)));
            float radius = geometry.iconRadius * (1f + 0.13f * closeness);
            boolean committed = i == selectedIndex && !dragging;
            int alpha = 255;

            paint.setColor(Color.argb((int) (17 + 20 * closeness), 79, 69, 139));
            canvas.drawCircle(point.x, point.y + dp(1.5f), radius + dp(1.2f), paint);
            paint.setColor(withAlpha(tab.color, alpha));
            canvas.drawCircle(point.x, point.y, radius, paint);

            if (closeness > 0.20f || committed) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.25f + closeness * 0.8f));
                paint.setColor(Color.argb((int) (135 + 110 * closeness), 255, 255, 255));
                canvas.drawCircle(point.x, point.y, radius - dp(0.5f), paint);
                paint.setStyle(Paint.Style.FILL);
            } else {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(0.8f));
                paint.setColor(Color.argb(62, 68, 63, 103));
                canvas.drawCircle(point.x, point.y, radius, paint);
                paint.setStyle(Paint.Style.FILL);
            }

            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(Math.min(13f, Math.max(10.5f, radius / density * 0.78f))));
            paint.setColor(contrastInk(tab.color));
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float baseline = point.y - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(tab.shortLabel, point.x, baseline, paint);
        }
        drawAddButton(canvas, w, h);
    }

    private void drawCurvedPanel(Canvas canvas, float w, float h) {
        panelPath.reset();
        panelPath.moveTo(0, 0);
        panelPath.lineTo(w * 0.40f, 0);
        panelPath.cubicTo(w * 0.50f, h * 0.18f, w * 0.96f, h * 0.38f, w * 0.94f, h * 0.61f);
        panelPath.cubicTo(w * 0.93f, h * 0.81f, w * 0.48f, h * 0.93f, w * 0.19f, h);
        panelPath.lineTo(0, h);
        panelPath.close();
        paint.setShader(new LinearGradient(0, 0, w, h,
                new int[]{Color.rgb(245, 242, 255), Color.rgb(226, 237, 255), Color.rgb(246, 232, 255)},
                null, Shader.TileMode.CLAMP));
        canvas.drawPath(panelPath, paint);
        paint.setShader(null);

        Path edge = new Path();
        edge.moveTo(w * 0.40f, 0);
        edge.cubicTo(w * 0.50f, h * 0.18f, w * 0.96f, h * 0.38f, w * 0.94f, h * 0.61f);
        edge.cubicTo(w * 0.93f, h * 0.81f, w * 0.48f, h * 0.93f, w * 0.19f, h);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(Color.argb(112, 255, 255, 255));
        canvas.drawPath(edge, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawAddButton(Canvas canvas, float w, float h) {
        float cx = w * 0.49f;
        float cy = h - dp(25);
        paint.setColor(Color.argb(24, 83, 71, 170));
        canvas.drawCircle(cx, cy + dp(1.5f), dp(16.5f), paint);
        paint.setColor(Color.rgb(238, 234, 254));
        canvas.drawCircle(cx, cy, dp(15), paint);
        paint.setColor(Color.rgb(109, 99, 203));
        paint.setStrokeWidth(dp(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(cx - dp(5), cy, cx + dp(5), cy, paint);
        canvas.drawLine(cx, cy - dp(5), cx, cy + dp(5), paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private RailGeometry geometry(float w, float h) {
        int count = visibleCount;
        float maxSpan = Math.max(dp(100), Math.min(h * 0.43f, h - dp(150)));
        float spacing = count <= 1 ? dp(42) : Math.min(dp(43), maxSpan / (count - 1));
        spacing = Math.max(dp(25), spacing);
        float halfSpan = (count - 1) * spacing / 2f;
        float centerY = clamp(h * 0.64f, halfSpan + dp(34), h - dp(57) - halfSpan);
        float iconRadius = Math.min(dp(16), spacing * 0.39f);
        iconRadius = Math.max(dp(10.5f), iconRadius);
        return new RailGeometry(centerY, spacing, halfSpan, w - iconRadius - dp(2), iconRadius);
    }

    private int windowStartFor(int selected, int total, int visible) {
        if (visible <= 0 || total <= visible || selected < 0) return 0;
        int start = selected - visible / 2;
        return Math.max(0, Math.min(total - visible, start));
    }

    private Point pointFor(int index, RailGeometry g) {
        int localIndex = index - visibleStart;
        float dy = (localIndex - (visibleCount - 1) / 2f) * g.spacing;
        float y = g.centerY + dy;
        float x = g.maxX;
        if (g.halfSpan > dp(1)) {
            float sagitta = dp(18);
            float radius = (g.halfSpan * g.halfSpan + sagitta * sagitta) / (2f * sagitta);
            float inside = Math.max(0f, radius * radius - dy * dy);
            x = g.maxX - radius + (float) Math.sqrt(inside);
        }
        return new Point(x, y);
    }

    private Point pointForProgress(float position, RailGeometry g) {
        if (visibleCount <= 1) return pointFor(visibleStart, g);
        float bounded = clamp(position, visibleStart, visibleStart + visibleCount - 1f);
        int lower = (int) Math.floor(bounded);
        int upper = Math.min(visibleStart + visibleCount - 1, lower + 1);
        float fraction = bounded - lower;
        Point a = pointFor(lower, g);
        Point b = pointFor(upper, g);
        return new Point(a.x + (b.x - a.x) * fraction, a.y + (b.y - a.y) * fraction);
    }

    private int findNearestTab(float x, float y) {
        if (tabs.isEmpty()) return -1;
        RailGeometry g = geometry(getWidth(), getHeight());
        int nearest = -1;
        float best = Float.MAX_VALUE;
        int end = Math.min(tabs.size(), visibleStart + visibleCount);
        for (int i = visibleStart; i < end; i++) {
            Point p = pointFor(i, g);
            float dx = p.x - x;
            float dy = p.y - y;
            float distance = dx * dx + dy * dy;
            if (distance < best) { best = distance; nearest = i; }
        }
        return nearest;
    }

    private boolean isNearAddButton(float x, float y) {
        float dx = x - getWidth() * 0.49f;
        float dy = y - (getHeight() - dp(25));
        return dx * dx + dy * dy <= dp(24) * dp(24);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                dragging = false;
                longPressed = false;
                downOnAdd = isNearAddButton(downX, downY);
                railSoundStarted = false;
                if (highlightAnimator != null) highlightAnimator.cancel();
                if (!downOnAdd) scheduleLongPress();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!dragging && Math.hypot(dx, dy) > touchSlop) {
                    dragging = true;
                    cancelLongPress();
                    if (!railSoundStarted) {
                        sound.startRotate(true);
                        railSoundStarted = true;
                    }
                }
                if (dragging && !longPressed && !tabs.isEmpty()) {
                    int nearest = findNearestTab(event.getX(), event.getY());
                    if (nearest >= 0 && nearest != targetIndex) {
                        targetIndex = nearest;
                        animateHighlightTo(nearest, 78L);
                        sound.playTick(true);
                        if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                cancelLongPress();
                if (downOnAdd && isNearAddButton(event.getX(), event.getY())) {
                    if (listener != null) listener.onAddGroup();
                } else if (!longPressed) {
                    int candidate = dragging ? targetIndex : findNearestTab(event.getX(), event.getY());
                    if (candidate >= 0 && (!dragging ? isTouchNearTab(event.getX(), event.getY(), candidate) : true)) {
                        boolean changed = candidate != selectedIndex;
                        selectedIndex = candidate;
                        targetIndex = candidate;
                        animateHighlightTo(candidate, 92L);
                        if (dragging) sound.playSnap(true);
                        else if (changed) sound.playSnap(true);
                        else sound.stopRotate();
                        if (changed && listener != null) listener.onGroupSelected(tabs.get(candidate).id);
                        if (changed && hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    } else {
                        sound.stopRotate();
                        animateHighlightTo(selectedIndex, 100L);
                    }
                } else {
                    sound.stopRotate();
                }
                railSoundStarted = false;
                dragging = false;
                downOnAdd = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelLongPress();
                sound.stopRotate();
                railSoundStarted = false;
                dragging = false;
                targetIndex = selectedIndex;
                animateHighlightTo(selectedIndex, 100L);
                downOnAdd = false;
                return true;
            default:
                return true;
        }
    }

    private boolean isTouchNearTab(float x, float y, int index) {
        if (index < 0 || index >= tabs.size()) return false;
        Point point = pointFor(index, geometry(getWidth(), getHeight()));
        float dx = point.x - x;
        float dy = point.y - y;
        return dx * dx + dy * dy <= dp(25) * dp(25);
    }

    private void scheduleLongPress() {
        cancelLongPress();
        longPressRunnable = () -> {
            if (!dragging && !downOnAdd) {
                longPressed = true;
                sound.stopRotate();
                if (listener != null) listener.onOpenGroupList();
                if (hapticsEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        };
        handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
    }

    private void cancelLongPress() {
        if (longPressRunnable != null) handler.removeCallbacks(longPressRunnable);
        longPressRunnable = null;
    }

    private void animateHighlightTo(int index, long duration) {
        if (tabs.isEmpty()) return;
        float end = clamp(index, 0f, tabs.size() - 1f);
        if (highlightAnimator != null) highlightAnimator.cancel();
        highlightAnimator = ValueAnimator.ofFloat(visualIndex, end);
        highlightAnimator.setDuration(duration);
        highlightAnimator.setInterpolator(new DecelerateInterpolator(1.15f));
        highlightAnimator.addUpdateListener(animation -> {
            visualIndex = (float) animation.getAnimatedValue();
            invalidate();
        });
        highlightAnimator.start();
    }

    private int contrastInk(int background) {
        double luminance = (0.299 * Color.red(background) + 0.587 * Color.green(background) + 0.114 * Color.blue(background)) / 255.0;
        return luminance < 0.52 ? Color.WHITE : Color.rgb(43, 39, 59);
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(float value) { return value * density; }
    private float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    @Override protected void onDetachedFromWindow() {
        cancelLongPress();
        if (highlightAnimator != null) highlightAnimator.cancel();
        if (sound != null) sound.stopRotate();
        super.onDetachedFromWindow();
    }

    private static final class RailGeometry {
        final float centerY, spacing, halfSpan, maxX, iconRadius;
        RailGeometry(float centerY, float spacing, float halfSpan, float maxX, float iconRadius) {
            this.centerY = centerY; this.spacing = spacing; this.halfSpan = halfSpan; this.maxX = maxX; this.iconRadius = iconRadius;
        }
    }

    private static final class Point {
        final float x, y;
        Point(float x, float y) { this.x = x; this.y = y; }
    }
}
