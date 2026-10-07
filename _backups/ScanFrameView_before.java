package com.gtinwmsqr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class ScanFrameView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    public ScanFrameView(Context c, AttributeSet a){ super(c,a); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(4)); p.setStrokeCap(Paint.Cap.SQUARE); }
    private float dp(float v){ return v * getResources().getDisplayMetrics().density; }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        p.setColor(0xFF42A5F5);
        float w=getWidth(), h=getHeight();
        float boxW=Math.min(w*0.78f, dp(310));
        float boxH=Math.min(h*0.48f, dp(310));
        float l=(w-boxW)/2f, t=(h-boxH)/2f;
        float len=dp(42);
        // four yellow corner brackets, no full rectangle
        c.drawLine(l,t,l+len,t,p); c.drawLine(l,t,l,t+len,p);
        c.drawLine(l+boxW,t,l+boxW-len,t,p); c.drawLine(l+boxW,t,l+boxW,t+len,p);
        c.drawLine(l,h-(h-t-boxH),l+len,h-(h-t-boxH),p); c.drawLine(l,h-(h-t-boxH),l,h-(h-t-boxH)-len,p);
        float b=t+boxH;
        c.drawLine(l,b,l+len,b,p); c.drawLine(l,b,l,b-len,p);
        // subtle center scan line
        p.setStrokeWidth(dp(2)); p.setColor(0xAA42A5F5);
        c.drawLine(l+dp(12), t+boxH/2f, l+boxW-dp(12), t+boxH/2f, p);
    }
}
