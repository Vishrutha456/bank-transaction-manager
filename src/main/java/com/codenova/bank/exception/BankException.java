package com.codenova.bank.exception;

/**
 * Base checked exception for every business-rule violation in the banking domain.
 * Catching this single type lets the UI layer handle all banking errors gracefully
 * without terminating the application.
 */
public class BankException extends Exception {
    public BankException(String message) {
        super(message);
    }
}
