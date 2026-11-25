package com.codenova.bank.model;

/** Every kind of ledger entry an account can record. */
public enum TransactionType {
    OPENING_DEPOSIT,
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER_IN,
    TRANSFER_OUT,
    INTEREST,
    FEE
}
