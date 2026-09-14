package cn.ff26710.carsharingapp.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class AmountUtil {

    public static Integer yuanToFenInt(BigDecimal amount) {
        if (amount == null) {
            return 0;
        }
        return amount.movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    public static Long yuanToFenLong(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public static BigDecimal fenToYuan(Integer fen) {
        if (fen == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(fen).movePointLeft(2);
    }

    public static BigDecimal fenLongToYuan(Long fen) {
        if (fen == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(fen).movePointLeft(2);
    }
}
