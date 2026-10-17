package com.dafeiyu.ledger;

/** 一条账目记录。 */
public class Record {
    public final long id;
    public final int type;        // Db.TYPE_EXPENSE / Db.TYPE_INCOME
    public final String category;
    public final double amount;
    public final String note;
    public final long ts;

    public Record(long id, int type, String category, double amount, String note, long ts) {
        this.id = id;
        this.type = type;
        this.category = category;
        this.amount = amount;
        this.note = note == null ? "" : note;
        this.ts = ts;
    }
}
