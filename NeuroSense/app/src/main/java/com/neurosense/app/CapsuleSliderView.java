package com.neurosense.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Locale;

public class CapsuleSliderView extends View {
    public interface Listener {
        void onValueChanged(int value, boolean fromUser);
        void onValueCommitted(int value);
    }

    private static final int MAX_VALUE = 230;
    private static final int TRACK = Color.rgb(237, 233, 255);
    private static final int TRACK_DISABLED = Color.rgb(231, 229, 236);
    private static final int LAVENDER = Color.rgb(124, 106, 230);
    private static final int PURPLE = Color.rgb(103, 85, 217);
    private static final int TEXT = Color.rgb(28, 27, 43);

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulsePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF bubbleRect = new RectF();
    private final Path trackClipPath = new Path();

    private Listener listener;
    private int value;
    private int lastHapticBucket = -1;
    private boolean vertical;
    private boolean dragging;
    private long capVisibleUntil;
    private long confirmationUntil;

    public CapsuleSliderView(Context context) {
        super(context);
        initialize();
    }

    public CapsuleSliderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    private void initialize() {
        setFocusable(true);
        setClickable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("NeuroVibe level slider");

        trackPaint.setColor(TRACK);
        capPaint.setColor(Color.argb(225, 255, 255, 255));
        bubblePaint.setColor(TEXT);
        bubbleTextPaint.setColor(Color.WHITE);
        bubbleTextPaint.setTextAlign(Paint.Align.CENTER);
        bubbleTextPaint.setTextSize(dp(17));
        bubbleTextPaint.setTypeface(android.graphics.Typeface.create(
                "monospace", android.graphics.Typeface.BOLD));
        pulsePaint.setStyle(Paint.Style.STROKE);
        pulsePaint.setStrokeWidth(dp(3));
        pulsePaint.setColor(Color.argb(130, 132, 105, 225));
        thumbPaint.setColor(Color.WHITE);
        thumbPaint.setShadowLayer(dp(7), 0, dp(2),
                Color.argb(75, 41, 37, 53));
        thumbBorderPaint.setStyle(Paint.Style.STROKE);
        thumbBorderPaint.setStrokeWidth(dp(2));
        thumbBorderPaint.setColor(PURPLE);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setVertical(boolean vertical) {
        this.vertical = vertical;
        requestLayout();
        invalidate();
    }

    public boolean isVertical() {
        return vertical;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int newValue) {
        setValueInternal(newValue, false);
    }

    private void setValueInternal(int newValue, boolean fromUser) {
        int constrained = Math.max(0, Math.min(MAX_VALUE, newValue));
        if (constrained == value) {
            return;
        }
        value = constrained;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setStateDescription(value + " hertz, " +
                    Math.round(value * 100f / MAX_VALUE) + " percent");
        }
        if (listener != null) {
            listener.onValueChanged(value, fromUser);
        }
        sendAccessibilityEvent(
                android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = dp(7);
        if (vertical) {
            float trackWidth = Math.min(dp(70), getWidth() * 0.48f);
            trackRect.set(getWidth() - inset - trackWidth, inset,
                    getWidth() - inset, getHeight() - inset);
        } else {
            float sideInset = dp(17);
            trackRect.set(sideInset, inset,
                    getWidth() - sideInset, getHeight() - inset);
        }
        float radius = Math.min(trackRect.width(), trackRect.height()) / 2f;

        trackPaint.setColor(isEnabled() ? TRACK : TRACK_DISABLED);
        trackPaint.clearShadowLayer();
        if (isEnabled() && value > 0) {
            trackPaint.setShadowLayer(dp(12), 0, dp(3),
                    Color.argb(45, 132, 105, 225));
        }
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);

        float fraction = value / (float) MAX_VALUE;
        if (isEnabled() && fraction > 0f) {
            fillPaint.setShader(vertical
                    ? new LinearGradient(0, trackRect.bottom, 0, trackRect.top,
                    LAVENDER, PURPLE, Shader.TileMode.CLAMP)
                    : new LinearGradient(trackRect.left, 0, trackRect.right, 0,
                    LAVENDER, PURPLE, Shader.TileMode.CLAMP));

            int save = canvas.save();
            trackClipPath.reset();
            trackClipPath.addRoundRect(
                    trackRect, radius, radius, Path.Direction.CW);
            canvas.clipPath(trackClipPath);
            RectF fill = vertical
                    ? new RectF(trackRect.left,
                    trackRect.bottom - trackRect.height() * fraction,
                    trackRect.right, trackRect.bottom)
                    : new RectF(trackRect.left, trackRect.top,
                    trackRect.left + trackRect.width() * fraction,
                    trackRect.bottom);
            canvas.drawRect(fill, fillPaint);
            canvas.restoreToCount(save);

            float edge = vertical
                    ? trackRect.bottom - trackRect.height() * fraction
                    : trackRect.left + trackRect.width() * fraction;
            if (dragging || SystemClock.uptimeMillis() < capVisibleUntil) {
                if (vertical) {
                    canvas.drawRoundRect(
                            trackRect.left + dp(4), edge - dp(2),
                            trackRect.right - dp(4), edge + dp(2),
                            dp(2), dp(2), capPaint);
                } else {
                    canvas.drawRoundRect(
                            edge - dp(2), trackRect.top + dp(4),
                            edge + dp(2), trackRect.bottom - dp(4),
                            dp(2), dp(2), capPaint);
                }
            }
        }

        float thumbX = vertical ? trackRect.centerX()
                : trackRect.left + trackRect.width() * fraction;
        float thumbY = vertical
                ? trackRect.bottom - trackRect.height() * fraction
                : trackRect.centerY();
        float thumbRadius = dp(14);
        thumbPaint.setColor(isEnabled() ? Color.WHITE
                : Color.rgb(244, 241, 246));
        thumbBorderPaint.setColor(isEnabled() ? PURPLE
                : Color.rgb(180, 173, 188));
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint);
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbBorderPaint);

        long now = SystemClock.uptimeMillis();
        if (now < confirmationUntil) {
            float progress = 1f -
                    (confirmationUntil - now) / 150f;
            pulsePaint.setAlpha((int) (150 * (1f - progress)));
            canvas.drawRoundRect(
                    trackRect.left - dp(2) * progress,
                    trackRect.top - dp(2) * progress,
                    trackRect.right + dp(2) * progress,
                    trackRect.bottom + dp(2) * progress,
                    radius, radius, pulsePaint);
            postInvalidateOnAnimation();
        }

        if (dragging) {
            drawValueBubble(canvas, fraction);
        } else if (now < capVisibleUntil) {
            postInvalidateOnAnimation();
        }
    }

    private void drawValueBubble(Canvas canvas, float fraction) {
        String label = String.format(Locale.US, "%d Hz", value);
        float width = dp(78);
        float height = dp(42);

        if (vertical) {
            float y = trackRect.bottom - trackRect.height() * fraction;
            float left = Math.max(0, trackRect.left - width - dp(10));
            float top = Math.max(0, Math.min(getHeight() - height, y - height / 2f));
            bubbleRect.set(left, top, left + width, top + height);
        } else {
            float x = trackRect.left + trackRect.width() * fraction;
            float left = Math.max(0, Math.min(getWidth() - width, x - width / 2f));
            bubbleRect.set(left, 0, left + width, height);
        }
        canvas.drawRoundRect(bubbleRect, dp(14), dp(14), bubblePaint);
        Paint.FontMetrics metrics = bubbleTextPaint.getFontMetrics();
        float baseline = bubbleRect.centerY() -
                (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(label, bubbleRect.centerX(), baseline, bubbleTextPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                dragging = true;
                lastHapticBucket = value / 10;
                updateFromTouch(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                updateFromTouch(event);
                return true;
            case MotionEvent.ACTION_UP:
                updateFromTouch(event);
                dragging = false;
                capVisibleUntil = SystemClock.uptimeMillis() + 200;
                confirmationUntil = SystemClock.uptimeMillis() + 150;
                performClick();
                performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        ? HapticFeedbackConstants.CONFIRM
                        : HapticFeedbackConstants.CLOCK_TICK);
                if (listener != null) {
                    listener.onValueCommitted(value);
                }
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void updateFromTouch(MotionEvent event) {
        float fraction;
        if (vertical) {
            fraction = (trackRect.bottom - event.getY()) /
                    Math.max(1f, trackRect.height());
        } else {
            fraction = (event.getX() - trackRect.left) /
                    Math.max(1f, trackRect.width());
        }
        int next = Math.round(Math.max(0f, Math.min(1f, fraction)) * MAX_VALUE);
        if (next != value) {
            int bucket = next / 10;
            if (next == 0 || next == MAX_VALUE) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            } else if (bucket != lastHapticBucket) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            lastHapticBucket = bucket;
            setValueInternal(next, true);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isEnabled()) {
            return super.onKeyDown(keyCode, event);
        }
        int delta;
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            delta = event.isShiftPressed() ? 10 : 1;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            delta = event.isShiftPressed() ? -10 : -1;
        } else {
            return super.onKeyDown(keyCode, event);
        }
        setValueInternal(value + delta, true);
        if (listener != null) {
            listener.onValueCommitted(value);
        }
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        return true;
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return android.widget.SeekBar.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(android.widget.SeekBar.class.getName());
        info.setContentDescription("NeuroVibe level, " + value + " hertz");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                    AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
                    0, MAX_VALUE, value));
        }
        if (isEnabled()) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, android.os.Bundle args) {
        if (isEnabled() &&
                (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ||
                        action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            int delta = action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    ? 1 : -1;
            setValueInternal(value + delta, true);
            if (listener != null) {
                listener.onValueCommitted(value);
            }
            return true;
        }
        return super.performAccessibilityAction(action, args);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
