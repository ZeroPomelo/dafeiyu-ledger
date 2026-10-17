package com.dafeiyu.ledger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** SQLite 存储：所有账目都放在一张表里，用 type 区分消费 / 入账。 */
public class Db extends SQLiteOpenHelper {

    public static final int TYPE_EXPENSE = 0;
    public static final int TYPE_INCOME = 1;

    private static final String DB_NAME = "dafeiyu_ledger.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "records";

    private static Db instance;

    public static synchronized Db get(Context ctx) {
        if (instance == null) {
            instance = new Db(ctx.getApplicationContext());
        }
        return instance;
    }

    private Db(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "type INTEGER NOT NULL,"
                + "category TEXT NOT NULL,"
                + "amount REAL NOT NULL,"
                + "note TEXT NOT NULL DEFAULT '',"
                + "ts INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_type_ts ON " + TABLE + "(type, ts)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 暂无迁移
    }

    public long insert(int type, String category, double amount, String note, long ts) {
        ContentValues v = new ContentValues();
        v.put("type", type);
        v.put("category", category);
        v.put("amount", amount);
        v.put("note", note == null ? "" : note);
        v.put("ts", ts);
        return getWritableDatabase().insert(TABLE, null, v);
    }

    public void delete(long id) {
        getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
    }

    public void clearAll() {
        getWritableDatabase().delete(TABLE, null, null);
    }

    /** 导入去重键：秒级时间 + 分金额。 */
    public static String key(long ts, double amount) {
        return (ts / 1000L) + "|" + Math.round(amount * 100d);
    }

    /** 某一类全部记录的去重键集合。 */
    public java.util.Set<String> keySet(int type) {
        java.util.Set<String> s = new java.util.HashSet<>();
        for (Record r : list(type)) {
            s.add(key(r.ts, r.amount));
        }
        return s;
    }

    /** 按时间倒序读取某一类的全部记录。 */
    public List<Record> list(int type) {
        return query(type, 0L, Long.MAX_VALUE, 0);
    }

    /** 按时间倒序读取某一类在 [from, to) 内的记录。limit<=0 表示不限。 */
    public List<Record> query(int type, long from, long to, int limit) {
        List<Record> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().query(TABLE,
                    new String[]{"id", "type", "category", "amount", "note", "ts"},
                    "type=? AND ts>=? AND ts<?",
                    new String[]{String.valueOf(type), String.valueOf(from), String.valueOf(to)},
                    null, null, "ts DESC, id DESC",
                    limit > 0 ? String.valueOf(limit) : null);
            while (c.moveToNext()) {
                out.add(new Record(c.getLong(0), c.getInt(1), c.getString(2),
                        c.getDouble(3), c.getString(4), c.getLong(5)));
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return out;
    }

    /** 某一类在所有时间里的总金额。 */
    public double total(int type) {
        return sum(type, 0L, Long.MAX_VALUE);
    }

    /** 某一类在 [from, to) 内的总金额。 */
    public double sum(int type, long from, long to) {
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT IFNULL(SUM(amount),0) FROM " + TABLE + " WHERE type=? AND ts>=? AND ts<?",
                    new String[]{String.valueOf(type), String.valueOf(from), String.valueOf(to)});
            if (c.moveToFirst()) {
                return c.getDouble(0);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return 0d;
    }

    /** 某一类在 [from, to) 内的记录条数。 */
    public int count(int type, long from, long to) {
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT COUNT(*) FROM " + TABLE + " WHERE type=? AND ts>=? AND ts<?",
                    new String[]{String.valueOf(type), String.valueOf(from), String.valueOf(to)});
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return 0;
    }

    /**
     * 按分类汇总 [from, to) 内的金额，金额从大到小排序。
     * 返回二维结构：每项为 {分类名, 金额}。
     */
    public List<Object[]> sumByCategory(int type, long from, long to) {
        List<Object[]> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT category, SUM(amount) AS s FROM " + TABLE
                            + " WHERE type=? AND ts>=? AND ts<? GROUP BY category ORDER BY s DESC",
                    new String[]{String.valueOf(type), String.valueOf(from), String.valueOf(to)});
            while (c.moveToNext()) {
                out.add(new Object[]{c.getString(0), c.getDouble(1)});
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return out;
    }
}
