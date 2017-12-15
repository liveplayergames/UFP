package com.liveplayergames.finneypoker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by kaandoit on 12/30/16.
 */


public class Circle extends View {

    public static final float START_ANGLE = 360;
    private static final int START_ANGLE_POINT = 270;
    private Paint paint;
    private RectF rect = null;
    final int strokeWidth = 12;
    private float angle;

    public Circle(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        //Circle color
        paint.setColor(context.getResources().getColor(R.color.color_circle_timer));
        //Initial Angle (optional, it can be zero)
        angle = START_ANGLE;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //need to get width here, cuz dimensions might not be set before onDraw
        int width = getWidth();
        int height = getHeight();
        //System.out.println("w = " + getWidth() + "; h = " + getHeight());
        //size 200x200 example
        if (rect == null)
            rect = new RectF(strokeWidth, strokeWidth, width - strokeWidth, height - strokeWidth);
        canvas.drawArc(rect, START_ANGLE_POINT, angle, false, paint);
    }

    public float getAngle() {
        return angle;
    }

    public void setAngle(float angle) {
        this.angle = angle;
    }
}
