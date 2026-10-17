package com.dafeiyu.ledger;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 分段选择控件（消费/入账、每日/每周/…）。 */
public class SegBar extends LinearLayout {

    public interface OnPick {
        void onPick(int index);
    }

    private final String[] labels;
    private int selected = 0;
    private int accent;
    private boolean interaction = true;
    private OnPick listener;

    public SegBar(Context ctx, String[] labels, int accent) {
        super(ctx);
        this.labels = labels;
        this.accent = accent;
        setOrientation(HORIZONTAL);
        build();
    }

    public void setOnPick(OnPick l) {
        this.listener = l;
    }

    public int getSelected() {
        return selected;
    }

    public void setSelected(int index, boolean notify) {
        if (index < 0 || index >= labels.length) {
            return;
        }
        selected = index;
        paint();
        if (notify && listener != null) {
            listener.onPick(index);
        }
    }

    /** 收支差模式下「每日/每周/每月/每年」用不上，置灰并禁用点击。 */
    public void setInteractionEnabled(boolean on) {
        interaction = on;
        setAlpha(on ? 1f : 0.35f);
    }

    public void setAccent(int color) {
        accent = color;
        paint();
    }

    private void build() {
        removeAllViews();
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView tv = new TextView(getContext());
            tv.setText(labels[i]);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) {
                lp.leftMargin = dp(5);
            }
            tv.setLayoutParams(lp);
            tv.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!interaction) {
                        return;
                    }
                    if (selected != index) {
                        setSelected(index, true);
                    }
                }
            });
            addView(tv);
        }
        paint();
    }

    private void paint() {
        Palette p = Palette.get(getContext());
        for (int i = 0; i < getChildCount(); i++) {
            TextView tv = (TextView) getChildAt(i);
            boolean on = (i == selected);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(11));
            if (on) {
                bg.setColor(accent);
                tv.setTextColor(0xFFFFFFFF);
                tv.setTypeface(null, Typeface.BOLD);
            } else {
                bg.setColor(p.chipBg);
                tv.setTextColor(p.chipText);
                tv.setTypeface(null, Typeface.NORMAL);
            }
            tv.setBackground(bg);
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
