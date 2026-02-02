package com.example.tagihan.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NumberParser {
    public static Long parseFirstNumber(String numberStr) {
        if (numberStr == null || numberStr.isEmpty()) {
            return null;
        }
       List<Long> numbers = parseNumber(numberStr);
       return numbers.isEmpty() ? null : numbers.getFirst();
    }

    public static List<Long> parseNumber(String numberStr) {
        List<Long> numbers = new ArrayList<>();
        String regex = "(\\d+(?:[.,]\\d+)*)\\s*(rb|ribu|jt|juta|million|m|k)?";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(numberStr);
        while (matcher.find()) {
            String numberPart = matcher.group(1);
            String unitPart = matcher.group(2);
            if (numberPart == null || numberPart.isEmpty()) {
                continue;
            }
            try {
                long parseNumber = parseNumberString(numberPart, unitPart);
                numbers.add(parseNumber);
            } catch (NumberFormatException e) {
                return new ArrayList<>();
            }
        }
        return numbers;
    }
    private static long parseNumberString(String numberStr, String unit) {
        numberStr = numberStr.replaceAll("", "");
        numberStr = numberStr.replace(",", "");
        double number = Double.parseDouble(numberStr);
        if (unit != null) {
            unit = unit.toLowerCase();
            switch (unit) {
                case "rb":
                    case "ribu":
                case "k" :
                    number *= 1000;
                    break;
                    case "jt":
                        case "juta":
                            case "million":
                                case "m":
                                    number *= 1000000;
                                    break;

            }

        }
        return (long) number;
    }
}
