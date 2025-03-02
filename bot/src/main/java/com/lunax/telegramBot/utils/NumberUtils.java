package com.lunax.telegramBot.utils;

public class NumberUtils {

    public static String formatUSD(Double value) {
        if (value < 1_000) {
            return String.format("$%.2f", value);
        } else if (value < 1_000_000) {
            return String.format("$%.1fK", value / 1_000);
        } else if (value < 1_000_000_000) {
            return String.format("$%.1fM", value / 1_000_000);
        } else if (value < 1_000_000_000_000.0) {
            return String.format("$%.1fB", value / 1_000_000_000);
        } else {
            return String.format("$%.1fT", value / 1_000_000_000_000.0);
        }
    }
}