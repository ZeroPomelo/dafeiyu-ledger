package com.dafeiyu.ledger;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 主界面：底部四个页面 —— 消费 / 入账 / 统计 / 设置。 */
public class MainActivity extends Activity {

    private static final String[] TAB_NAMES = {"消费", "入账", "统计", "设置"};

    /** recreate（切深色模式）后保持当前页。 */
    private static int lastTab = 0;

    private FrameLayout content;
    private EntryPage expensePage;
    private EntryPage incomePage;
    private StatsPage statsPage;
    private SettingsPage settingsPage;

    private final TextView[] tabs = new TextView[4];
    private int current = 0;

    /** 记账页增删记录后：统计页跟着刷新。 */
    private final EntryPage.OnDataChanged dataChanged = new EntryPage.OnDataChanged() {
        @Override
        public void onDataChanged() {
            if (statsPage != null) {
                statsPage.refresh();
            }
        }
    };

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    /** 跨到新的自然月时，把「本月实际支出/收入」刷成 0。 */
    private final Runnable monthTick = new Runnable() {
        @Override
        public void run() {
            dataBulkChanged.run();
            scheduleMonthTick();
        }
    };

    /** 通知监听自动记完一笔后刷新界面。 */
    private final PayListener.Hook autoHook = new PayListener.Hook() {
        @Override
        public void onAutoAdded() {
            dataBulkChanged.run();
        }
    };

    /** 设置页导入 / 清空数据后：三页全部刷新。 */
    private final Runnable dataBulkChanged = new Runnable() {
        @Override
        public void run() {
            if (expensePage != null) {
                expensePage.refresh();
            }
            if (incomePage != null) {
                incomePage.refresh();
            }
            if (statsPage != null) {
                statsPage.refresh();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(Prefs.isDark(this) ? R.style.AppTheme_Dark : R.style.AppTheme);
        super.onCreate(savedInstanceState);
        Palette.reload(this);
        setContentView(R.layout.activity_main);

        content = (FrameLayout) findViewById(R.id.content);
        LinearLayout bar = (LinearLayout) findViewById(R.id.tabbar);

        expensePage = new EntryPage(this, Db.TYPE_EXPENSE, content, dataChanged);
        incomePage = new EntryPage(this, Db.TYPE_INCOME, content, dataChanged);
        statsPage = new StatsPage(this, content);
        settingsPage = new SettingsPage(this, content, dataBulkChanged);

        content.addView(expensePage.getView());
        content.addView(incomePage.getView());
        content.addView(statsPage.getView());
        content.addView(settingsPage.getView());

        buildTabs(bar);

        int start = (savedInstanceState != null)
                ? savedInstanceState.getInt("tab", 0) : lastTab;
        select(start);

        handleViewIntent(getIntent());
        scheduleMonthTick();
    }

    /** 定时到下一个自然月 1 号 00:00，刷新一次月度数据。 */
    private void scheduleMonthTick() {
        long now = System.currentTimeMillis();
        long next = Period.of(Period.MONTH, now).end;
        long delay = Math.max(3000L, next - now + 1500L);
        uiHandler.removeCallbacks(monthTick);
        uiHandler.postDelayed(monthTick, delay);
    }

    @Override
    protected void onDestroy() {
        if (uiHandler != null) {
            uiHandler.removeCallbacks(monthTick);
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleViewIntent(intent);
    }

    /** 从文件管理器 / 邮件里直接「用大肥鱼账本打开」账单时，跳到设置页并开始导入。 */
    private void handleViewIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return;
        }
        Uri data = intent.getData();
        if (data == null) {
            return;
        }
        // 消费掉，避免 Activity 重建时重复弹导入
        setIntent(new Intent());
        select(3);
        if (settingsPage != null) {
            settingsPage.startImportFrom(data);
        }
    }

    private void buildTabs(LinearLayout bar) {
        Palette p = Palette.get(this);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            if (i > 0) {
                View div = new View(this);
                div.setBackgroundColor(p.line);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        dp(1), LinearLayout.LayoutParams.MATCH_PARENT);
                dlp.topMargin = dp(15);
                dlp.bottomMargin = dp(15);
                div.setLayoutParams(dlp);
                bar.addView(div);
            }
            TextView tv = new TextView(this);
            tv.setText(TAB_NAMES[i]);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            final int idx = i;
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(idx);
                }
            });
            bar.addView(tv);
            tabs[i] = tv;
        }
    }

    private void select(int index) {
        if (index < 0 || index >= TAB_NAMES.length) {
            index = 0;
        }
        current = index;

        expensePage.getView().setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        incomePage.getView().setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        statsPage.getView().setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        settingsPage.getView().setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        if (index == 2) {
            statsPage.refresh();
        } else if (index == 3) {
            settingsPage.refresh();
        } else if (index == 0) {
            expensePage.refresh();
        } else if (index == 1) {
            incomePage.refresh();
        }

        Palette p = Palette.get(this);
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] == null) {
                continue;
            }
            boolean on = (i == index);
            tabs[i].setTextColor(on ? p.brand : p.textSub);
            tabs[i].setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
        lastTab = index;
    }

    @Override
    protected void onResume() {
        super.onResume();
        PayListener.hook = autoHook;
        if (settingsPage != null) {
            settingsPage.refresh();
        }
        // 可能跨月了，回来时重新算一次本月实际值
        dataBulkChanged.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        PayListener.hook = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (settingsPage != null) {
            settingsPage.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", current);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
