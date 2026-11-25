package com.codenova.bank.ui;

import com.codenova.bank.exception.BankException;
import com.codenova.bank.model.Account;
import com.codenova.bank.model.AccountType;
import com.codenova.bank.model.Transaction;
import com.codenova.bank.service.AmountParser;
import com.codenova.bank.service.BankService;
import com.codenova.bank.service.StatementExporter;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.codenova.bank.ui.ConsoleUi.*;

/**
 * Non-interactive walkthrough of every feature, including the error paths.
 * Run with {@code --demo}. Useful for screenshots and for reviewers.
 */
public class DemoRunner {

    private final BankService service;
    private final Path statementsDir;

    public DemoRunner(BankService service, Path statementsDir) {
        this.service = service;
        this.statementsDir = statementsDir;
    }

    public void run() throws IOException {
        banner();
        if (!service.listAccounts().isEmpty()) {
            info("demo-data/ already has accounts. Delete the demo-data folder to replay from scratch.");
        }

        heading("1. Opening accounts");
        Account riya = step(() -> service.openAccount(AccountType.SAVINGS, "Riya Sharma", bd("25000.00")));
        Account arjun = step(() -> service.openAccount(AccountType.CHECKING, "Arjun Mehta", bd("8000.00")));
        Account kabir = step(() -> service.openAccount(AccountType.SAVINGS, "Kabir Khan", bd("1500.00")));
        for (Account a : new Account[]{riya, arjun, kabir}) {
            if (a != null) ok(a.getAccountId() + " opened for " + a.getHolderName() + " -> " + a.getBalance());
        }
        expectError("Savings opening deposit below minimum",
                () -> service.openAccount(AccountType.SAVINGS, "Low Balance", bd("500")));

        heading("2. Deposits & withdrawals");
        step(() -> service.deposit(riya.getAccountId(), bd("4999.99")));
        ok("Deposited 4999.99 into " + riya.getAccountId() + " -> " + riya.getBalance());
        step(() -> service.withdraw(arjun.getAccountId(), bd("10500.00")));
        ok("Withdrew 10500.00 from " + arjun.getAccountId() + " (uses overdraft) -> " + arjun.getBalance());
        expectError("Savings withdrawal breaking 1000.00 minimum",
                () -> service.withdraw(kabir.getAccountId(), bd("600")));
        expectError("Checking withdrawal beyond 5000.00 overdraft",
                () -> service.withdraw(arjun.getAccountId(), bd("3000")));

        heading("3. Input validation");
        expectError("Negative amount", () -> AmountParser.parse("-250"));
        expectError("Non-numeric amount", () -> AmountParser.parse("ten thousand"));
        expectError("Three decimal places", () -> AmountParser.parse("10.505"));
        expectError("Unknown account", () -> service.deposit("SAV9999", bd("100")));

        heading("4. Atomic fund transfers");
        step(() -> { service.transfer(riya.getAccountId(), arjun.getAccountId(), bd("7500.50")); return null; });
        ok("Transferred 7500.50 " + riya.getAccountId() + " -> " + arjun.getAccountId());
        BigDecimal before1 = kabir.getBalance();
        BigDecimal before2 = riya.getBalance();
        expectError("Transfer exceeding available funds",
                () -> { service.transfer(kabir.getAccountId(), riya.getAccountId(), bd("900")); return null; });
        info("Balances unchanged after failed transfer: " + kabir.getAccountId() + "=" + kabir.getBalance()
                + " (was " + before1 + "), " + riya.getAccountId() + "=" + riya.getBalance() + " (was " + before2 + ")");
        expectError("Transfer to the same account",
                () -> { service.transfer(riya.getAccountId(), riya.getAccountId(), bd("10")); return null; });

        heading("5. Month-end processing");
        for (Transaction t : service.runMonthEnd()) {
            ok(t.accountId() + "  " + t.type() + "  " + t.signedAmount().toPlainString()
                    + "  -> balance " + t.balanceAfter());
        }

        heading("6. Precision check (BigDecimal)");
        info("double : 0.1 + 0.2 = " + (0.1 + 0.2));
        info("BigDecimal : 0.10 + 0.20 = " + bd("0.10").add(bd("0.20")));

        heading("7. Final balances");
        System.out.println(DIM + String.format("  %-10s %-9s %-22s %15s", "ACCOUNT", "TYPE", "HOLDER", "BALANCE") + RESET);
        for (Account a : service.listAccounts()) {
            System.out.println("  " + a);
        }

        heading("8. Exporting statements");
        StatementExporter exporter = new StatementExporter();
        Path txt = exporter.exportText(riya, statementsDir);
        exporter.exportCsv(riya, statementsDir);
        ok("Wrote " + txt + " (+ .csv)");
        System.out.println();
        for (String line : Files.readAllLines(txt)) {
            System.out.println("  " + line);
        }
        System.out.println();
        ok("Demo finished without a single crash. Data saved in demo-data/.");
    }

    @FunctionalInterface
    private interface BankAction<T> {
        T run() throws BankException;
    }

    private static <T> T step(BankAction<T> action) {
        try {
            return action.run();
        } catch (BankException e) {
            error("Unexpected: " + e.getMessage());
            return null;
        }
    }

    private static void expectError(String label, BankAction<?> action) {
        try {
            action.run();
            error(label + " -> was NOT rejected");
        } catch (BankException e) {
            System.out.println(YELLOW + "  [BLOCKED] " + RESET + label + DIM + "  =>  "
                    + e.getClass().getSimpleName() + ": " + e.getMessage() + RESET);
        }
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
