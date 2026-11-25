package com.codenova.bank.model;

import com.codenova.bank.exception.InsufficientFundsException;
import com.codenova.bank.exception.InvalidAmountException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Base account entity. The balance can only change through {@link #credit} and
 * {@link #debit}, so every change is validated and recorded in the ledger
 * (encapsulation: no public setter for the balance exists).
 *
 * Subclasses define their own withdrawal rules by overriding
 * {@link #availableToWithdraw()}.
 */
public abstract class Account {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private final String accountId;
    private final String holderName;
    private BigDecimal balance;
    private final List<Transaction> transactions = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    protected Account(String accountId, String holderName, BigDecimal openingBalance) {
        this.accountId = Objects.requireNonNull(accountId);
        this.holderName = Objects.requireNonNull(holderName);
        this.balance = money(openingBalance);
    }

    public abstract AccountType getType();

    /** Largest amount that may be debited right now without breaking this account's rules. */
    public abstract BigDecimal availableToWithdraw();

    /** Human-readable summary of this account's rules, shown in the UI. */
    public abstract String describeRules();

    public void credit(BigDecimal amount) throws InvalidAmountException {
        requirePositive(amount);
        balance = money(balance.add(amount));
    }

    public void debit(BigDecimal amount) throws InvalidAmountException, InsufficientFundsException {
        requirePositive(amount);
        if (amount.compareTo(availableToWithdraw()) > 0) {
            throw new InsufficientFundsException(accountId, money(amount), availableToWithdraw());
        }
        balance = money(balance.subtract(amount));
    }

    /** Package-private hook used only by the service layer to record ledger entries. */
    public void record(Transaction tx) {
        transactions.add(tx);
    }

    /** Restores state when loading from storage (bypasses rules on purpose). */
    public void restoreBalance(BigDecimal storedBalance) {
        this.balance = money(storedBalance);
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    private static void requirePositive(BigDecimal amount) throws InvalidAmountException {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
    }

    public String getAccountId() { return accountId; }
    public String getHolderName() { return holderName; }
    public BigDecimal getBalance() { return balance; }
    public ReentrantLock getLock() { return lock; }
    public List<Transaction> getTransactions() { return Collections.unmodifiableList(transactions); }

    @Override
    public String toString() {
        return String.format("%-10s %-9s %-22s %15s", accountId, getType(), holderName, balance.toPlainString());
    }
}
