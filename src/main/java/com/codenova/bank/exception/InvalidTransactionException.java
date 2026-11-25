package com.codenova.bank.exception;

/** Thrown for structurally invalid operations, e.g. transferring to the same account. */
public class InvalidTransactionException extends BankException {
    public InvalidTransactionException(String message) {
        super(message);
    }
}
