package com.example.tagihan.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public class CurrencyUtil {

    public static String formatRupiah(Long amount) {
        if (amount == null) {
            return "Rp0";
        }

        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');

        DecimalFormat decimalFormat = new DecimalFormat("###,###", symbols);

        return "Rp" + decimalFormat.format(amount);
    }

}
