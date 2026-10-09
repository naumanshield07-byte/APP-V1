package com.gtinwmsqr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;

public class MetallicTextView extends AppCompatTextView {

    private LinearGradient gradient;

    public MetallicTextView(Context c) {
        super(c); init();
    }

    public MetallicTextView(Context c, AttributeSet a) {
        super(c, a); init();
    }

    public MetallicTextView(Context c, AttributeSet a, int d) {
        super(c, a, d); init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (h <= 0) return;
        // Metallic yellow: bright top -> base -> secondary highlight -> dark gold
        int[] colors = new int[] {
            0xFFFFF7A8,
            0xFFFCFC3D,
            0xFFFFFC85,
            0xFFB8B818
        };
        float[] pos = new float[] { 0f, 0.35f, 0.55f, 1f };
        gradient = new LinearGradient(0, 0, 0, h, colors, pos,
                                      Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (gradient != null) {
            getPaint().setShader(gradient);
        }
        super.onDraw(canvas);
    }
}
