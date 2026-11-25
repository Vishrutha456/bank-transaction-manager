package com.codenova.bank.service;

import com.codenova.bank.exception.AccountNotFoundException;
import com.codenova.bank.exception.BankException;
import com.codenova.bank.exception.InsufficientFundsException;
import com.codenova.bank.exception.InvalidAmountException;
import com.codenova.bank.exception.InvalidTransactionException;
import com.codenova.bank.model.Account;
import com.codenova.bank.model.AccountType;
import com.codenova.bank.model.CheckingAccount;
import com.codenova.bank.model.SavingsAccount;
import com.codenova.bank.model.Transaction;
import com.codenova.bank.model.TransactionType;
import com.codenova.bank.repository.AccountRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Application service (controller logic) for all account operations.
 * Every public mutating method validates input, applies the change, records a
 * ledger entry and persists — or throws a {@link BankException} and leaves the
 * state untouched.
 */
public class BankService {

    private final AccountRepository repository;
    private final Clock clock;
    private final AtomicLong sequence;

    public BankService(AccountRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    public BankService(AccountRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
        this.sequence = new AtomicLong(repository.lastSequence());
    }

    // ------------------------------------------------------------------ accounts

    public Account openAccount(AccountType type, String holderName, BigDecimal openingDeposit)
            throws BankException {
        if (holderName == null || holderName.isBlank()) {
            throw new InvalidTransactionException("Account holder name is required");
        }
        BigDecimal amount = AmountParser.validate(openingDeposit);
        if (type == AccountType.SAVINGS && amount.compareTo(SavingsAccount.MINIMUM_BALANCE) < 0) {
            throw new InvalidAmountException("Savings accounts need an opening deposit of at least "
                    + SavingsAccount.MINIMUM_BALANCE.toPlainString());
        }
        String id = nextAccountId(type);
        Account account = type == AccountType.SAVINGS
                ? new SavingsAccount(id, holderName.trim(), BigDecimal.ZERO)
                : new CheckingAccount(id, holderName.trim(), BigDecimal.ZERO);
        account.credit(amount);
        record(account, TransactionType.OPENING_DEPOSIT, amount, "", "Account opened");
        repository.add(account);
        repository.save();
        return account;
    }

    public Account getAccount(String accountId) throws AccountNotFoundException {
        return repository.findById(accountId);
    }

    public Collection<Account> listAccounts() {
        return repository.findAll();
    }

    // ------------------------------------------------------------------ money movement

    public Transaction deposit(String accountId, BigDecimal amount) throws BankException {
        BigDecimal value = AmountParser.validate(amount);
        Account account = repository.findById(accountId);
        account.getLock().lock();
        try {
            account.credit(value);
            Transaction tx = record(account, TransactionType.DEPOSIT, value, "", "Cash deposit");
            repository.save();
            return tx;
        } finally {
            account.getLock().unlock();
        }
    }

    public Transaction withdraw(String accountId, BigDecimal amount) throws BankException {
        BigDecimal value = AmountParser.validate(amount);
        Account account = repository.findById(accountId);
        account.getLock().lock();
        try {
            account.debit(value);
            Transaction tx = record(account, TransactionType.WITHDRAWAL, value, "", "Cash withdrawal");
            repository.save();
            return tx;
        } finally {
            account.getLock().unlock();
        }
    }

    /**
     * Moves money between two accounts as a single atomic unit.
     * <ol>
     *   <li>Both accounts are locked in a fixed (alphabetical) order, so two
     *       opposite transfers can never deadlock.</li>
     *   <li>The debit is validated first; if it fails nothing has changed.</li>
     *   <li>If the credit fails after the debit succeeded, the debit is rolled back.</li>
     * </ol>
     */
    public void transfer(String fromId, String toId, BigDecimal amount) throws BankException {
        BigDecimal value = AmountParser.validate(amount);
        Account from = repository.findById(fromId);
        Account to = repository.findById(toId);
        if (from.getAccountId().equals(to.getAccountId())) {
            throw new InvalidTransactionException("Cannot transfer to the same account");
        }

        Account first = from.getAccountId().compareTo(to.getAccountId()) < 0 ? from : to;
        Account second = first == from ? to : from;
        first.getLock().lock();
        second.getLock().lock();
        try {
            from.debit(value); // throws InsufficientFundsException before any change
            try {
                to.credit(value);
            } catch (InvalidAmountException | RuntimeException e) {
                from.restoreBalance(from.getBalance().add(value)); // roll back the debit
                throw new InvalidTransactionException("Transfer rolled back: " + e.getMessage());
            }
            record(from, TransactionType.TRANSFER_OUT, value, to.getAccountId(),
                    "Transfer to " + to.getAccountId());
            record(to, TransactionType.TRANSFER_IN, value, from.getAccountId(),
                    "Transfer from " + from.getAccountId());
            repository.save();
        } finally {
            second.getLock().unlock();
            first.getLock().unlock();
        }
    }

    /**
     * Month-end processing: credits interest to savings accounts and charges the
     * maintenance fee on checking accounts below the waiver balance.
     *
     * @return the ledger entries created
     */
    public List<Transaction> runMonthEnd() {
        List<Transaction> created = new ArrayList<>();
        for (Account account : repository.findAll()) {
            account.getLock().lock();
            try {
                if (account instanceof SavingsAccount savings) {
                    BigDecimal interest = savings.monthlyInterest();
                    if (interest.signum() > 0) {
                        savings.credit(interest);
                        created.add(record(savings, TransactionType.INTEREST, interest, "",
                                "Monthly interest @ 4% p.a."));
                    }
                } else if (account instanceof CheckingAccount checking && checking.isMonthlyFeeDue()) {
                    try {
                        checking.debit(CheckingAccount.MONTHLY_FEE);
                        created.add(record(checking, TransactionType.FEE, CheckingAccount.MONTHLY_FEE, "",
                                "Monthly maintenance fee"));
                    } catch (InsufficientFundsException e) {
                        // Account is at its overdraft limit: fee is skipped, never forced.
                    }
                }
            } catch (InvalidAmountException e) {
                throw new IllegalStateException("Unexpected invalid amount during month-end", e);
            } finally {
                account.getLock().unlock();
            }
        }
        repository.save();
        return created;
    }

    // ------------------------------------------------------------------ helpers

    private Transaction record(Account account, TransactionType type, BigDecimal amount,
                               String reference, String description) {
        Transaction tx = new Transaction(sequence.incrementAndGet(), account.getAccountId(),
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS), type,
                Account.money(amount), account.getBalance(), reference, description);
        account.record(tx);
        return tx;
    }

    private String nextAccountId(AccountType type) {
        String prefix = type == AccountType.SAVINGS ? "SAV" : "CHK";
        int n = 1001;
        while (repository.exists(prefix + n)) {
            n++;
        }
        return prefix + n;
    }
}
