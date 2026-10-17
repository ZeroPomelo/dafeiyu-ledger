package com.dafeiyu.ledger;

import android.content.Context;
import android.util.TypedValue;

/** 当前主题的调色板：所有代码里用到的颜色都从这里取，跟随浅色 / 深色主题。 */
public final class Palette {

    public static Palette P;

    public final boolean dark;
    public final int bg;
    public final int card;
    public final int text;
    public final int textSub;
    public final int line;
    public final int chipBg;
    public final int chipLine;
    public final int chipText;
    public final int overlay;

    public final int brand;
    public final int expense;
    public final int income;

    public static Palette get(Context c) {
        if (P == null) {
            P = new Palette(c);
        }
        return P;
    }

    /** 重建（切换主题、Activity 重建时调用）。 */
    public static Palette reload(Context c) {
        P = new Palette(c);
        return P;
    }

    private Palette(Context c) {
        dark = Prefs.isDark(c);
        bg = resolve(c, R.attr.colBg, 0xFFF4F6FB);
        card = resolve(c, R.attr.colCard, 0xFFFFFFFF);
        text = resolve(c, R.attr.colText, 0xFF1F2A37);
        textSub = resolve(c, R.attr.colTextSub, 0xFF8A96A8);
        line = resolve(c, R.attr.colLine, 0xFFE6EBF5);
        chipBg = resolve(c, R.attr.colChipBg, 0xFFF1F4FB);
        chipLine = resolve(c, R.attr.colChipLine, 0xFFDCE3F2);
        chipText = resolve(c, R.attr.colChipText, 0xFF5A6B85);
        overlay = resolve(c, R.attr.colOverlay, 0x66000000);

        brand = 0xFF2F6FED;
        expense = dark ? 0xFFF07185 : 0xFFF2637B;
        income = dark ? 0xFF35C79A : 0xFF23B48C;
    }

    public int accentFor(int type) {
        return type == Db.TYPE_INCOME ? income : expense;
    }

    private static int resolve(Context c, int attr, int def) {
        TypedValue tv = new TypedValue();
        if (c.getTheme().resolveAttribute(attr, tv, true)) {
            return tv.data;
        }
        return def;
    }
}
