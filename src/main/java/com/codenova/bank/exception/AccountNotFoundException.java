package com.codenova.bank.exception;

/** Thrown when an operation references an account number that does not exist. */
public class AccountNotFoundException extends BankException {
    public AccountNotFoundException(String accountId) {
        super("Account not found: " + accountId);
    }
}
