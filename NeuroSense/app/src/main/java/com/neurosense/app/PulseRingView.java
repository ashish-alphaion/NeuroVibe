package com.neurosense.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

public class PulseRingView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean active;
    private int value;

    public PulseRingView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.STROKE);
    }

    public void setState(boolean connected, int value) {
        this.active = connected && value > 0;
        this.value = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float base = Math.min(getWidth(), getHeight()) * 0.34f;
        float phase = active
                ? (SystemClock.uptimeMillis() % 1400L) / 1400f : 0f;
        float strength = Math.max(0.15f, value / 230f);

        for (int i = 0; i < 3; i++) {
            float local = (phase + i / 3f) % 1f;
            float radius = base + local * getWidth() * 0.14f;
            int alpha = active
                    ? (int) ((1f - local) * 75 * strength)
                    : 28 - i * 6;
            paint.setColor(Color.argb(Math.max(0, alpha), 124, 106, 230));
            paint.setStrokeWidth(dp(2));
            canvas.drawCircle(cx, cy, radius, paint);
        }
        if (active) {
            postInvalidateDelayed(32);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
