package com.example.tagihan.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public class CurrencyUtil {

    public static String formatRupiah(Long amount) {
        if (amount == null) return "Rp0";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat df = new DecimalFormat("Rp#,##0", symbols);
        return df.format(amount);
    }

}
