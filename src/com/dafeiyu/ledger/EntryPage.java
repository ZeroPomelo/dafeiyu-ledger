package com.dafeiyu.ledger;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 消费 / 入账 页：总额卡 + 按天分组列表 + 分类筛选 + 多选删除。 */
public class EntryPage {

    public interface OnDataChanged {
        void onDataChanged();
    }

    private static final int KIND_DAY = 0;
    private static final int KIND_ROW = 1;

    private static class Item {
        int kind;
        String day;
        Record record;
        boolean firstInGroup;
        boolean lastInGroup;
    }

    private final Context ctx;
    private final int type;
    private final Db db;
    private final OnDataChanged callback;

    private final View root;
    private final TextView tvHeaderLabel;
    private final TextView tvTotal;
    private final TextView tvSubTotal;
    private final TextView tvGoal;
    private final TextView tvActual;
    private final View filterBar;
    private final TextView tvFilterInfo;
    private final View selBar;
    private final TextView tvSelCount;
    private final View fabBox;
    private final TextView fabFilter;
    private final ListView list;
    private final TextView emptyView;
    private final Adapter adapter;

    private final List<Item> items = new ArrayList<>();
    private final Set<Long> selected = new HashSet<>();
    private String filter;
    private boolean selecting;

    public EntryPage(Context ctx, int type, ViewGroup parent, OnDataChanged callback) {
        this.ctx = ctx;
        this.type = type;
        this.db = Db.get(ctx);
        this.callback = callback;

        root = LayoutInflater.from(ctx).inflate(R.layout.page_entry, parent, false);
        tvHeaderLabel = (TextView) root.findViewById(R.id.tvHeaderLabel);
        tvTotal = (TextView) root.findViewById(R.id.tvTotal);
        tvSubTotal = (TextView) root.findViewById(R.id.tvSubTotal);
        tvGoal = (TextView) root.findViewById(R.id.tvGoal);
        tvActual = (TextView) root.findViewById(R.id.tvActual);
        filterBar = root.findViewById(R.id.filterBar);
        tvFilterInfo = (TextView) root.findViewById(R.id.tvFilterInfo);
        selBar = root.findViewById(R.id.selBar);
        tvSelCount = (TextView) root.findViewById(R.id.tvSelCount);
        fabBox = root.findViewById(R.id.fabBox);
        fabFilter = (TextView) root.findViewById(R.id.fabFilter);
        list = (ListView) root.findViewById(R.id.list);
        emptyView = (TextView) root.findViewById(R.id.emptyView);
        TextView fab = (TextView) root.findViewById(R.id.fab);

        boolean income = (type == Db.TYPE_INCOME);
        root.findViewById(R.id.totalCard)
                .setBackgroundResource(income ? R.drawable.header_income : R.drawable.header_expense);
        fab.setBackgroundResource(income ? R.drawable.fab_income : R.drawable.fab_expense);
        tvHeaderLabel.setText(income ? "入账总额" : "消费总额");

        adapter = new Adapter();
        list.setAdapter(adapter);

        styleFab(fab);
        styleFab(fabFilter);

        tvGoal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showGoalDialog();
            }
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSheet();
            }
        });
        fabFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFilterDialog();
            }
        });
        root.findViewById(R.id.btnFilterClear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filter = null;
                refresh();
            }
        });
        root.findViewById(R.id.btnSelCancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exitSelect();
            }
        });
        root.findViewById(R.id.btnSelAll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectAll();
            }
        });
        root.findViewById(R.id.btnSelDel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDeleteSelected();
            }
        });

        refresh();
    }

    public View getView() {
        return root;
    }

    private void openSheet() {
        EntrySheet sheet = new EntrySheet(ctx, type, new EntrySheet.OnSaved() {
            @Override
            public void onSaved(double amount) {
                refresh();
                if (callback != null) {
                    callback.onDataChanged();
                }
                Toast.makeText(ctx,
                        (type == Db.TYPE_INCOME ? "已记入账 " : "已记消费 ")
                                + Period.money(amount) + " 元",
                        Toast.LENGTH_SHORT).show();
            }
        });
        sheet.show();
    }

    // ------------------------------------------------------------------ 刷新

    public void refresh() {
        double total = db.total(type);
        int count = db.count(type, 0L, Long.MAX_VALUE);
        tvTotal.setText("¥ " + Period.money(total));
        tvSubTotal.setText("共 " + count + " 笔记录");

        List<Record> all = db.list(type);
        List<Record> rows = new ArrayList<>();
        double filteredSum = 0d;
        for (Record r : all) {
            if (filter != null && !filter.equals(r.category)) {
                continue;
            }
            rows.add(r);
            filteredSum += r.amount;
        }
        buildItems(rows);
        adapter.notifyDataSetChanged();

        boolean empty = rows.isEmpty();
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            emptyView.setText(filter != null
                    ? "「" + filter + "」下还没有记录"
                    : "还没有记录\n点右下角「记」开始记账");
        }

        if (filter != null) {
            tvFilterInfo.setText("只看「" + filter + "」·" + rows.size() + " 笔 · ¥"
                    + Period.money(filteredSum));
            tvFilterInfo.setTextColor(accent());
        }

        updateGoal();
        updateChrome();
    }

    // ------------------------------------------------------------------ 本月目标

    private void updateGoal() {
        long cents = Prefs.getGoalCents(ctx, type);
        String label = (type == Db.TYPE_INCOME ? "本月收入目标：" : "本月支出目标：");
        tvGoal.setText(cents > 0L
                ? label + "¥" + Period.money(cents / 100d)
                : label + "未设置");

        // 本月实际值：按当前自然月实时累计，每月 1 号 00:00 自动从 0 开始
        Period month = Period.of(Period.MONTH, System.currentTimeMillis());
        double actual = db.sum(type, month.start, month.end);
        tvActual.setText((type == Db.TYPE_INCOME ? "本月实际收入：" : "本月实际支出：")
                + "¥" + Period.money(actual));
    }

    private void showGoalDialog() {
        final EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("填 0 或留空表示不设目标");
        long cents = Prefs.getGoalCents(ctx, type);
        if (cents > 0L) {
            input.setText(Period.moneyPlain(cents / 100d));
            input.setSelection(input.getText().length());
        }
        FrameLayout box = new FrameLayout(ctx);
        box.setPadding(dp(22), dp(6), dp(22), 0);
        box.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(ctx)
                .setTitle(type == Db.TYPE_INCOME ? "本月收入目标" : "本月支出目标")
                .setView(box)
                .setNegativeButton("取消", null)
                .setNeutralButton("清除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Prefs.setGoalCents(ctx, type, 0L);
                        refresh();
                    }
                })
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String raw = input.getText().toString().trim();
                        long value = 0L;
                        try {
                            if (!raw.isEmpty()) {
                                value = Math.round(Double.parseDouble(raw) * 100d);
                            }
                        } catch (NumberFormatException ignore) {
                            value = 0L;
                        }
                        Prefs.setGoalCents(ctx, type, Math.max(0L, value));
                        refresh();
                    }
                })
                .show();
    }

    private void buildItems(List<Record> rows) {
        items.clear();
        SimpleDateFormat f = new SimpleDateFormat("yyyy.MM.dd", Locale.CHINA);
        String lastDay = null;
        for (Record r : rows) {
            String d = f.format(new Date(r.ts));
            Item row = new Item();
            row.kind = KIND_ROW;
            row.record = r;
            row.day = d;
            if (!d.equals(lastDay)) {
                Item head = new Item();
                head.kind = KIND_DAY;
                head.day = d;
                items.add(head);
                row.firstInGroup = true;
                lastDay = d;
            }
            items.add(row);
        }
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.kind != KIND_ROW) {
                continue;
            }
            it.lastInGroup = (i + 1 >= items.size()) || items.get(i + 1).kind == KIND_DAY;
        }
    }

    private void updateChrome() {
        selBar.setVisibility(selecting ? View.VISIBLE : View.GONE);
        fabBox.setVisibility(selecting ? View.GONE : View.VISIBLE);
        filterBar.setVisibility(!selecting && filter != null ? View.VISIBLE : View.GONE);
    }

    /** 两个悬浮按钮统一成同样大小、同样的实心主题色，不带阴影。 */
    private void styleFab(TextView v) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(accent());
        v.setBackground(g);
        v.setTextColor(0xFFFFFFFF);
        v.setElevation(0f);
    }

    // ------------------------------------------------------------------ 多选

    private void enterSelect(long id) {
        selecting = true;
        selected.clear();
        selected.add(id);
        updateChrome();
        tvSelCount.setText("已选 1 条");
        adapter.notifyDataSetChanged();
    }

    private void toggleSelect(long id) {
        if (selected.contains(id)) {
            selected.remove(id);
        } else {
            selected.add(id);
        }
        if (selected.isEmpty()) {
            exitSelect();
            return;
        }
        tvSelCount.setText("已选 " + selected.size() + " 条");
        adapter.notifyDataSetChanged();
    }

    private void exitSelect() {
        selecting = false;
        selected.clear();
        updateChrome();
        adapter.notifyDataSetChanged();
    }

    private void selectAll() {
        selected.clear();
        for (Item it : items) {
            if (it.kind == KIND_ROW) {
                selected.add(it.record.id);
            }
        }
        selecting = true;
        updateChrome();
        tvSelCount.setText("已选 " + selected.size() + " 条");
        adapter.notifyDataSetChanged();
    }

    private void confirmDeleteSelected() {
        final int n = selected.size();
        if (n == 0) {
            return;
        }
        new AlertDialog.Builder(ctx)
                .setTitle("删除选中的 " + n + " 条记录？")
                .setMessage("删除后无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (Long id : selected) {
                            db.delete(id);
                        }
                        selected.clear();
                        selecting = false;
                        refresh();
                        if (callback != null) {
                            callback.onDataChanged();
                        }
                        Toast.makeText(ctx, "已删除 " + n + " 条", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------ 筛选

    private void showFilterDialog() {
        String[] cats = Cats.of(type);
        final String[] options = new String[cats.length + 1];
        options[0] = "全部分类";
        System.arraycopy(cats, 0, options, 1, cats.length);

        int checked = 0;
        if (filter != null) {
            for (int i = 0; i < cats.length; i++) {
                if (cats[i].equals(filter)) {
                    checked = i + 1;
                }
            }
        }
        new AlertDialog.Builder(ctx)
                .setTitle("只看哪个分类")
                .setSingleChoiceItems(options, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        filter = (which == 0) ? null : options[which];
                        selecting = false;
                        selected.clear();
                        dialog.dismiss();
                        refresh();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ------------------------------------------------------------------ 工具

    private int accent() {
        return Palette.get(ctx).accentFor(type);
    }

    private int dp(float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int blend(int base, int over, float ratio) {
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int or = (over >> 16) & 0xFF, og = (over >> 8) & 0xFF, ob = over & 0xFF;
        int r = (int) (br + (or - br) * ratio);
        int g = (int) (bg + (og - bg) * ratio);
        int b = (int) (bb + (ob - bb) * ratio);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private GradientDrawable rowBg(boolean first, boolean last, boolean sel) {
        Palette p = Palette.get(ctx);
        float r = dp(14);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadii(new float[]{
                first ? r : 0f, first ? r : 0f,
                first ? r : 0f, first ? r : 0f,
                last ? r : 0f, last ? r : 0f,
                last ? r : 0f, last ? r : 0f});
        g.setColor(sel ? blend(p.card, accent(), 0.16f) : p.card);
        return g;
    }

    // ------------------------------------------------------------------ 列表

    private class Adapter extends BaseAdapter {

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            Item it = items.get(position);
            return it.kind == KIND_ROW ? it.record.id : -position - 1;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).kind;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Item it = items.get(position);
            Palette p = Palette.get(ctx);

            if (it.kind == KIND_DAY) {
                TextView tv;
                if (convertView instanceof TextView) {
                    tv = (TextView) convertView;
                } else {
                    tv = (TextView) LayoutInflater.from(ctx)
                            .inflate(R.layout.item_day, parent, false);
                }
                tv.setText(it.day);
                tv.setTextColor(accent());
                return tv;
            }

            View v = convertView;
            if (v == null || !(v.findViewById(R.id.tvCat) != null)) {
                v = LayoutInflater.from(ctx).inflate(R.layout.item_record, parent, false);
            }
            final Record r = it.record;
            boolean sel = selected.contains(r.id);

            v.setBackground(rowBg(it.firstInGroup, it.lastInGroup, sel));
            v.findViewById(R.id.divider).setVisibility(
                    it.lastInGroup ? View.GONE : View.VISIBLE);

            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(Cats.colorOf(type, r.category));
            v.findViewById(R.id.dot).setBackground(dot);

            ((TextView) v.findViewById(R.id.tvCat)).setText(r.category);
            ((TextView) v.findViewById(R.id.tvCat)).setTextColor(p.text);

            TextView tvNote = (TextView) v.findViewById(R.id.tvNote);
            if (TextUtils.isEmpty(r.note)) {
                tvNote.setText("（无备注）");
                tvNote.setTextColor(p.textSub & 0x66FFFFFF);
            } else {
                tvNote.setText(r.note);
                tvNote.setTextColor(p.textSub);
            }

            TextView tvAmount = (TextView) v.findViewById(R.id.tvAmount);
            String sign = (type == Db.TYPE_INCOME) ? "+" : "-";
            tvAmount.setText(sign + Period.moneyPlain(r.amount));
            tvAmount.setTextColor(accent());
            tvAmount.setTypeface(null, Typeface.BOLD);

            ((TextView) v.findViewById(R.id.tvTime)).setText(Period.shortTime(r.ts));

            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (selecting) {
                        toggleSelect(r.id);
                    }
                }
            });
            v.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    if (selecting) {
                        toggleSelect(r.id);
                    } else {
                        enterSelect(r.id);
                    }
                    return true;
                }
            });
            return v;
        }
    }
}
