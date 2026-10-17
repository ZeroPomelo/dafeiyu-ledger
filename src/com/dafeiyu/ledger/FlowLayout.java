package com.dafeiyu.ledger;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** 简单的自动换行布局，让分类标签一屏排得下、不用横向滑动。 */
public class FlowLayout extends ViewGroup {

    public FlowLayout(Context ctx) {
        super(ctx);
    }

    public FlowLayout(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
    }

    public FlowLayout(Context ctx, AttributeSet attrs, int defStyle) {
        super(ctx, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int available = (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED)
                ? Integer.MAX_VALUE
                : widthSize - getPaddingLeft() - getPaddingRight();

        int lineWidth = 0;
        int lineHeight = 0;
        int total = 0;
        int maxLine = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int cw = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            int ch = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;

            if (lineWidth > 0 && lineWidth + cw > available) {
                total += lineHeight;
                maxLine = Math.max(maxLine, lineWidth);
                lineWidth = cw;
                lineHeight = ch;
            } else {
                lineWidth += cw;
                lineHeight = Math.max(lineHeight, ch);
            }
        }
        total += lineHeight;
        maxLine = Math.max(maxLine, lineWidth);

        setMeasuredDimension(
                resolveSize(maxLine + getPaddingLeft() + getPaddingRight(), widthMeasureSpec),
                resolveSize(total + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int available = r - l - getPaddingLeft() - getPaddingRight();
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int lineHeight = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int cw = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            int ch = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;

            if (x > getPaddingLeft() && (x - getPaddingLeft()) + cw > available) {
                x = getPaddingLeft();
                y += lineHeight;
                lineHeight = 0;
            }
            child.layout(x + lp.leftMargin, y + lp.topMargin,
                    x + lp.leftMargin + child.getMeasuredWidth(),
                    y + lp.topMargin + child.getMeasuredHeight());
            x += cw;
            lineHeight = Math.max(lineHeight, ch);
        }
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof MarginLayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new MarginLayoutParams(p);
    }
}
