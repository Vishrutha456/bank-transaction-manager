package com.codenova.bank;

import com.codenova.bank.exception.AccountNotFoundException;
import com.codenova.bank.exception.InsufficientFundsException;
import com.codenova.bank.exception.InvalidAmountException;
import com.codenova.bank.exception.InvalidTransactionException;
import com.codenova.bank.model.Account;
import com.codenova.bank.model.AccountType;
import com.codenova.bank.model.Transaction;
import com.codenova.bank.model.TransactionType;
import com.codenova.bank.repository.CsvAccountRepository;
import com.codenova.bank.service.AmountParser;
import com.codenova.bank.service.BankService;
import com.codenova.bank.service.StatementExporter;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Zero-dependency test suite (no JUnit download needed).
 * Run: ./test.sh  (or test.bat on Windows)
 */
public class BankServiceTest {

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        test("Savings account enforces minimum opening deposit", () -> {
            BankService s = fresh();
            expect(InvalidAmountException.class, () -> s.openAccount(AccountType.SAVINGS, "A", bd("999.99")));
            Account a = s.openAccount(AccountType.SAVINGS, "A", bd("1000"));
            check(a.getBalance().equals(bd("1000.00")), "balance should be 1000.00");
        });

        test("Deposit and withdraw update balance exactly", () -> {
            BankService s = fresh();
            Account a = s.openAccount(AccountType.CHECKING, "A", bd("100.10"));
            s.deposit(a.getAccountId(), bd("0.20"));
            s.withdraw(a.getAccountId(), bd("0.10"));
            check(a.getBalance().equals(bd("100.20")), "expected 100.20 but was " + a.getBalance());
        });

        test("Savings withdrawal cannot break minimum balance", () -> {
            BankService s = fresh();
            Account a = s.openAccount(AccountType.SAVINGS, "A", bd("1500"));
            expect(InsufficientFundsException.class, () -> s.withdraw(a.getAccountId(), bd("500.01")));
            s.withdraw(a.getAccountId(), bd("500.00"));
            check(a.getBalance().equals(bd("1000.00")), "should stop exactly at minimum");
        });

        test("Checking overdraft limit is enforced", () -> {
            BankService s = fresh();
            Account a = s.openAccount(AccountType.CHECKING, "A", bd("100"));
            s.withdraw(a.getAccountId(), bd("5100"));
            check(a.getBalance().equals(bd("-5000.00")), "should reach -5000.00");
            expect(InsufficientFundsException.class, () -> s.withdraw(a.getAccountId(), bd("0.01")));
        });

        test("Invalid amounts are rejected", () -> {
            expect(InvalidAmountException.class, () -> AmountParser.parse("-5"));
            expect(InvalidAmountException.class, () -> AmountParser.parse("0"));
            expect(InvalidAmountException.class, () -> AmountParser.parse("abc"));
            expect(InvalidAmountException.class, () -> AmountParser.parse("1.234"));
            expect(InvalidAmountException.class, () -> AmountParser.parse(""));
            check(AmountParser.parse("1,250.5").equals(bd("1250.50")), "commas should be accepted");
        });

        test("Unknown account throws AccountNotFoundException", () -> {
            BankService s = fresh();
            expect(AccountNotFoundException.class, () -> s.deposit("NOPE1", bd("10")));
        });

        test("Transfer moves money and records both sides", () -> {
            BankService s = fresh();
            Account a = s.openAccount(AccountType.SAVINGS, "A", bd("5000"));
            Account b = s.openAccount(AccountType.CHECKING, "B", bd("100"));
            s.transfer(a.getAccountId(), b.getAccountId(), bd("1234.56"));
            check(a.getBalance().equals(bd("3765.44")), "sender balance");
            check(b.getBalance().equals(bd("1334.56")), "receiver balance");
            check(last(a).type() == TransactionType.TRANSFER_OUT, "sender ledger");
            check(last(b).type() == TransactionType.TRANSFER_IN, "receiver ledger");
            check(last(b).reference().equals(a.getAccountId()), "receiver reference");
        });

        test("Failed transfer leaves both balances untouched", () -> {
            BankService s = fresh();
            Account a = s.openAccount(AccountType.SAVINGS, "A", bd("1200"));
            Account b = s.openAccount(AccountType.SAVINGS, "B", bd("1200"));
            int before = a.getTransactions().size() + b.getTransactions().size();
            expect(InsufficientFundsException.class, () -> s.transfer(a.getAccountId(), b.getAccountId(), bd("300")));
            expect(InvalidTransactionException.class, () -> s.transfer(a.getAccountId(), a.getAccountId(), bd("1")));
            check(a.getBalance().equals(bd("1200.00")) && b.getBalance().equals(bd("1200.00")), "balances changed");
            check(a.getTransactions().size() + b.getTransactions().size() == before, "ledger changed");
        });

        test("Concurrent opposite transfers are atomic and never deadlock", () -> {
            BankService s = fresh();
            Account a = s.openAccount(AccountType.CHECKING, "A", bd("10000"));
            Account b = s.openAccount(AccountType.CHECKING, "B", bd("10000"));
            ExecutorService pool = Executors.newFixedThreadPool(8);
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < 400; i++) {
                final boolean ab = i % 2 == 0;
                pool.submit(() -> {
                    start.await();
                    if (ab) s.transfer(a.getAccountId(), b.getAccountId(), bd("7.77"));
                    else s.transfer(b.getAccountId(), a.getAccountId(), bd("3.33"));
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            check(pool.awaitTermination(30, TimeUnit.SECONDS), "possible deadlock: transfers did not finish");
            BigDecimal total = a.getBalance().add(b.getBalance());
            check(total.equals(bd("20000.00")), "money created or lost! total=" + total);
            check(a.getBalance().equals(bd("10000.00").subtract(bd("4.44").multiply(bd("200")))), "A=" + a.getBalance());
        });

        test("Month-end credits interest and charges low-balance fee", () -> {
            BankService s = fresh();
            Account sav = s.openAccount(AccountType.SAVINGS, "A", bd("12000"));
            Account chk = s.openAccount(AccountType.CHECKING, "B", bd("500"));
            s.runMonthEnd();
            check(sav.getBalance().equals(bd("12040.00")), "interest: " + sav.getBalance());
            check(chk.getBalance().equals(bd("450.00")), "fee: " + chk.getBalance());
        });

        test("Data persists across application restarts", () -> {
            Path dir = Files.createTempDirectory("bank-test");
            BankService s1 = new BankService(new CsvAccountRepository(dir));
            Account a = s1.openAccount(AccountType.SAVINGS, "Doe, Jane \"JD\"", bd("2000"));
            s1.deposit(a.getAccountId(), bd("10.05"));
            BankService s2 = new BankService(new CsvAccountRepository(dir));
            Account reloaded = s2.getAccount(a.getAccountId());
            check(reloaded.getHolderName().equals("Doe, Jane \"JD\""), "name with comma/quotes survives CSV");
            check(reloaded.getBalance().equals(bd("2010.05")), "balance survives restart");
            check(reloaded.getTransactions().size() == 2, "ledger survives restart");
            Transaction t = s2.deposit(a.getAccountId(), bd("1"));
            check(t.sequence() == 3, "sequence continues after restart, got " + t.sequence());
        });

        test("Exported statement is in exact chronological order", () -> {
            BankService s = fresh();
            Account a = s.openAccount(AccountType.CHECKING, "A", bd("100"));
            for (int i = 1; i <= 5; i++) s.deposit(a.getAccountId(), bd(String.valueOf(i)));
            Path dir = Files.createTempDirectory("stmt");
            Path csv = new StatementExporter().exportCsv(a, dir);
            List<String> lines = Files.readAllLines(csv);
            check(lines.size() == 7, "header + 6 rows expected");
            long prev = 0;
            for (String line : lines.subList(1, lines.size())) {
                long seq = Long.parseLong(line.split(",")[0]);
                check(seq > prev, "out of order at " + seq);
                prev = seq;
            }
            check(lines.get(6).endsWith(",5.00,115.00"), "last row: " + lines.get(6));
            check(Files.exists(new StatementExporter().exportText(a, dir)), "txt exported");
        });

        System.out.println();
        System.out.println("  " + passed + " passed, " + failures.size() + " failed");
        if (!failures.isEmpty()) {
            failures.forEach(f -> System.out.println("  FAILED: " + f));
            System.exit(1);
        }
    }

    // ---------------------------------------------------------------- tiny test framework

    @FunctionalInterface
    interface Body { void run() throws Exception; }

    private static void test(String name, Body body) {
        try {
            body.run();
            passed++;
            System.out.println("  \u001B[32mPASS\u001B[0m  " + name);
        } catch (Throwable t) {
            failures.add(name + " -> " + t);
            System.out.println("  \u001B[31mFAIL\u001B[0m  " + name + "  (" + t.getMessage() + ")");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expect(Class<? extends Throwable> type, Body body) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) return;
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + t);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
    }

    private static BankService fresh() throws Exception {
        return new BankService(new CsvAccountRepository(Files.createTempDirectory("bank-test")));
    }

    private static Transaction last(Account a) {
        return a.getTransactions().get(a.getTransactions().size() - 1);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
