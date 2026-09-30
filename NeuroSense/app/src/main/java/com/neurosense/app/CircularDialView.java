package com.neurosense.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Locale;

/** Native, accessible iOS-inspired circular control for the 0–230 command range. */
public class CircularDialView extends View {
    public interface Listener {
        void onValueChanged(int value, boolean fromUser);
        void onValueCommitted(int value);
    }

    private static final int MAX_VALUE = 230;
    private static final int PRIMARY = Color.rgb(103, 85, 217);
    private static final int TRACK = Color.rgb(232, 231, 240);
    private static final int TEXT = Color.rgb(28, 27, 43);
    private static final int TEXT_MUTED = Color.rgb(99, 96, 116);

    private final Paint surfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();
    private final RectF chipBounds = new RectF();

    private Listener listener;
    private int value;
    private int lastHapticBucket = -1;
    private boolean dragging;

    public CircularDialView(Context context) {
        super(context);
        initialize();
    }

    public CircularDialView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    private void initialize() {
        setFocusable(true);
        setClickable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        surfacePaint.setColor(Color.WHITE);
        surfacePaint.setShadowLayer(dp(18), 0, dp(8), Color.argb(22, 20, 16, 50));
        trackPaint.setColor(TRACK);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeWidth(dp(8));
        progressPaint.setColor(PRIMARY);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(dp(9));
        thumbPaint.setColor(Color.WHITE);
        thumbPaint.setShadowLayer(dp(7), 0, dp(2), Color.argb(65, 20, 16, 50));
        thumbDotPaint.setColor(PRIMARY);

        valuePaint.setColor(TEXT);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTextSize(dp(52));
        valuePaint.setTypeface(android.graphics.Typeface.create(
                "sans-serif", android.graphics.Typeface.BOLD));
        labelPaint.setColor(TEXT_MUTED);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(13));
        labelPaint.setTypeface(android.graphics.Typeface.create(
                "sans-serif", android.graphics.Typeface.BOLD));
        chipPaint.setColor(Color.rgb(241, 238, 255));
        chipTextPaint.setColor(PRIMARY);
        chipTextPaint.setTextAlign(Paint.Align.CENTER);
        chipTextPaint.setTextSize(dp(13));
        chipTextPaint.setTypeface(android.graphics.Typeface.create(
                "monospace", android.graphics.Typeface.BOLD));
        updateAccessibility();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
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
        updateAccessibility();
        if (listener != null) {
            listener.onValueChanged(value, fromUser);
        }
        invalidate();
    }

    private void updateAccessibility() {
        int percent = Math.round(value * 100f / MAX_VALUE);
        setContentDescription(String.format(Locale.US,
                "NeuroVibe level, %d hertz, %d percent", value, percent));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setStateDescription(String.format(Locale.US,
                    "%d hertz, %d percent", value, percent));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float outerRadius = Math.min(getWidth(), getHeight()) / 2f - dp(18);
        canvas.drawCircle(cx, cy, outerRadius, surfacePaint);

        float ringRadius = outerRadius - dp(18);
        arcBounds.set(cx - ringRadius, cy - ringRadius,
                cx + ringRadius, cy + ringRadius);
        trackPaint.setAlpha(isEnabled() ? 255 : 150);
        progressPaint.setAlpha(isEnabled() ? 255 : 95);
        canvas.drawArc(arcBounds, -90f, 360f, false, trackPaint);
        float fraction = value / (float) MAX_VALUE;
        if (value > 0) {
            canvas.drawArc(arcBounds, -90f, fraction * 360f,
                    false, progressPaint);
        }

        float angle = (float) Math.toRadians(-90f + fraction * 360f);
        float thumbX = cx + (float) Math.cos(angle) * ringRadius;
        float thumbY = cy + (float) Math.sin(angle) * ringRadius;
        canvas.drawCircle(thumbX, thumbY, dp(14), thumbPaint);
        canvas.drawCircle(thumbX, thumbY, dp(5), thumbDotPaint);

        int percent = Math.round(value * 100f / MAX_VALUE);
        Paint.FontMetrics valueMetrics = valuePaint.getFontMetrics();
        float valueBaseline = cy - dp(16) -
                (valueMetrics.ascent + valueMetrics.descent) / 2f;
        canvas.drawText(percent + "%", cx, valueBaseline, valuePaint);
        canvas.drawText(value > 0 ? "INTENSITY" : "READY",
                cx, cy + dp(24), labelPaint);

        chipBounds.set(cx - dp(48), cy + dp(42),
                cx + dp(48), cy + dp(72));
        canvas.drawRoundRect(chipBounds, dp(9), dp(9), chipPaint);
        Paint.FontMetrics chipMetrics = chipTextPaint.getFontMetrics();
        float chipBaseline = chipBounds.centerY() -
                (chipMetrics.ascent + chipMetrics.descent) / 2f;
        canvas.drawText(value + " Hz", cx, chipBaseline, chipTextPaint);
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
                updateFromTouch(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    updateFromTouch(event.getX(), event.getY());
                }
                return true;
            case MotionEvent.ACTION_UP:
                updateFromTouch(event.getX(), event.getY());
                dragging = false;
                performClick();
                performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        ? HapticFeedbackConstants.CONFIRM
                        : HapticFeedbackConstants.CLOCK_TICK);
                if (listener != null) {
                    listener.onValueCommitted(value);
                }
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void updateFromTouch(float x, float y) {
        float dx = x - getWidth() / 2f;
        float dy = y - getHeight() / 2f;
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx)) + 90f;
        if (angle < 0f) {
            angle += 360f;
        }
        int next = Math.round(angle / 360f * MAX_VALUE);
        int bucket = next / 10;
        if (next == 0 || next == MAX_VALUE) {
            if (next != value) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        } else if (bucket != lastHapticBucket) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        lastHapticBucket = bucket;
        setValueInternal(next, true);
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
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
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
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
                0, MAX_VALUE, value));
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
            int delta = action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1 : -1;
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
