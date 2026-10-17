package com.dafeiyu.ledger;

import android.content.Context;
import android.content.SharedPreferences;

/** 本地设置项。 */
public final class Prefs {

    private static final String FILE = "dafeiyu_settings";
    private static final String KEY_DARK = "dark_mode";
    private static final String KEY_AUTO = "auto_record";
    private static final String KEY_GOAL_EXPENSE = "goal_expense_cents";
    private static final String KEY_GOAL_INCOME = "goal_income_cents";

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isDark(Context c) {
        return sp(c).getBoolean(KEY_DARK, false);
    }

    public static void setDark(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_DARK, v).apply();
    }

    /** 通知监听自动记账的总开关。 */
    public static boolean isAutoRecord(Context c) {
        return sp(c).getBoolean(KEY_AUTO, true);
    }

    public static void setAutoRecord(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_AUTO, v).apply();
    }

    /** 本月支出 / 收入目标，用「分」存，避免浮点误差。 */
    public static long getGoalCents(Context c, int type) {
        String key = type == Db.TYPE_INCOME ? KEY_GOAL_INCOME : KEY_GOAL_EXPENSE;
        return sp(c).getLong(key, 0L);
    }

    public static void setGoalCents(Context c, int type, long cents) {
        String key = type == Db.TYPE_INCOME ? KEY_GOAL_INCOME : KEY_GOAL_EXPENSE;
        sp(c).edit().putLong(key, Math.max(0L, cents)).apply();
    }
}
