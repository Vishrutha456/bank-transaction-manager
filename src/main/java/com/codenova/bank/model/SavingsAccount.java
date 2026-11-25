package com.codenova.bank.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Savings account: earns interest and must always keep a minimum balance.
 */
public class SavingsAccount extends Account {

    public static final BigDecimal MINIMUM_BALANCE = new BigDecimal("1000.00");
    public static final BigDecimal ANNUAL_INTEREST_RATE = new BigDecimal("0.04"); // 4% p.a.

    public SavingsAccount(String accountId, String holderName, BigDecimal openingBalance) {
        super(accountId, holderName, openingBalance);
    }

    @Override
    public AccountType getType() {
        return AccountType.SAVINGS;
    }

    @Override
    public BigDecimal availableToWithdraw() {
        BigDecimal available = getBalance().subtract(MINIMUM_BALANCE);
        return available.signum() > 0 ? money(available) : money(BigDecimal.ZERO);
    }

    /** One month of interest on the current balance (annual rate / 12), rounded to paise. */
    public BigDecimal monthlyInterest() {
        BigDecimal monthlyRate = ANNUAL_INTEREST_RATE.divide(BigDecimal.valueOf(12), MathContext.DECIMAL128);
        return getBalance().multiply(monthlyRate).setScale(SCALE, RoundingMode.HALF_EVEN);
    }

    @Override
    public String describeRules() {
        return "Savings: minimum balance " + MINIMUM_BALANCE.toPlainString()
                + ", interest " + ANNUAL_INTEREST_RATE.movePointRight(2).stripTrailingZeros().toPlainString()
                + "% p.a. credited monthly";
    }
}
