package com.codenova.bank.repository;

import com.codenova.bank.exception.AccountNotFoundException;
import com.codenova.bank.model.Account;
import com.codenova.bank.model.AccountType;
import com.codenova.bank.model.CheckingAccount;
import com.codenova.bank.model.SavingsAccount;
import com.codenova.bank.model.Transaction;
import com.codenova.bank.model.TransactionType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps accounts in memory and persists them to two CSV files:
 * {@code accounts.csv} and {@code transactions.csv}. Writes go to a temp file
 * first and are then moved into place, so a crash mid-write never leaves a
 * half-written data file.
 */
public class CsvAccountRepository implements AccountRepository {

    private static final String ACCOUNTS_HEADER = "account_id,type,holder_name,balance";
    private static final String TX_HEADER =
            "sequence,account_id,timestamp,type,amount,balance_after,reference,description";

    private final Path accountsFile;
    private final Path transactionsFile;
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private long lastSequence;

    public CsvAccountRepository(Path dataDir) {
        this.accountsFile = dataDir.resolve("accounts.csv");
        this.transactionsFile = dataDir.resolve("transactions.csv");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create data directory " + dataDir, e);
        }
        load();
    }

    @Override
    public synchronized void add(Account account) {
        accounts.put(account.getAccountId(), account);
    }

    @Override
    public synchronized Account findById(String accountId) throws AccountNotFoundException {
        Account account = accountId == null ? null : accounts.get(accountId.trim().toUpperCase());
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        return account;
    }

    @Override
    public synchronized boolean exists(String accountId) {
        return accounts.containsKey(accountId);
    }

    @Override
    public synchronized Collection<Account> findAll() {
        return Collections.unmodifiableCollection(new ArrayList<>(accounts.values()));
    }

    @Override
    public long lastSequence() {
        return lastSequence;
    }

    @Override
    public synchronized void save() {
        List<String> accLines = new ArrayList<>();
        accLines.add(ACCOUNTS_HEADER);
        List<Transaction> all = new ArrayList<>();
        for (Account a : accounts.values()) {
            accLines.add(CsvUtil.join(a.getAccountId(), a.getType().name(), a.getHolderName(),
                    a.getBalance().toPlainString()));
            all.addAll(a.getTransactions());
        }
        all.sort(Comparator.comparingLong(Transaction::sequence));
        List<String> txLines = new ArrayList<>();
        txLines.add(TX_HEADER);
        for (Transaction t : all) {
            txLines.add(CsvUtil.join(String.valueOf(t.sequence()), t.accountId(), t.timestamp().toString(),
                    t.type().name(), t.amount().toPlainString(), t.balanceAfter().toPlainString(),
                    t.reference(), t.description()));
        }
        writeAtomically(accountsFile, accLines);
        writeAtomically(transactionsFile, txLines);
    }

    private void load() {
        try {
            if (Files.exists(accountsFile)) {
                List<String> lines = Files.readAllLines(accountsFile, StandardCharsets.UTF_8);
                for (String line : lines.subList(1, lines.size())) {
                    if (line.isBlank()) continue;
                    List<String> f = CsvUtil.parseLine(line);
                    BigDecimal balance = new BigDecimal(f.get(3));
                    Account a = AccountType.valueOf(f.get(1)) == AccountType.SAVINGS
                            ? new SavingsAccount(f.get(0), f.get(2), balance)
                            : new CheckingAccount(f.get(0), f.get(2), balance);
                    accounts.put(a.getAccountId(), a);
                }
            }
            if (Files.exists(transactionsFile)) {
                List<String> lines = Files.readAllLines(transactionsFile, StandardCharsets.UTF_8);
                for (String line : lines.subList(1, lines.size())) {
                    if (line.isBlank()) continue;
                    List<String> f = CsvUtil.parseLine(line);
                    Transaction t = new Transaction(Long.parseLong(f.get(0)), f.get(1),
                            LocalDateTime.parse(f.get(2)), TransactionType.valueOf(f.get(3)),
                            new BigDecimal(f.get(4)), new BigDecimal(f.get(5)), f.get(6), f.get(7));
                    Account owner = accounts.get(t.accountId());
                    if (owner != null) {
                        owner.record(t);
                    }
                    lastSequence = Math.max(lastSequence, t.sequence());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load bank data", e);
        }
    }

    private static void writeAtomically(Path target, List<String> lines) {
        try {
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save " + target, e);
        }
    }
}
