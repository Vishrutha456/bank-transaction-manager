package com.codenova.bank.model;

import java.math.BigDecimal;

/**
 * Checking (current) account: no interest, but allows an overdraft down to a fixed
 * limit. A monthly maintenance fee applies while the balance is below the
 * fee-waiver threshold.
 */
public class CheckingAccount extends Account {

    public static final BigDecimal OVERDRAFT_LIMIT = new BigDecimal("5000.00");
    public static final BigDecimal MONTHLY_FEE = new BigDecimal("50.00");
    public static final BigDecimal FEE_WAIVER_BALANCE = new BigDecimal("2000.00");

    public CheckingAccount(String accountId, String holderName, BigDecimal openingBalance) {
        super(accountId, holderName, openingBalance);
    }

    @Override
    public AccountType getType() {
        return AccountType.CHECKING;
    }

    @Override
    public BigDecimal availableToWithdraw() {
        return money(getBalance().add(OVERDRAFT_LIMIT));
    }

    public boolean isMonthlyFeeDue() {
        return getBalance().compareTo(FEE_WAIVER_BALANCE) < 0;
    }

    @Override
    public String describeRules() {
        return "Checking: overdraft up to " + OVERDRAFT_LIMIT.toPlainString()
                + ", monthly fee " + MONTHLY_FEE.toPlainString()
                + " if balance below " + FEE_WAIVER_BALANCE.toPlainString();
    }
}
