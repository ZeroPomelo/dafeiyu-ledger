package com.dafeiyu.ledger;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/** 「记一笔」底部弹层：金额 + 分类 + 备注。 */
public class EntrySheet extends Dialog {

    public interface OnSaved {
        void onSaved(double amount);
    }

    private final Context ctx;
    private final int type;
    private final OnSaved onSaved;
    private final int accent;

    private final EditText etAmount;
    private final EditText etNote;
    private final FlowLayout chips;
    private String category;

    public EntrySheet(Context ctx, int type, OnSaved onSaved) {
        super(ctx, Prefs.isDark(ctx) ? R.style.SheetDialog_Dark : R.style.SheetDialog);
        this.ctx = ctx;
        this.type = type;
        this.onSaved = onSaved;
        this.accent = Palette.get(ctx).accentFor(type);

        View root = LayoutInflater.from(ctx).inflate(R.layout.sheet_entry, null);
        setContentView(root);

        etAmount = (EditText) root.findViewById(R.id.sheetAmount);
        etNote = (EditText) root.findViewById(R.id.sheetNote);
        chips = (FlowLayout) root.findViewById(R.id.sheetChips);

        TextView title = (TextView) root.findViewById(R.id.sheetTitle);
        title.setText(type == Db.TYPE_INCOME ? "记一笔入账" : "记一笔消费");

        Button save = (Button) root.findViewById(R.id.sheetSave);
        save.setBackgroundResource(type == Db.TYPE_INCOME
                ? R.drawable.input_income : R.drawable.input_expense);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });

        etNote.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    save();
                    return true;
                }
                return false;
            }
        });

        // 点空白处关闭
        root.findViewById(R.id.scrim).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        root.findViewById(R.id.sheet).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 吞掉点击
            }
        });

        buildChips();

        Window w = getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }
        setCanceledOnTouchOutside(true);
    }

    private void buildChips() {
        String[] cats = Cats.of(type);
        category = cats[0];
        chips.removeAllViews();
        for (int i = 0; i < cats.length; i++) {
            final String name = cats[i];
            TextView chip = new TextView(ctx);
            chip.setText(name);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(7), dp(14), dp(7));
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            lp.bottomMargin = dp(8);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    category = name;
                    paintChips();
                }
            });
            chips.addView(chip);
        }
        paintChips();
    }

    private void paintChips() {
        Palette p = Palette.get(ctx);
        String[] cats = Cats.of(type);
        for (int i = 0; i < chips.getChildCount(); i++) {
            TextView chip = (TextView) chips.getChildAt(i);
            boolean on = cats[i].equals(category);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(18));
            if (on) {
                bg.setColor(accent);
                chip.setTextColor(0xFFFFFFFF);
                chip.setTypeface(null, Typeface.BOLD);
            } else {
                bg.setColor(p.chipBg);
                bg.setStroke(dp(1), p.chipLine);
                chip.setTextColor(p.chipText);
                chip.setTypeface(null, Typeface.NORMAL);
            }
            chip.setBackground(bg);
        }
    }

    private void save() {
        String raw = etAmount.getText().toString().trim();
        if (TextUtils.isEmpty(raw)) {
            toast("请先填写金额");
            etAmount.requestFocus();
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            toast("金额格式不对，请只填数字");
            return;
        }
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0d) {
            toast("金额要大于 0 才行");
            return;
        }
        if (amount > 999999999d) {
            toast("金额太大了，请分几笔记");
            return;
        }
        amount = Math.round(amount * 100d) / 100d;

        String note = etNote.getText().toString().trim();
        Db.get(ctx).insert(type, category, amount, note, System.currentTimeMillis());

        hideKeyboard();
        dismiss();
        if (onSaved != null) {
            onSaved.onSaved(amount);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && etAmount.getWindowToken() != null) {
            imm.hideSoftInputFromWindow(etAmount.getWindowToken(), 0);
        }
    }

    private void toast(String s) {
        Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
