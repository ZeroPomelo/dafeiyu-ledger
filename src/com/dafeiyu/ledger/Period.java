package com.dafeiyu.ledger;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** 统计周期：每日 / 每周 / 每月 / 每年。 */
public final class Period {

    public static final int DAY = 0;
    public static final int WEEK = 1;
    public static final int MONTH = 2;
    public static final int YEAR = 3;

    public static final String[] NAMES = {"每日", "每周", "每月", "每年"};

    /** 区间起点（含）。 */
    public final long start;
    /** 区间终点（不含）。 */
    public final long end;
    /** 展示用标题。 */
    public final String label;

    private Period(long start, long end, String label) {
        this.start = start;
        this.end = end;
        this.label = label;
    }

    public static Calendar calendar(long ts) {
        Calendar c = Calendar.getInstance(Locale.CHINA);
        c.setTimeInMillis(ts);
        return c;
    }

    public static long startOfDay(long ts) {
        Calendar c = calendar(ts);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static long startOfWeek(long ts) {
        Calendar c = calendar(startOfDay(ts));
        int dow = c.get(Calendar.DAY_OF_WEEK); // SUNDAY=1 ... SATURDAY=7
        int back = (dow + 5) % 7;              // 周一为 0
        c.add(Calendar.DAY_OF_MONTH, -back);
        return c.getTimeInMillis();
    }

    public static long startOfMonth(long ts) {
        Calendar c = calendar(startOfDay(ts));
        c.set(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    public static long startOfYear(long ts) {
        Calendar c = calendar(startOfDay(ts));
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.MONTH, Calendar.JANUARY);
        return c.getTimeInMillis();
    }

    private static long plus(long ts, int field, int amount) {
        Calendar c = calendar(ts);
        c.add(field, amount);
        return c.getTimeInMillis();
    }

    /** 把任意时刻归到指定粒度的区间。 */
    public static Period of(int kind, long anchor) {
        long s;
        long e;
        switch (kind) {
            case WEEK:
                s = startOfWeek(anchor);
                e = plus(s, Calendar.DAY_OF_MONTH, 7);
                return new Period(s, e, weekLabel(s, e - 1));
            case MONTH:
                s = startOfMonth(anchor);
                e = plus(s, Calendar.MONTH, 1);
                return new Period(s, e, fmt("yyyy年M月", s));
            case YEAR:
                s = startOfYear(anchor);
                e = plus(s, Calendar.YEAR, 1);
                return new Period(s, e, fmt("yyyy年", s));
            case DAY:
            default:
                s = startOfDay(anchor);
                e = plus(s, Calendar.DAY_OF_MONTH, 1);
                return new Period(s, e, dayLabel(s));
        }
    }

    /** 区间起点按粒度归一化，用于把记录聚合到同一个周期。 */
    public static long normalize(int kind, long ts) {
        switch (kind) {
            case WEEK:
                return startOfWeek(ts);
            case MONTH:
                return startOfMonth(ts);
            case YEAR:
                return startOfYear(ts);
            case DAY:
            default:
                return startOfDay(ts);
        }
    }

    /** 按粒度前后平移若干个周期。 */
    public static long shift(int kind, long start, int delta) {
        switch (kind) {
            case WEEK:
                return plus(start, Calendar.DAY_OF_MONTH, 7 * delta);
            case MONTH:
                return plus(start, Calendar.MONTH, delta);
            case YEAR:
                return plus(start, Calendar.YEAR, delta);
            case DAY:
            default:
                return plus(start, Calendar.DAY_OF_MONTH, delta);
        }
    }

    private static final String[] WEEK_CN = {"", "周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    private static String dayLabel(long ts) {
        Calendar c = calendar(ts);
        return fmt("yyyy年M月d日", ts) + " " + WEEK_CN[c.get(Calendar.DAY_OF_WEEK)];
    }

    private static String weekLabel(long from, long to) {
        return fmt("M月d日", from) + " - " + fmt("M月d日", to);
    }

    /** 记录列表里用的短时间：MM-dd HH:mm。 */
    public static String shortTime(long ts) {
        return fmt("MM-dd HH:mm", ts);
    }

    private static String fmt(String pattern, long ts) {
        // SimpleDateFormat 非线程安全，这里每次新建，调用频率很低。
        return new SimpleDateFormat(pattern, Locale.CHINA).format(new Date(ts));
    }

    /** 金额展示：1234.5 -> 1,234.50。 */
    public static String money(double v) {
        return String.format(Locale.CHINA, "%,.2f", v);
    }

    /** 无货币符号的紧凑金额。 */
    public static String moneyPlain(double v) {
        return String.format(Locale.CHINA, "%.2f", v);
    }
}
