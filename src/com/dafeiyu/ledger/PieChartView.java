package com.dafeiyu.ledger;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 2D 饼状图：分类名称 + 占比直接画在扇形内部；
 * 角度太小、内部放不下的扇区，自动用引线标注到外侧。
 */
public class PieChartView extends View {

    public static class Slice {
        public final String label;
        public final double value;
        public final int color;

        public Slice(String label, double value, int color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }
    }

    /** 小于这个角度就放不下内部文字，改为外置引线标注。 */
    private static final float MIN_INSIDE_DEG = 17f;
    /** 大于这个角度才排得下「名称 + 百分比」两行。 */
    private static final float TWO_LINE_DEG = 36f;

    private final List<Slice> slices = new ArrayList<>();

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gap = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pctPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private String emptyText = "还没有数据";
    private int labelColor = 0xFF8A96A8;

    public PieChartView(Context ctx) {
        super(ctx);
        init();
    }

    public PieChartView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        init();
    }

    public PieChartView(Context ctx, AttributeSet attrs, int defStyle) {
        super(ctx, attrs, defStyle);
        init();
    }

    private void init() {
        gap.setStyle(Paint.Style.STROKE);
        gap.setColor(0xFFFFFFFF);

        namePaint.setTextAlign(Paint.Align.CENTER);
        namePaint.setFakeBoldText(true);
        namePaint.setColor(0xFFFFFFFF);
        namePaint.setShadowLayer(dp(2f), 0f, dp(1f), 0x59000000);

        pctPaint.setTextAlign(Paint.Align.CENTER);
        pctPaint.setColor(0xFFFFFFFF);
        pctPaint.setShadowLayer(dp(2f), 0f, dp(1f), 0x59000000);

        outPaint.setTextAlign(Paint.Align.LEFT);
        outPaint.setFakeBoldText(true);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(1.2f));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void setSlices(List<Slice> data) {
        slices.clear();
        if (data != null && !data.isEmpty()) {
            slices.addAll(data);
        }
        invalidate();
    }

    public void setEmptyText(String s) {
        emptyText = s;
        invalidate();
    }

    public void setLabelColor(int color) {
        labelColor = color;
        invalidate();
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(w, h) / 2f - dp(40f);
        if (radius < dp(40f)) {
            radius = Math.min(w, h) / 2f - dp(6f);
        }
        if (radius <= 0) {
            return;
        }

        double total = 0d;
        for (Slice s : slices) {
            total += s.value;
        }

        if (total <= 0d) {
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(dp(16f));
            fill.setColor(labelColor & 0x33FFFFFF);
            canvas.drawCircle(cx, cy, radius - dp(8f), fill);

            outPaint.setTextAlign(Paint.Align.CENTER);
            outPaint.setFakeBoldText(false);
            outPaint.setColor(labelColor);
            outPaint.setTextSize(dp(14f));
            canvas.drawText(emptyText, cx, cy + dp(5f), outPaint);
            return;
        }

        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);

        float[] starts = new float[slices.size()];
        float[] sweeps = new float[slices.size()];
        float acc = -90f;
        for (int i = 0; i < slices.size(); i++) {
            starts[i] = acc;
            sweeps[i] = (float) (slices.get(i).value / total * 360d);
            acc += sweeps[i];
        }

        // 1) 扇形
        fill.setStyle(Paint.Style.FILL);
        if (slices.size() == 1) {
            fill.setColor(slices.get(0).color);
            canvas.drawCircle(cx, cy, radius, fill);
        } else {
            for (int i = 0; i < slices.size(); i++) {
                fill.setColor(slices.get(i).color);
                canvas.drawArc(oval, starts[i], sweeps[i], true, fill);
            }
            gap.setStrokeWidth(dp(2f));
            for (int i = 0; i < slices.size(); i++) {
                canvas.drawArc(oval, starts[i], sweeps[i], true, gap);
            }
        }

        // 2) 标签
        List<float[]> outside = new ArrayList<>();
        for (int i = 0; i < slices.size(); i++) {
            if (sweeps[i] >= MIN_INSIDE_DEG) {
                drawInside(canvas, cx, cy, radius, slices.get(i), starts[i], sweeps[i], total);
            } else {
                outside.add(new float[]{starts[i] + sweeps[i] / 2f, sweeps[i], i});
            }
        }
        if (!outside.isEmpty()) {
            drawOutside(canvas, cx, cy, radius, outside, total);
        }
    }

    private static String pctText(double p) {
        if (p >= 10d) {
            return String.format(Locale.CHINA, "%.0f%%", p);
        }
        return String.format(Locale.CHINA, "%.1f%%", p);
    }

    private void drawInside(Canvas canvas, float cx, float cy, float radius,
                            Slice s, float start, float sweep, double total) {
        double mid = Math.toRadians(start + sweep / 2f);
        String pct = pctText(s.value / total * 100d);

        if (sweep >= TWO_LINE_DEG) {
            float r = radius * 0.62f;
            float tx = cx + (float) (Math.cos(mid) * r);
            float ty = cy + (float) (Math.sin(mid) * r);
            namePaint.setTextSize(Math.max(dp(9f), Math.min(dp(13f), radius * 0.115f)));
            pctPaint.setTextSize(Math.max(dp(9f), Math.min(dp(11.5f), radius * 0.10f)));

            float half = (namePaint.getTextSize() + dp(3f) + pctPaint.getTextSize()) / 2f;
            canvas.drawText(s.label, tx, ty - half + namePaint.getTextSize() * 0.86f, namePaint);
            canvas.drawText(pct, tx, ty + half, pctPaint);
        } else {
            float r = radius * 0.66f;
            float tx = cx + (float) (Math.cos(mid) * r);
            float ty = cy + (float) (Math.sin(mid) * r);
            namePaint.setTextSize(Math.max(dp(8.5f), Math.min(dp(10.5f), radius * 0.09f)));
            canvas.drawText(s.label + " " + pct, tx, ty + namePaint.getTextSize() * 0.36f, namePaint);
        }
    }

    /** 小扇区：引线拉到饼图外面标注，纵向错开避免叠在一起。 */
    private void drawOutside(Canvas canvas, float cx, float cy, float radius,
                             List<float[]> items, double total) {
        final float textSize = dp(10.5f);
        outPaint.setTextSize(textSize);
        outPaint.setColor(labelColor);
        outPaint.setFakeBoldText(true);
        linePaint.setColor(labelColor);

        Collections.sort(items, new Comparator<float[]>() {
            @Override
            public int compare(float[] a, float[] b) {
                return Float.compare(a[0], b[0]);
            }
        });

        float minGap = textSize * 1.7f;
        float[] ys = new float[items.size()];
        for (int i = 0; i < items.size(); i++) {
            double mid = Math.toRadians(items.get(i)[0]);
            ys[i] = cy + (float) (Math.sin(mid) * (radius + dp(16f)));
        }
        for (int i = 1; i < ys.length; i++) {
            if (ys[i] - ys[i - 1] < minGap) {
                ys[i] = ys[i - 1] + minGap;
            }
        }
        float bottom = getHeight() - dp(6f);
        if (ys[ys.length - 1] > bottom) {
            float shift = ys[ys.length - 1] - bottom;
            for (int i = 0; i < ys.length; i++) {
                ys[i] -= shift;
            }
        }
        for (int i = 0; i < ys.length; i++) {
            if (ys[i] < dp(8f)) {
                ys[i] = dp(8f);
            }
        }

        for (int i = 0; i < items.size(); i++) {
            float[] item = items.get(i);
            int idx = (int) item[2];
            double mid = Math.toRadians(item[0]);
            float pct = (float) (slices.get(idx).value / total * 100d);
            String text = slices.get(idx).label + " " + pctText(pct);

            float dirX = (float) Math.cos(mid);
            float dirY = (float) Math.sin(mid);
            float edgeX = cx + dirX * radius;
            float edgeY = cy + dirY * radius;
            float anchorX = cx + dirX * (radius + dp(12f));
            float anchorY = ys[i];

            canvas.drawLine(edgeX, edgeY, anchorX, anchorY, linePaint);

            boolean right = dirX >= 0f;
            float textW = outPaint.measureText(text);
            if (right) {
                float tx = anchorX + dp(4f);
                if (tx + textW > getWidth() - dp(2f)) {
                    tx = getWidth() - dp(2f) - textW;
                }
                outPaint.setTextAlign(Paint.Align.LEFT);
                canvas.drawLine(anchorX, anchorY, tx, anchorY, linePaint);
                canvas.drawText(text, tx, anchorY + textSize * 0.36f, outPaint);
            } else {
                float tx = anchorX - dp(4f);
                if (tx - textW < dp(2f)) {
                    tx = textW + dp(2f);
                }
                outPaint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawLine(anchorX, anchorY, tx, anchorY, linePaint);
                canvas.drawText(text, tx, anchorY + textSize * 0.36f, outPaint);
            }
        }
    }
}
