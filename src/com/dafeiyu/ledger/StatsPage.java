package com.dafeiyu.ledger;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 统计页：消费 / 入账 的单饼图模式，以及「月/年收支差」的双饼图模式。 */
public class StatsPage {

    private static final int MODE_EXPENSE = 0;
    private static final int MODE_DIFF = 1;
    private static final int MODE_INCOME = 2;

    private final Context ctx;
    private final Db db;
    private final View root;

    private final View normalBox;
    private final View diffBox;
    private final View totalCard;
    private final SegBar segType;
    private final SegBar segPeriod;
    private final PieChartView pie;
    private final PieChartView pieYear;
    private final PieChartView pieMonth;
    private final TextView tvTotalLabel;
    private final TextView tvPeriodTotal;
    private final TextView tvPeriodSub;
    private final TextView btnDate;
    private final TextView btnYear;
    private final TextView btnMonth;
    private final TextView tvYearDiff;
    private final TextView tvMonthDiff;

    private int mode = MODE_EXPENSE;
    private int kind = Period.DAY;
    private long selectedStart;
    private long yearStart;
    private long monthStart;

    public StatsPage(Context ctx, ViewGroup parent) {
        this.ctx = ctx;
        this.db = Db.get(ctx);
        root = LayoutInflater.from(ctx).inflate(R.layout.page_stats, parent, false);

        normalBox = root.findViewById(R.id.normalBox);
        diffBox = root.findViewById(R.id.diffBox);
        totalCard = root.findViewById(R.id.totalCard);
        pie = (PieChartView) root.findViewById(R.id.pie);
        pieYear = (PieChartView) root.findViewById(R.id.pieYear);
        pieMonth = (PieChartView) root.findViewById(R.id.pieMonth);
        tvTotalLabel = (TextView) root.findViewById(R.id.tvTotalLabel);
        tvPeriodTotal = (TextView) root.findViewById(R.id.tvPeriodTotal);
        tvPeriodSub = (TextView) root.findViewById(R.id.tvPeriodSub);
        btnDate = (TextView) root.findViewById(R.id.btnDate);
        btnYear = (TextView) root.findViewById(R.id.btnYear);
        btnMonth = (TextView) root.findViewById(R.id.btnMonth);
        tvYearDiff = (TextView) root.findViewById(R.id.tvYearDiff);
        tvMonthDiff = (TextView) root.findViewById(R.id.tvMonthDiff);

        long now = System.currentTimeMillis();
        selectedStart = Period.normalize(kind, now);
        yearStart = Period.normalize(Period.YEAR, now);
        monthStart = Period.normalize(Period.MONTH, now);

        LinearLayout typeBox = (LinearLayout) root.findViewById(R.id.segType);
        LinearLayout periodBox = (LinearLayout) root.findViewById(R.id.segPeriod);

        segType = new SegBar(ctx, new String[]{"消费", "月/年收支差", "入账"}, accent());
        segType.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        typeBox.addView(segType);
        segType.setOnPick(new SegBar.OnPick() {
            @Override
            public void onPick(int index) {
                mode = index;
                render();
            }
        });

        segPeriod = new SegBar(ctx, Period.NAMES, accent());
        segPeriod.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        periodBox.addView(segPeriod);
        segPeriod.setOnPick(new SegBar.OnPick() {
            @Override
            public void onPick(int index) {
                kind = index;
                selectedStart = Period.normalize(kind, System.currentTimeMillis());
                render();
            }
        });

        root.findViewById(R.id.btnPrev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedStart = Period.shift(kind, selectedStart, -1);
                render();
            }
        });
        root.findViewById(R.id.btnNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedStart = Period.shift(kind, selectedStart, 1);
                render();
            }
        });
        btnDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePicker();
            }
        });

        root.findViewById(R.id.btnYearPrev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                yearStart = Period.shift(Period.YEAR, yearStart, -1);
                render();
            }
        });
        root.findViewById(R.id.btnYearNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                yearStart = Period.shift(Period.YEAR, yearStart, 1);
                render();
            }
        });
        btnYear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showUnitPicker(Period.YEAR, true);
            }
        });

        root.findViewById(R.id.btnMonthPrev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                monthStart = Period.shift(Period.MONTH, monthStart, -1);
                render();
            }
        });
        root.findViewById(R.id.btnMonthNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                monthStart = Period.shift(Period.MONTH, monthStart, 1);
                render();
            }
        });
        btnMonth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showUnitPicker(Period.MONTH, false);
            }
        });

        render();
    }

    public View getView() {
        return root;
    }

    public void refresh() {
        render();
    }

    // ------------------------------------------------------------------ 状态

    private int type() {
        return mode == MODE_INCOME ? Db.TYPE_INCOME : Db.TYPE_EXPENSE;
    }

    private int accent() {
        Palette p = Palette.get(ctx);
        return mode == MODE_DIFF ? p.brand : p.accentFor(type());
    }

    private void render() {
        boolean diff = mode == MODE_DIFF;
        normalBox.setVisibility(diff ? View.GONE : View.VISIBLE);
        diffBox.setVisibility(diff ? View.VISIBLE : View.GONE);

        int a = accent();
        segType.setAccent(a);
        segPeriod.setAccent(a);
        segPeriod.setInteractionEnabled(!diff);

        Palette p = Palette.get(ctx);
        pie.setLabelColor(p.textSub);
        pieYear.setLabelColor(p.textSub);
        pieMonth.setLabelColor(p.textSub);

        if (diff) {
            renderDiff();
        } else {
            renderNormal();
        }
    }

    // ------------------------------------------------------------ 单饼图模式

    private void renderNormal() {
        totalCard.setBackgroundResource(type() == Db.TYPE_INCOME
                ? R.drawable.header_income : R.drawable.header_expense);

        Period p = Period.of(kind, selectedStart);
        double total = db.sum(type(), p.start, p.end);
        int count = db.count(type(), p.start, p.end);

        tvTotalLabel.setText(type() == Db.TYPE_INCOME ? "入账合计" : "消费合计");
        tvPeriodTotal.setText("¥ " + Period.money(total));
        tvPeriodSub.setText("共 " + count + " 笔 · " + p.label);
        btnDate.setText(p.label);

        List<Object[]> rows = db.sumByCategory(type(), p.start, p.end);
        List<PieChartView.Slice> slices = new ArrayList<>();
        for (Object[] row : rows) {
            String cat = (String) row[0];
            double v = (Double) row[1];
            slices.add(new PieChartView.Slice(cat, v, Cats.colorOf(type(), cat)));
        }
        pie.setEmptyText("这一期还没有" + (type() == Db.TYPE_INCOME ? "入账" : "消费"));
        pie.setSlices(slices);
    }

    // ---------------------------------------------------------- 收支差模式

    private void renderDiff() {
        Period y = Period.of(Period.YEAR, yearStart);
        double yearExpense = db.sum(Db.TYPE_EXPENSE, y.start, y.end);
        double yearIncome = db.sum(Db.TYPE_INCOME, y.start, y.end);
        btnYear.setText(y.label);
        setDiffPie(pieYear, yearExpense, yearIncome, "这一年还没有记录");
        setDiffText(tvYearDiff, yearIncome - yearExpense);

        Period m = Period.of(Period.MONTH, monthStart);
        double monthExpense = db.sum(Db.TYPE_EXPENSE, m.start, m.end);
        double monthIncome = db.sum(Db.TYPE_INCOME, m.start, m.end);
        btnMonth.setText(m.label);
        setDiffPie(pieMonth, monthExpense, monthIncome, "这个月还没有记录");
        setDiffText(tvMonthDiff, monthIncome - monthExpense);
    }

    private void setDiffPie(PieChartView view, double expense, double income, String empty) {
        Palette p = Palette.get(ctx);
        List<PieChartView.Slice> slices = new ArrayList<>();
        if (expense > 0d) {
            slices.add(new PieChartView.Slice("支出", expense, p.expense));
        }
        if (income > 0d) {
            slices.add(new PieChartView.Slice("收入", income, p.income));
        }
        view.setEmptyText(empty);
        view.setSlices(slices);
    }

    private void setDiffText(TextView view, double diff) {
        Palette p = Palette.get(ctx);
        if (Math.abs(diff) < 0.005d) {
            view.setText("0.00");
            view.setTextColor(p.textSub);
            return;
        }
        view.setText((diff > 0d ? "+" : "-") + Period.money(Math.abs(diff)));
        view.setTextColor(diff > 0d ? p.income : p.expense);
    }

    // ------------------------------------------------------------------ 选择

    private void showDatePicker() {
        long cur = Period.normalize(kind, System.currentTimeMillis());
        int count;
        switch (kind) {
            case Period.WEEK:
                count = 52;
                break;
            case Period.MONTH:
                count = 36;
                break;
            case Period.YEAR:
                count = 12;
                break;
            case Period.DAY:
            default:
                count = 60;
                break;
        }
        final long[] starts = new long[count];
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) {
            starts[i] = Period.shift(kind, cur, -i);
            labels[i] = Period.of(kind, starts[i]).label;
        }
        new AlertDialog.Builder(ctx)
                .setTitle("选择" + Period.NAMES[kind])
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectedStart = starts[which];
                        render();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showUnitPicker(final int pickKind, final boolean forYear) {
        long cur = Period.normalize(pickKind, System.currentTimeMillis());
        final int count = pickKind == Period.YEAR ? 12 : 36;
        final long[] starts = new long[count];
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) {
            starts[i] = Period.shift(pickKind, cur, -i);
            labels[i] = Period.of(pickKind, starts[i]).label;
        }
        new AlertDialog.Builder(ctx)
                .setTitle(forYear ? "选择年份" : "选择月份")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (forYear) {
                            yearStart = starts[which];
                        } else {
                            monthStart = starts[which];
                        }
                        render();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
