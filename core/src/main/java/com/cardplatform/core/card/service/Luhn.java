package com.cardplatform.core.card.service;

// The check digit on every card number: catches any single mistyped digit.
public final class Luhn {

    private Luhn() {
    }

    public static int checkDigit(String digitsWithoutCheck) {
        int sum = 0;
        // Counting from the right, the digit next to the check digit is the first one doubled
        boolean doubleIt = true;
        for (int i = digitsWithoutCheck.length() - 1; i >= 0; i--) {
            int digit = digitsWithoutCheck.charAt(i) - '0';
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return (10 - sum % 10) % 10;
    }

    public static boolean isValid(String number) {
        if (number == null || !number.matches("\\d{12,19}")) {
            return false;
        }
        int last = number.length() - 1;
        return checkDigit(number.substring(0, last)) == number.charAt(last) - '0';
    }
}