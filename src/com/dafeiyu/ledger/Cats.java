package com.dafeiyu.ledger;

/** 账目分类定义与配色。 */
public final class Cats {

    /** 消费分类。 */
    public static final String[] EXPENSE = {
            "红包", "转账", "购物", "游戏", "生活缴费", "吃饭", "应用会员"
    };

    /** 入账分类。 */
    public static final String[] INCOME = {
            "红包", "转账", "工资", "收款"
    };

    /** 一个页面内保证互不重复的配色（按分类下标取色）。 */
    private static final int[] PALETTE = {
            0xFF4E7CF6, // 蓝
            0xFFF2637B, // 珊瑚红
            0xFFF5A623, // 金
            0xFF2FB98F, // 青绿
            0xFF9B6BF2, // 紫
            0xFF25A0E8, // 天蓝
            0xFFE86A3C, // 橙
            0xFF7FB800, // 草绿
            0xFFD65DB1, // 品红
            0xFF00A5A5, // 蓝绿
            0xFFB07C3F, // 棕
            0xFF6C7A89  // 灰
    };

    private Cats() {
    }

    public static String[] of(int type) {
        return type == Db.TYPE_INCOME ? INCOME : EXPENSE;
    }

    public static int colorOf(int type, String category) {
        String[] arr = of(type);
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(category)) {
                return PALETTE[i % PALETTE.length];
            }
        }
        return PALETTE[PALETTE.length - 1];
    }
}
