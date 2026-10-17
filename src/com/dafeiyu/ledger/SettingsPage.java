package com.dafeiyu.ledger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 设置页：深色模式、导入账单、清空数据、关于。 */
public class SettingsPage {

    public static final int REQ_PICK = 0x51;

    private final Activity act;
    private final View root;
    private final Db db;
    private final Runnable onDataChanged;
    private final Switch swAuto;
    private final TextView tvAutoDesc;

    public SettingsPage(Activity act, ViewGroup parent, Runnable onDataChanged) {
        this.act = act;
        this.db = Db.get(act);
        this.onDataChanged = onDataChanged;

        root = LayoutInflater.from(act).inflate(R.layout.page_settings, parent, false);

        Switch sw = (Switch) root.findViewById(R.id.swDark);
        sw.setChecked(Prefs.isDark(act));
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                if (isChecked == Prefs.isDark(act)) {
                    return;
                }
                Prefs.setDark(act, isChecked);
                act.recreate();
            }
        });

        swAuto = (Switch) root.findViewById(R.id.swAuto);
        tvAutoDesc = (TextView) root.findViewById(R.id.tvAutoDesc);
        root.findViewById(R.id.rowAuto).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAutoRowClick();
            }
        });
        refreshAuto();

        root.findViewById(R.id.rowImport).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImportGuide();
            }
        });
        root.findViewById(R.id.rowClear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClear();
            }
        });

        TextView ver = (TextView) root.findViewById(R.id.tvVersion);
        ver.setText("版本 " + versionName());
    }

    public View getView() {
        return root;
    }

    // ------------------------------------------------------------ 自动记账

    /** 页面重新显示时刷新开关状态。 */
    public void refresh() {
        refreshAuto();
    }

    private void refreshAuto() {
        boolean granted = PayListener.isEnabled(act);
        boolean want = Prefs.isAutoRecord(act);
        swAuto.setChecked(granted && want);
        if (!granted) {
            tvAutoDesc.setText("点这里去系统设置里打开「通知使用权」");
        } else if (!want) {
            tvAutoDesc.setText("已授权，但自动记账已关闭");
        } else {
            tvAutoDesc.setText("已授权，收付款通知会自动记一笔");
        }
    }

    private void onAutoRowClick() {
        if (!PayListener.isEnabled(act)) {
            try {
                act.startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                Toast.makeText(act, "请找到「大肥鱼账本」并打开通知使用权",
                        Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(act, "打不开系统设置，请手动：设置 → 通知 → 通知使用权",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        Prefs.setAutoRecord(act, !Prefs.isAutoRecord(act));
        refreshAuto();
    }

    // ------------------------------------------------------------ 导入账单

    private void showImportGuide() {
        String msg = "支持导入微信、支付宝导出的 CSV 账单。\n\n"
                + "微信：我 → 服务 → 钱包 → 账单 → 常见问题 → 下载账单 → 用于个人对账，"
                + "选好时间范围后文件会发到邮箱（压缩包有密码），解压出 CSV 再导入。\n\n"
                + "支付宝：我的 → 账单 → 右上角 → 开具交易流水证明 → 用于个人对账，同样到邮箱取文件。\n\n"
                + "拿到压缩包的话请先用文件管理器解压出 .csv，再回来点「选择文件」。";
        new AlertDialog.Builder(act)
                .setTitle("导入账单")
                .setMessage(msg)
                .setNegativeButton("取消", null)
                .setPositiveButton("选择文件", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        pickFile();
                    }
                })
                .show();
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            act.startActivityForResult(Intent.createChooser(i, "选择账单文件"), REQ_PICK);
        } catch (Exception e) {
            toast("这台设备上没有可用的文件选择器");
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_PICK || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        startImportFrom(data.getData());
    }

    /** 供外部（文件管理器直接打开账单）调用。 */
    public void startImportFrom(final Uri uri) {
        if (uri == null) {
            return;
        }
        final ProgressDialog pd = new ProgressDialog(act);
        pd.setMessage("正在解析账单…");
        pd.setCancelable(false);
        pd.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                BillImport.Result parsed = null;
                String err = null;
                try {
                    byte[] raw = read(uri);
                    byte[] csv = unzipIfNeeded(raw);
                    parsed = BillImport.parse(csv);
                } catch (Exception e) {
                    err = e.getMessage();
                }
                final BillImport.Result r = parsed;
                final String e2 = err;
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            pd.dismiss();
                        } catch (Exception ignore) {
                            // 页面可能已销毁
                        }
                        if (r == null) {
                            new AlertDialog.Builder(act)
                                    .setTitle("读取失败")
                                    .setMessage(e2 == null ? "无法读取这个文件" : e2)
                                    .setPositiveButton("知道了", null)
                                    .show();
                            return;
                        }
                        showSummary(r);
                    }
                });
            }
        }).start();
    }

    private void showSummary(final BillImport.Result r) {
        if (r.error != null) {
            new AlertDialog.Builder(act)
                    .setTitle("导入失败")
                    .setMessage(r.error)
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("来源：").append(r.source).append("账单\n");
        sb.append("解析到 ").append(r.rows.size()).append(" 笔\n");
        if (r.expenseCount > 0) {
            sb.append("· 消费 ").append(r.expenseCount).append(" 笔，合计 ¥")
                    .append(Period.money(r.expenseSum)).append('\n');
        }
        if (r.incomeCount > 0) {
            sb.append("· 入账 ").append(r.incomeCount).append(" 笔，合计 ¥")
                    .append(Period.money(r.incomeSum)).append('\n');
        }
        if (r.skipped > 0) {
            sb.append("跳过 ").append(r.skipped).append(" 笔（退款 / 关闭 / 不计收支等）\n");
        }
        sb.append("\n已存在的重复记录会自动跳过。\n\n前几条预览：\n");
        int n = Math.min(5, r.rows.size());
        for (int i = 0; i < n; i++) {
            BillImport.Row row = r.rows.get(i);
            sb.append(Period.shortTime(row.ts)).append("  ")
                    .append(row.type == Db.TYPE_INCOME ? "入账" : "消费")
                    .append("·").append(row.category).append("  ")
                    .append(Period.moneyPlain(row.amount));
            if (!row.note.isEmpty()) {
                sb.append("  ").append(row.note);
            }
            sb.append('\n');
        }

        new AlertDialog.Builder(act)
                .setTitle("确认导入")
                .setMessage(sb.toString())
                .setNegativeButton("取消", null)
                .setPositiveButton("导入", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        doImport(r);
                    }
                })
                .show();
    }

    private void doImport(BillImport.Result r) {
        Set<String> expenseKeys = db.keySet(Db.TYPE_EXPENSE);
        Set<String> incomeKeys = db.keySet(Db.TYPE_INCOME);

        int added = 0;
        int dup = 0;
        for (BillImport.Row row : r.rows) {
            Set<String> keys = row.type == Db.TYPE_INCOME ? incomeKeys : expenseKeys;
            String k = Db.key(row.ts, row.amount);
            if (keys.contains(k)) {
                dup++;
                continue;
            }
            keys.add(k);
            db.insert(row.type, row.category, row.amount, row.note, row.ts);
            added++;
        }

        StringBuilder msg = new StringBuilder("成功导入 " + added + " 笔");
        if (dup > 0) {
            msg.append("，跳过重复 ").append(dup).append(" 笔");
        }
        Toast.makeText(act, msg.toString(), Toast.LENGTH_LONG).show();

        if (onDataChanged != null) {
            onDataChanged.run();
        }
    }

    // ------------------------------------------------------------ 清空数据

    private void confirmClear() {
        new AlertDialog.Builder(act)
                .setTitle("清空全部数据？")
                .setMessage("会删除所有消费和入账记录，包括导入的账单，删除后无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        db.clearAll();
                        Toast.makeText(act, "已清空", Toast.LENGTH_SHORT).show();
                        if (onDataChanged != null) {
                            onDataChanged.run();
                        }
                    }
                })
                .show();
    }

    // ------------------------------------------------------------ 文件读取

    private byte[] read(Uri uri) throws IOException {
        InputStream in = null;
        try {
            in = act.getContentResolver().openInputStream(uri);
            if (in == null) {
                throw new IOException("打不开这个文件");
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            long total = 0;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > 40L * 1024 * 1024) {
                    throw new IOException("文件太大了（超过 40MB）");
                }
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignore) {
                    // 忽略
                }
            }
        }
    }

    /** 如果用户直接选了压缩包，尽量自动取里面的 CSV。 */
    private byte[] unzipIfNeeded(byte[] raw) throws IOException {
        if (raw.length < 4 || raw[0] != 'P' || raw[1] != 'K') {
            return raw;
        }
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(raw));
        try {
            ZipEntry e;
            byte[] first = null;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                String name = e.getName().toLowerCase(Locale.US);
                boolean want = name.endsWith(".csv") || name.endsWith(".txt");
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = zis.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                }
                if (want) {
                    return bos.toByteArray();
                }
                if (first == null) {
                    first = bos.toByteArray();
                }
            }
            if (first != null) {
                return first;
            }
            throw new IOException("压缩包里没有可用的文件");
        } catch (java.util.zip.ZipException ze) {
            throw new IOException("这个压缩包有密码，请先用文件管理器解压出 CSV 再导入");
        } finally {
            try {
                zis.close();
            } catch (IOException ignore) {
                // 忽略
            }
        }
    }

    private String versionName() {
        try {
            return act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "2.0";
        }
    }

    private void toast(String s) {
        Toast.makeText(act, s, Toast.LENGTH_SHORT).show();
    }
}
