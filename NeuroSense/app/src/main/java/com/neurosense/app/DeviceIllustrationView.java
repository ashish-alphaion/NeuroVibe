package com.neurosense.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Resolution-independent NeuroVibe hardware illustration.
 * Drawn entirely with native canvas primitives; no raster image asset is used.
 */
public class DeviceIllustrationView extends View {
    private static final int PRIMARY = Color.rgb(103, 85, 217);
    private static final int INK = Color.rgb(46, 44, 60);
    private static final int CABLE = Color.rgb(174, 170, 186);

    private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint devicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cablePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint connectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint motorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint motorRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF controller = new RectF();
    private final Path leftCable = new Path();
    private final Path rightCable = new Path();

    public DeviceIllustrationView(Context context) {
        super(context);
        initialize();
    }

    public DeviceIllustrationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    private void initialize() {
        setContentDescription("NeuroVibe device illustration");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        auraPaint.setColor(Color.rgb(242, 238, 255));
        devicePaint.setColor(Color.WHITE);
        devicePaint.setShadowLayer(dp(18), 0, dp(9), Color.argb(35, 60, 43, 110));
        edgePaint.setColor(Color.rgb(225, 222, 233));
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(dp(1));
        cablePaint.setColor(CABLE);
        cablePaint.setStyle(Paint.Style.STROKE);
        cablePaint.setStrokeWidth(dp(3));
        cablePaint.setStrokeCap(Paint.Cap.ROUND);
        connectorPaint.setColor(PRIMARY);
        connectorPaint.setStyle(Paint.Style.STROKE);
        connectorPaint.setStrokeWidth(dp(3));
        connectorPaint.setStrokeCap(Paint.Cap.ROUND);
        motorPaint.setColor(Color.rgb(249, 249, 252));
        motorPaint.setShadowLayer(dp(7), 0, dp(3), Color.argb(45, 35, 31, 54));
        motorRingPaint.setColor(Color.rgb(194, 190, 205));
        motorRingPaint.setStyle(Paint.Style.STROKE);
        motorRingPaint.setStrokeWidth(dp(2));
        labelPaint.setColor(INK);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(10));
        labelPaint.setTypeface(android.graphics.Typeface.create(
                "sans-serif", android.graphics.Typeface.BOLD));
        ledPaint.setColor(PRIMARY);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float cx = width / 2f;

        canvas.drawCircle(cx, height * 0.42f,
                Math.min(width, height) * 0.36f, auraPaint);

        float controllerWidth = Math.min(width * 0.34f, dp(150));
        float controllerHeight = controllerWidth * 0.62f;
        float controllerTop = height * 0.16f;
        controller.set(cx - controllerWidth / 2f, controllerTop,
                cx + controllerWidth / 2f, controllerTop + controllerHeight);

        float leftPortX = cx - controllerWidth * 0.24f;
        float rightPortX = cx + controllerWidth * 0.24f;
        float portY = controller.bottom - dp(1);
        float leftMotorX = cx - Math.min(width * 0.25f, dp(112));
        float rightMotorX = cx + Math.min(width * 0.25f, dp(112));
        float motorY = height * 0.76f;

        leftCable.reset();
        leftCable.moveTo(leftPortX, portY);
        leftCable.cubicTo(leftPortX - dp(8), height * 0.50f,
                leftMotorX - dp(12), height * 0.52f,
                leftMotorX, motorY - dp(18));
        rightCable.reset();
        rightCable.moveTo(rightPortX, portY);
        rightCable.cubicTo(rightPortX + dp(8), height * 0.50f,
                rightMotorX + dp(12), height * 0.52f,
                rightMotorX, motorY - dp(18));
        canvas.drawPath(leftCable, cablePaint);
        canvas.drawPath(rightCable, cablePaint);
        canvas.drawLine(leftPortX, portY, leftPortX, portY + dp(10), connectorPaint);
        canvas.drawLine(rightPortX, portY, rightPortX, portY + dp(10), connectorPaint);

        canvas.drawRoundRect(controller, dp(22), dp(22), devicePaint);
        canvas.drawRoundRect(controller, dp(22), dp(22), edgePaint);
        canvas.drawText("NEUROVIBE", cx, controller.centerY() + dp(4), labelPaint);
        canvas.drawRoundRect(cx - dp(16), controller.bottom - dp(15),
                cx + dp(16), controller.bottom - dp(12),
                dp(2), dp(2), ledPaint);

        drawMotor(canvas, leftMotorX, motorY);
        drawMotor(canvas, rightMotorX, motorY);
    }

    private void drawMotor(Canvas canvas, float x, float y) {
        canvas.drawCircle(x, y, dp(19), motorPaint);
        canvas.drawCircle(x, y, dp(19), motorRingPaint);
        canvas.drawCircle(x, y, dp(11), edgePaint);
        canvas.drawCircle(x, y, dp(4), ledPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
