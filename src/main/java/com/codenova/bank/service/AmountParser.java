package com.codenova.bank.service;

import com.codenova.bank.exception.InvalidAmountException;

import java.math.BigDecimal;

/**
 * Converts user text into a safe monetary {@link BigDecimal}. Never uses
 * {@code double}, so values like 0.1 + 0.2 are exact.
 */
public final class AmountParser {

    public static final BigDecimal MAX_SINGLE_AMOUNT = new BigDecimal("10000000.00");

    private AmountParser() {
    }

    public static BigDecimal parse(String text) throws InvalidAmountException {
        if (text == null || text.isBlank()) {
            throw new InvalidAmountException("Amount is required");
        }
        BigDecimal value;
        try {
            value = new BigDecimal(text.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            throw new InvalidAmountException("'" + text.trim() + "' is not a valid number");
        }
        return validate(value);
    }

    public static BigDecimal validate(BigDecimal value) throws InvalidAmountException {
        if (value.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new InvalidAmountException("Amount cannot have more than 2 decimal places");
        }
        if (value.compareTo(MAX_SINGLE_AMOUNT) > 0) {
            throw new InvalidAmountException("Amount exceeds the single-transaction limit of "
                    + MAX_SINGLE_AMOUNT.toPlainString());
        }
        return value.setScale(2);
    }
}
