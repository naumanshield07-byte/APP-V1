package com.gtinwmsqr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class ScanFrameView extends View {

    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ScanFrameView(Context c, AttributeSet a){
        super(c, a);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(dp(4));
        frame.setStrokeCap(Paint.Cap.SQUARE);
        overlay.setStyle(Paint.Style.FILL);
        overlay.setColor(0x730A0B10); /* 45% opacity dark grey */
    }

    private float dp(float v){
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas c){
        super.onDraw(c);

        float w = getWidth();
        float h = getHeight();
        float boxW = Math.min(w * 0.78f, dp(310));
        float boxH = Math.min(h * 0.48f, dp(310));
        float l = (w - boxW) / 2f;
        float t = (h - boxH) / 2f;
        float r = l + boxW;
        float b = t + boxH;

        /* Grey overlay everywhere EXCEPT the scan rectangle */
        c.drawRect(0, 0, w, t, overlay);
        c.drawRect(0, b, w, h, overlay);
        c.drawRect(0, t, l, b, overlay);
        c.drawRect(r, t, w, b, overlay);

        float len = dp(42);

        /* Top-left */
        frame.setColor(0xFF06B6D4);
        frame.setStrokeWidth(dp(4));
        c.drawLine(l, t, l + len, t, frame);
        c.drawLine(l, t, l, t + len, frame);

        /* Top-right */
        c.drawLine(r - len, t, r, t, frame);
        c.drawLine(r, t, r, t + len, frame);

        /* Bottom-left */
        c.drawLine(l, b - len, l, b, frame);
        c.drawLine(l, b, l + len, b, frame);

        /* Bottom-right */
        c.drawLine(r - len, b, r, b, frame);
        c.drawLine(r, b - len, r, b, frame);

        /* Center scan line */
        frame.setStrokeWidth(dp(2));
        frame.setColor(0xAA22B8E6);
        c.drawLine(l + dp(12), t + boxH / 2f, r - dp(12), t + boxH / 2f, frame);
    }
}
