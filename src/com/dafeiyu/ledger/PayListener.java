package com.dafeiyu.ledger;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通知监听自动记账：读到支付宝 / 微信的收付款通知，自动解析金额并记一笔。
 * 只有用户在系统里授予「通知使用权」并且设置页开关打开时才生效。
 */
public class PayListener extends NotificationListenerService {

    private static final String PKG_ALIPAY = "com.eg.android.AlipayGphone";
    private static final String PKG_WECHAT = "com.tencent.mm";

    private static final Pattern P_YUAN =
            Pattern.compile("([0-9]{1,9}(?:\\.[0-9]{1,2})?)\\s*元");
    private static final Pattern P_SIGN =
            Pattern.compile("[¥￥]\\s*([0-9]{1,9}(?:\\.[0-9]{1,2})?)");

    private static final String[] INCOME_WORDS = {"到账", "收款", "已收钱", "收钱", "入账", "收到转账"};
    private static final String[] EXPENSE_WORDS = {"付款", "支付", "消费", "支出", "扣款", "已扣"};

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 自动记完一笔后通知界面刷新。 */
    public interface Hook {
        void onAutoAdded();
    }

    public static volatile Hook hook;

    private static long lastTs = 0L;
    private static String lastKey = "";

    /** 系统里是否已经授予了通知使用权。 */
    public static boolean isEnabled(Context c) {
        try {
            ComponentName cn = new ComponentName(c, PayListener.class);
            if (Build.VERSION.SDK_INT >= 27) {
                android.app.NotificationManager nm = (android.app.NotificationManager)
                        c.getSystemService(Context.NOTIFICATION_SERVICE);
                return nm != null && nm.isNotificationListenerAccessGranted(cn);
            }
            String flat = Settings.Secure.getString(c.getContentResolver(),
                    "enabled_notification_listeners");
            return flat != null && flat.contains(cn.flattenToString());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            handle(sbn);
        } catch (Exception ignore) {
            // 任何异常都不影响其它通知
        }
    }

    private void handle(StatusBarNotification sbn) {
        if (sbn == null) {
            return;
        }
        String pkg = sbn.getPackageName();
        if (!PKG_ALIPAY.equals(pkg) && !PKG_WECHAT.equals(pkg)) {
            return;
        }
        Context ctx = getApplicationContext();
        if (ctx == null || !Prefs.isAutoRecord(ctx)) {
            return;
        }
        Notification n = sbn.getNotification();
        if (n == null) {
            return;
        }
        Bundle ex = n.extras;
        if (ex == null) {
            return;
        }

        String text = collect(ex);
        if (TextUtils.isEmpty(text)) {
            return;
        }
        parseAndRecord(ctx, text);
    }

    private static void add(StringBuilder sb, CharSequence cs) {
        if (cs != null) {
            sb.append(cs).append(' ');
        }
    }

    private static String collect(Bundle ex) {
        StringBuilder sb = new StringBuilder();
        add(sb, ex.getCharSequence(Notification.EXTRA_TITLE));
        add(sb, ex.getCharSequence(Notification.EXTRA_TEXT));
        add(sb, ex.getCharSequence(Notification.EXTRA_BIG_TEXT));
        add(sb, ex.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
        CharSequence[] lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence c : lines) {
                add(sb, c);
            }
        }
        return sb.toString().replace('\n', ' ').trim();
    }

    private static boolean has(String text, String[] words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private void parseAndRecord(Context ctx, String raw) {
        String text = raw.length() > 120 ? raw.substring(0, 120) : raw;

        boolean income = has(text, INCOME_WORDS);
        boolean expense = has(text, EXPENSE_WORDS);
        if (!income && !expense) {
            return;
        }
        int type = income ? Db.TYPE_INCOME : Db.TYPE_EXPENSE;

        double amount = findAmount(text);
        if (amount <= 0d || amount > 999999999d) {
            return;
        }
        amount = Math.round(amount * 100d) / 100d;

        // 同一条通知被反复更新时避免重复记账
        String key = type + "|" + Math.round(amount * 100d);
        long now = System.currentTimeMillis();
        if (key.equals(lastKey) && now - lastTs < 15000L) {
            return;
        }
        lastKey = key;
        lastTs = now;

        String category = BillImport.guessCategory(type, text);
        String note = text.length() > 40 ? text.substring(0, 40) : text;
        Db.get(ctx).insert(type, category, amount, note, now);

        final int fType = type;
        final double fAmount = amount;
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Toast.makeText(ctx,
                            (fType == Db.TYPE_INCOME ? "已自动记入账 " : "已自动记消费 ")
                                    + Period.money(fAmount) + " 元",
                            Toast.LENGTH_SHORT).show();
                } catch (Exception ignore) {
                    // 后台不允许弹 toast 就算了
                }
                Hook h = hook;
                if (h != null) {
                    h.onAutoAdded();
                }
            }
        });
    }

    private static double findAmount(String text) {
        Matcher m = P_YUAN.matcher(text);
        if (m.find()) {
            return safeParse(m.group(1));
        }
        m = P_SIGN.matcher(text);
        if (m.find()) {
            return safeParse(m.group(1));
        }
        return 0d;
    }

    private static double safeParse(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0d;
        }
    }
}
