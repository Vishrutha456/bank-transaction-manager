package com.codenova.bank.ui;

import com.codenova.bank.exception.BankException;
import com.codenova.bank.model.Account;
import com.codenova.bank.model.AccountType;
import com.codenova.bank.model.Transaction;
import com.codenova.bank.service.AmountParser;
import com.codenova.bank.service.BankService;
import com.codenova.bank.service.StatementExporter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

import static com.codenova.bank.ui.ConsoleUi.*;

/**
 * Interactive menu. Every action is wrapped so that a {@link BankException}
 * (or bad input) prints a friendly message and returns to the menu instead of
 * crashing the program.
 */
public class BankConsoleApp {

    private final BankService service;
    private final StatementExporter exporter = new StatementExporter();
    private final Path statementsDir;
    private final Scanner in;

    public BankConsoleApp(BankService service, Path statementsDir, Scanner in) {
        this.service = service;
        this.statementsDir = statementsDir;
        this.in = in;
    }

    public void run() {
        banner();
        while (true) {
            System.out.println();
            System.out.println(BOLD + "  MAIN MENU" + RESET);
            System.out.println("  1. Open new account        5. Transfer funds");
            System.out.println("  2. List all accounts       6. View transaction history");
            System.out.println("  3. Deposit                 7. Export statement (TXT + CSV)");
            System.out.println("  4. Withdraw                8. Run month-end (interest & fees)");
            System.out.println("  0. Exit");
            String choice = prompt("Choose an option");
            if (choice == null || choice.equals("0")) {
                info("Goodbye! All data has been saved.");
                return;
            }
            try {
                switch (choice) {
                    case "1" -> openAccount();
                    case "2" -> listAccounts();
                    case "3" -> deposit();
                    case "4" -> withdraw();
                    case "5" -> transfer();
                    case "6" -> history();
                    case "7" -> export();
                    case "8" -> monthEnd();
                    default -> error("Unknown option '" + choice + "'. Please enter 0-8.");
                }
            } catch (BankException e) {
                error(e.getClass().getSimpleName() + ": " + e.getMessage());
            } catch (IOException | UncheckedIOException e) {
                error("File error: " + e.getMessage());
            }
        }
    }

    private void openAccount() throws BankException {
        String t = prompt("Account type (1 = Savings, 2 = Checking)");
        AccountType type = switch (t == null ? "" : t) {
            case "1" -> AccountType.SAVINGS;
            case "2" -> AccountType.CHECKING;
            default -> null;
        };
        if (type == null) {
            error("Please enter 1 or 2.");
            return;
        }
        String name = prompt("Account holder name");
        String amount = prompt("Opening deposit");
        Account a = service.openAccount(type, name, AmountParser.parse(amount));
        ok("Opened " + a.getType() + " account " + BOLD + a.getAccountId() + RESET + GREEN
                + " for " + a.getHolderName() + " with balance " + a.getBalance().toPlainString());
        info(a.describeRules());
    }

    private void listAccounts() {
        heading("All accounts");
        System.out.println(DIM + String.format("  %-10s %-9s %-22s %15s", "ACCOUNT", "TYPE", "HOLDER", "BALANCE") + RESET);
        for (Account a : service.listAccounts()) {
            System.out.println("  " + a);
        }
        if (service.listAccounts().isEmpty()) {
            info("No accounts yet. Choose option 1 to open one.");
        }
    }

    private void deposit() throws BankException {
        String id = prompt("Account number");
        Transaction t = service.deposit(id, AmountParser.parse(prompt("Amount to deposit")));
        ok("Deposited " + t.amount().toPlainString() + ". New balance: " + t.balanceAfter().toPlainString());
    }

    private void withdraw() throws BankException {
        String id = prompt("Account number");
        Transaction t = service.withdraw(id, AmountParser.parse(prompt("Amount to withdraw")));
        ok("Withdrew " + t.amount().toPlainString() + ". New balance: " + t.balanceAfter().toPlainString());
    }

    private void transfer() throws BankException {
        String from = prompt("From account");
        String to = prompt("To account");
        String amount = prompt("Amount");
        service.transfer(from, to, AmountParser.parse(amount));
        ok("Transferred " + amount + " from " + from.toUpperCase() + " to " + to.toUpperCase());
        System.out.println("  " + service.getAccount(from));
        System.out.println("  " + service.getAccount(to));
    }

    private void history() throws BankException {
        Account a = service.getAccount(prompt("Account number"));
        heading("History for " + a.getAccountId() + " (" + a.getHolderName() + ")");
        for (Transaction t : a.getTransactions()) {
            String amt = t.signedAmount().signum() >= 0
                    ? GREEN + "+" + t.signedAmount().toPlainString() + RESET
                    : RED + t.signedAmount().toPlainString() + RESET;
            System.out.printf("  #%-4d %s  %-15s %-28s %s  -> %s%n", t.sequence(), t.timestamp(), t.type(),
                    t.description(), amt, t.balanceAfter().toPlainString());
        }
    }

    private void export() throws BankException, IOException {
        Account a = service.getAccount(prompt("Account number"));
        Path txt = exporter.exportText(a, statementsDir);
        Path csv = exporter.exportCsv(a, statementsDir);
        ok("Statement exported:");
        info(txt.toAbsolutePath().toString());
        info(csv.toAbsolutePath().toString());
    }

    private void monthEnd() {
        List<Transaction> created = service.runMonthEnd();
        ok("Month-end complete: " + created.size() + " entries posted.");
        for (Transaction t : created) {
            info(t.accountId() + "  " + t.type() + "  " + t.signedAmount().toPlainString());
        }
    }

    private String prompt(String label) {
        System.out.print(CYAN + "  " + label + ": " + RESET);
        if (!in.hasNextLine()) {
            return null;
        }
        return in.nextLine().trim();
    }
}
