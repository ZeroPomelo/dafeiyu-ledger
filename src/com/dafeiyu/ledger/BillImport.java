package com.dafeiyu.ledger;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 微信 / 支付宝导出的 CSV 账单解析。
 * 两家格式不完全一样，这里按表头名字自动识别列，不写死列序。
 */
public final class BillImport {

    public static class Row {
        public long ts;
        public double amount;
        public int type;
        public String category;
        public String note;
    }

    public static class Result {
        public final List<Row> rows = new ArrayList<>();
        public int skipped;
        public int expenseCount;
        public int incomeCount;
        public double expenseSum;
        public double incomeSum;
        public String source = "账单";
        public String error;
    }

    private static final String[] TIME_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy.MM.dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm",
            "yyyy年MM月dd日 HH:mm:ss",
            "yyyy-MM-dd"
    };

    private static final String[][] EXPENSE_RULES = {
            {"红包", "红包"},
            {"转账", "转账", "还钱", "还款", "群收款", "亲属卡"},
            {"生活缴费", "电费", "水费", "燃气", "话费", "宽带", "物业", "缴费", "国网", "中国移动", "中国联通", "中国电信"},
            {"应用会员", "会员", "vip", "app store", "apple", "icloud", "腾讯视频", "爱奇艺", "优酷", "芒果", "哔哩哔哩", "b站", "网易云", "qq音乐", "netflix", "spotify", "百度网盘", "订阅", "云服务"},
            {"游戏", "游戏", "王者", "和平精英", "原神", "米哈游", "steam", "点券", "皮肤", "蛋仔", "光遇", "崩坏", "游戏充值"},
            {"吃饭", "美团", "饿了么", "外卖", "餐厅", "饭店", "食堂", "咖啡", "奶茶", "星巴克", "瑞幸", "肯德基", "麦当劳", "汉堡", "烧烤", "火锅", "面馆", "小吃", "美食", "餐饮", "早餐", "午餐", "晚餐", "盒马"},
            {"购物", "淘宝", "天猫", "京东", "拼多多", "唯品会", "苏宁", "商城", "超市", "便利店", "商场", "购物", "亚马逊", "amazon", "闲鱼", "得物", "小米", "华为", "抖音", "快手"}
    };

    private static final String[][] INCOME_RULES = {
            {"红包", "红包"},
            {"工资", "工资", "薪资", "薪酬", "代发", "奖金", "绩效"},
            {"转账", "转账", "转入", "还款"},
            {"收款", "收款", "退款", "到账", "收钱"}
    };

    private BillImport() {
    }

    public static Result parse(byte[] data) {
        Result r = new Result();
        try {
            String text = decode(data);
            List<String> lines = splitLines(text);
            parseLines(lines, r);
        } catch (Exception e) {
            r.error = "解析失败：" + e.getMessage();
        }
        return r;
    }

    // ------------------------------------------------------------- 解码 / 切行

    private static String decode(byte[] data) {
        Charset utf8 = Charset.forName("UTF-8");
        CharsetDecoder dec = utf8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return dec.decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            String[] fallbacks = {"GB18030", "GBK", "Big5"};
            for (String cs : fallbacks) {
                try {
                    return new String(data, Charset.forName(cs));
                } catch (Exception ignore) {
                    // 换下一个
                }
            }
            return new String(data, utf8);
        }
    }

    private static List<String> splitLines(String text) {
        List<String> out = new ArrayList<>();
        int from = 0;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);
            if (ch == '\n' || ch == '\r') {
                out.add(text.substring(from, i));
                if (ch == '\r' && i + 1 < len && text.charAt(i + 1) == '\n') {
                    i++;
                }
                from = i + 1;
            }
        }
        if (from < len) {
            out.add(text.substring(from));
        }
        return out;
    }

    // ------------------------------------------------------------------ 解析

    private static void parseLines(List<String> lines, Result r) {
        int headerIdx = -1;
        for (int i = 0; i < lines.size() && i < 80; i++) {
            String l = lines.get(i);
            if (l == null) {
                continue;
            }
            if (l.contains("金额") && (l.contains("收/支") || l.contains("收支")
                    || l.contains("交易时间") || l.contains("交易对方"))) {
                headerIdx = i;
                break;
            }
        }
        if (headerIdx < 0) {
            r.error = "没认出账单表头，请确认是微信 / 支付宝导出的 CSV 账单文件。";
            return;
        }

        // 判断来源
        StringBuilder head = new StringBuilder();
        for (int i = 0; i <= Math.min(headerIdx, 40); i++) {
            head.append(lines.get(i)).append('\n');
        }
        String headText = head.toString();
        if (headText.contains("微信")) {
            r.source = "微信";
        } else if (headText.contains("支付宝")) {
            r.source = "支付宝";
        }

        List<String> col = splitCsv(lines.get(headerIdx));
        int iTime = findCol(col, "交易时间", "付款时间", "创建时间", "交易创建时间", "时间");
        int iAmt = findColStarts(col, "金额");
        int iDir = findCol(col, "收/支", "收支", "收付", "资金状态");
        int iCounter = findCol(col, "交易对方", "对方账号", "对方");
        int iProduct = findCol(col, "商品说明", "商品名称", "商品");
        int iStatus = findCol(col, "交易状态", "当前状态", "状态");
        int iRemark = findCol(col, "备注");

        if (iTime < 0 || iAmt < 0) {
            r.error = "账单里找不到「交易时间」或「金额」列。";
            return;
        }

        for (int i = headerIdx + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null) {
                continue;
            }
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("-")) {
                break; // 表尾分隔线
            }
            List<String> c = splitCsv(line);
            if (c.size() <= iAmt) {
                r.skipped++;
                continue;
            }

            String dir = cell(c, iDir);
            if (dir.contains("不计收支") || dir.equals("不计")) {
                r.skipped++;
                continue;
            }
            String status = cell(c, iStatus);
            if (status.contains("失败") || status.contains("关闭") || status.contains("撤销")
                    || status.contains("已过期") || status.contains("未支付") || status.contains("待支付")) {
                r.skipped++;
                continue;
            }

            double amount;
            try {
                amount = parseAmount(cell(c, iAmt));
            } catch (Exception e) {
                r.skipped++;
                continue;
            }
            if (amount == 0d) {
                r.skipped++;
                continue;
            }

            int type;
            if (dir.contains("收入") || dir.contains("收")) {
                type = Db.TYPE_INCOME;
            } else if (dir.contains("支出") || dir.contains("支")) {
                type = Db.TYPE_EXPENSE;
            } else {
                type = amount < 0 ? Db.TYPE_EXPENSE : Db.TYPE_EXPENSE;
            }
            if (dir.isEmpty() && amount > 0) {
                // 没有收/支列时，按金额符号判断
                type = Db.TYPE_EXPENSE;
            }

            // 支出侧的退款 / 关闭，钱没真正花出去，跳过
            if (type == Db.TYPE_EXPENSE && status.contains("退款")) {
                r.skipped++;
                continue;
            }

            long ts = parseTime(cell(c, iTime));
            if (ts <= 0L) {
                r.skipped++;
                continue;
            }

            String counter = cell(c, iCounter);
            String product = cell(c, iProduct);
            String remark = cell(c, iRemark);

            Row row = new Row();
            row.ts = ts;
            row.amount = Math.abs(Math.round(amount * 100d) / 100d);
            row.type = type;
            row.category = guessCategory(type, counter + " " + product + " " + remark);
            row.note = buildNote(counter, product, remark);
            r.rows.add(row);

            if (type == Db.TYPE_INCOME) {
                r.incomeCount++;
                r.incomeSum += row.amount;
            } else {
                r.expenseCount++;
                r.expenseSum += row.amount;
            }
        }

        if (r.rows.isEmpty()) {
            r.error = "账单里没有可导入的收支记录（可能都是「不计收支」或非成功交易）。";
        }
    }

    private static String cell(List<String> c, int idx) {
        if (idx < 0 || idx >= c.size()) {
            return "";
        }
        String s = c.get(idx);
        return s == null ? "" : s.trim();
    }

    private static String buildNote(String counter, String product, String remark) {
        String p = clean(product);
        String ct = clean(counter);
        String rm = clean(remark);
        String note;
        if (!p.isEmpty() && !ct.isEmpty() && !p.contains(ct)) {
            note = ct + " · " + p;
        } else if (!p.isEmpty()) {
            note = p;
        } else if (!ct.isEmpty()) {
            note = ct;
        } else {
            note = rm;
        }
        if (note.isEmpty()) {
            note = rm;
        }
        if (note.length() > 40) {
            note = note.substring(0, 40);
        }
        return note;
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.equals("/") || t.equals("-") || t.equals("--")) {
            return "";
        }
        return t;
    }

    public static String guessCategory(int type, String haystack) {
        String h = haystack == null ? "" : haystack.toLowerCase(Locale.CHINA);
        String[][] rules = type == Db.TYPE_INCOME ? INCOME_RULES : EXPENSE_RULES;
        for (String[] rule : rules) {
            for (int i = 1; i < rule.length; i++) {
                if (h.contains(rule[i].toLowerCase(Locale.CHINA))) {
                    return rule[0];
                }
            }
        }
        return type == Db.TYPE_INCOME ? "收款" : "购物";
    }

    private static double parseAmount(String s) {
        String t = s.replace("¥", "").replace("￥", "").replace(",", "")
                .replace(" ", "").replace("\t", "").replace("元", "").trim();
        if (t.isEmpty() || t.equals("-")) {
            return 0d;
        }
        return Double.parseDouble(t);
    }

    private static long parseTime(String s) {
        String t = s.trim();
        if (t.isEmpty()) {
            return 0L;
        }
        for (String p : TIME_PATTERNS) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.CHINA);
                f.setLenient(false);
                Date d = f.parse(t);
                if (d != null) {
                    return d.getTime();
                }
            } catch (Exception ignore) {
                // 换下一个格式
            }
        }
        return 0L;
    }

    // -------------------------------------------------------------- CSV / 列名

    private static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    sb.append(ch);
                }
            } else {
                if (ch == '"') {
                    quoted = true;
                } else if (ch == ',') {
                    out.add(cleanCell(sb.toString()));
                    sb.setLength(0);
                } else {
                    sb.append(ch);
                }
            }
        }
        out.add(cleanCell(sb.toString()));
        return out;
    }

    private static String cleanCell(String s) {
        return s.replace("\uFEFF", "").trim();
    }

    private static String norm(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("（", "(").replace("）", ")")
                .replace(" ", "").replace("\t", "")
                .replace("\uFEFF", "")
                .toLowerCase(Locale.CHINA);
    }

    private static int findCol(List<String> head, String... names) {
        for (String n : names) {
            String target = norm(n);
            for (int i = 0; i < head.size(); i++) {
                if (norm(head.get(i)).equals(target)) {
                    return i;
                }
            }
        }
        for (String n : names) {
            String target = norm(n);
            for (int i = 0; i < head.size(); i++) {
                if (norm(head.get(i)).contains(target)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findColStarts(List<String> head, String prefix) {
        String target = norm(prefix);
        for (int i = 0; i < head.size(); i++) {
            if (norm(head.get(i)).startsWith(target)) {
                return i;
            }
        }
        return -1;
    }
}
