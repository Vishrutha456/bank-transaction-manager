package com.codenova.bank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable ledger entry. {@code sequence} is a global, strictly increasing number
 * so statements are always ordered exactly as operations happened, even when two
 * entries share the same timestamp.
 *
 * @param amount       always positive; the {@code type} says whether money came in or out
 * @param balanceAfter account balance immediately after this entry was applied
 * @param reference    counterparty account for transfers, otherwise empty
 */
public record Transaction(
        long sequence,
        String accountId,
        LocalDateTime timestamp,
        TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String reference,
        String description) {

    /** Signed amount: positive for credits, negative for debits. */
    public BigDecimal signedAmount() {
        return switch (type) {
            case WITHDRAWAL, TRANSFER_OUT, FEE -> amount.negate();
            default -> amount;
        };
    }
}
