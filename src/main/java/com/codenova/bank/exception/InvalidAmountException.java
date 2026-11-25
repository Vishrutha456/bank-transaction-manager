package com.codenova.bank.exception;

/** Thrown for zero, negative, non-numeric or over-precise monetary amounts. */
public class InvalidAmountException extends BankException {
    public InvalidAmountException(String message) {
        super(message);
    }
}
