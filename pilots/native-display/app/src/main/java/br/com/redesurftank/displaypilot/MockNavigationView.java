package br.com.redesurftank.displaypilot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Original synthetic drawing. No map tiles, third-party assets, location or animation. */
public final class MockNavigationView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();

    public MockNavigationView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        setContentDescription("SIMULAÇÃO: virar à direita em 300 metros; sem Waze ou navegação real.");
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth() / 360f, getHeight() / 200f);
        if (scale <= 0) return;
        canvas.save();
        canvas.translate((getWidth() - 360 * scale) / 2, (getHeight() - 200 * scale) / 2);
        canvas.scale(scale, scale);
        paint.setColor(Color.rgb(8, 23, 35));
        canvas.drawRoundRect(2, 2, 358, 198, 14, 14, paint);
        paint.setColor(Color.rgb(255, 208, 89));
        paint.setTextSize(22);
        paint.setFakeBoldText(true);
        canvas.drawText("SIMULAÇÃO · SEM WAZE", 16, 34, paint);
        paint.setColor(Color.rgb(76, 218, 198));
        paint.setStrokeWidth(10);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        arrow.reset();
        arrow.moveTo(42, 132); arrow.lineTo(42, 84); arrow.lineTo(112, 84);
        arrow.moveTo(88, 62); arrow.lineTo(112, 84); arrow.lineTo(88, 106);
        canvas.drawPath(arrow, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(38);
        canvas.drawText("300 m", 145, 96, paint);
        paint.setTextSize(23);
        canvas.drawText("à direita", 145, 127, paint);
        paint.setFakeBoldText(false);
        paint.setTextSize(19);
        canvas.drawText("Exemplo: 6,2 km · chegada 18:30", 16, 172, paint);
        canvas.restore();
    }
}
