package com.codenova.bank.exception;

import java.math.BigDecimal;

/** Thrown when a withdrawal or transfer would break an account's balance rules. */
public class InsufficientFundsException extends BankException {
    private final BigDecimal requested;
    private final BigDecimal available;

    public InsufficientFundsException(String accountId, BigDecimal requested, BigDecimal available) {
        super(String.format("Insufficient funds in %s: requested %s, available to withdraw %s",
                accountId, requested.toPlainString(), available.toPlainString()));
        this.requested = requested;
        this.available = available;
    }

    public BigDecimal getRequested() { return requested; }
    public BigDecimal getAvailable() { return available; }
}
