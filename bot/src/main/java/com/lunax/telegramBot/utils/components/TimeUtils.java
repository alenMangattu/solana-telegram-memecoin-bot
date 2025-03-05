package com.lunax.telegramBot.utils.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeUtils {
    private static final Logger log = LoggerFactory.getLogger(TimeUtils.class);

    /**
     * Validates if the selected time frequency is appropriate for the given time
     * period.
     * Returns an adjusted frequency if needed.
     * 
     * @param frequency The current frequency (e.g., "15M", "1H", "1D")
     * @param period    The selected time period (e.g., "1D", "15D", "6M")
     * @return The adjusted frequency if needed, or the original frequency if valid
     */
    public static String validateTimeFrequency(String frequency, String period) {
        // Parse period
        int periodValue = Integer.parseInt(period.substring(0, period.length() - 1));
        char periodUnit = period.charAt(period.length() - 1);

        // Parse frequency
        int freqValue = 0;
        char freqUnit = frequency.charAt(frequency.length() - 1);
        try {
            freqValue = Integer.parseInt(frequency.substring(0, frequency.length() - 1));
        } catch (NumberFormatException e) {
            freqValue = 1; // Default value
        }

        log.info("Validating time frequency: {} for period: {}", frequency, period);

        // Calculate approximate hours for both
        int periodHours = periodToHours(periodValue, periodUnit);
        int freqHours = freqToHours(freqValue, freqUnit);

        // If frequency is larger than period, adjust it
        if (freqHours >= periodHours) {
            log.warn("Frequency {} is greater than or equal to period {}", frequency, period);
            return null; // Indicate that the frequency is not valid
        }

        // Return the frequency with the correct unit
        return frequency; // Return the original frequency if valid
    }

    // Convert period to approximate hours
    private static int periodToHours(int value, char unit) {
        switch (unit) {
            case 'H':
                return value;
            case 'D':
                return value * 24;
            case 'M':
                return value * 30 * 24; // Approximate month as 30 days
            case 'Y':
                return value * 365 * 24; // Approximate year as 365 days
            default:
                return value * 24; // Default to days
        }
    }

    // Convert frequency to approximate hours
    private static int freqToHours(int value, char unit) {
        switch (unit) {
            case 'M':
                return value / 60; // Convert to fraction of hours
            case 'H':
            case 'h':
                return value;
            case 'D':
            case 'd':
                return value * 24;
            case 'm':
                return value / 60; // Convert to fraction of hours
            default:
                return value; // Default to hours
        }
    }

    /**
     * Format a Unix timestamp to a human-readable date string
     * 
     * @param timestamp Unix timestamp in seconds
     * @return Formatted date string
     */
    public static String formatTimestamp(long timestamp) {
        try {
            // If timestamp is 0, return a special message
            if (timestamp == 0) {
                return "Not available";
            }

            // If timestamp is in milliseconds (13 digits), no conversion needed
            // If timestamp is in seconds (10 digits), convert to milliseconds
            long timestampMs = timestamp;
            if (String.valueOf(timestamp).length() <= 10) {
                timestampMs = timestamp * 1000;
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
            return sdf.format(new Date(timestampMs));
        } catch (Exception e) {
            log.error("Error formatting timestamp: {}", e.getMessage());
            return "Unknown";
        }
    }
}