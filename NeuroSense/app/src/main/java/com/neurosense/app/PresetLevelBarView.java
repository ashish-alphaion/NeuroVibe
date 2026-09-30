package com.neurosense.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

public class PresetLevelBarView extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private int value;

    public PresetLevelBarView(Context context) {
        super(context);
        trackPaint.setColor(Color.rgb(237, 233, 255));
        markerPaint.setColor(Color.WHITE);
        markerPaint.setShadowLayer(dp(5), 0, dp(2),
                Color.argb(60, 103, 85, 217));
        markerBorderPaint.setStyle(Paint.Style.STROKE);
        markerBorderPaint.setStrokeWidth(dp(2));
        markerBorderPaint.setColor(Color.rgb(103, 85, 217));
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setValue(int value) {
        this.value = Math.max(0, Math.min(230, value));
        setContentDescription(this.value + " hertz, " +
                Math.round(this.value * 100f / 230f) + " percent level");
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float markerRadius = dp(11);
        float centerY = getHeight() / 2f;
        track.set(markerRadius, centerY - dp(5),
                getWidth() - markerRadius, centerY + dp(5));
        canvas.drawRoundRect(track, dp(5), dp(5), trackPaint);

        float fraction = value / 230f;
        float markerX = track.left + track.width() * fraction;
        fillPaint.setShader(new LinearGradient(
                track.left, 0, track.right, 0,
                Color.rgb(124, 106, 230),
                Color.rgb(103, 85, 217),
                Shader.TileMode.CLAMP));
        RectF fill = new RectF(track.left, track.top,
                Math.max(track.left + dp(2), markerX), track.bottom);
        canvas.drawRoundRect(fill, dp(5), dp(5), fillPaint);
        canvas.drawCircle(markerX, centerY, markerRadius, markerPaint);
        canvas.drawCircle(markerX, centerY, markerRadius, markerBorderPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
