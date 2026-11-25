package com.codenova.bank.service;

import com.codenova.bank.model.Account;
import com.codenova.bank.model.Transaction;
import com.codenova.bank.repository.CsvUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Writes an account's full history, in exact chronological order, to text or CSV. */
public class StatementExporter {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");

    public Path exportText(Account account, Path dir) throws IOException {
        List<Transaction> txs = sorted(account);
        String line = "=".repeat(104);
        String thin = "-".repeat(104);
        List<String> out = new ArrayList<>();
        out.add(line);
        out.add(center("CODENOVA BANK  -  ACCOUNT STATEMENT", 104));
        out.add(line);
        out.add(String.format("Account No : %-30s Holder    : %s", account.getAccountId(), account.getHolderName()));
        out.add(String.format("Type       : %-30s Generated : %s", account.getType(), LocalDateTime.now().format(TS)));
        out.add("Rules      : " + account.describeRules());
        out.add(thin);
        out.add(String.format("%-5s %-21s %-16s %-28s %14s %15s", "#", "Date & Time", "Type", "Description",
                "Amount", "Balance"));
        out.add(thin);
        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal debits = BigDecimal.ZERO;
        int i = 1;
        for (Transaction t : txs) {
            BigDecimal signed = t.signedAmount();
            if (signed.signum() >= 0) credits = credits.add(signed); else debits = debits.add(signed.negate());
            out.add(String.format("%-5d %-21s %-16s %-28s %14s %15s", i++, t.timestamp().format(TS), t.type(),
                    truncate(t.description(), 28), (signed.signum() >= 0 ? "+" : "") + signed.toPlainString(),
                    t.balanceAfter().toPlainString()));
        }
        out.add(thin);
        out.add(String.format("Total credits : %15s", credits.toPlainString()));
        out.add(String.format("Total debits  : %15s", debits.toPlainString()));
        out.add(String.format("Closing bal.  : %15s", account.getBalance().toPlainString()));
        out.add(String.format("Entries       : %15d", txs.size()));
        out.add(line);
        Path file = dir.resolve("statement_" + account.getAccountId() + ".txt");
        Files.createDirectories(dir);
        Files.write(file, out, StandardCharsets.UTF_8);
        return file;
    }

    public Path exportCsv(Account account, Path dir) throws IOException {
        List<String> out = new ArrayList<>();
        out.add("sequence,timestamp,type,description,reference,amount,balance_after");
        for (Transaction t : sorted(account)) {
            out.add(CsvUtil.join(String.valueOf(t.sequence()), t.timestamp().toString(), t.type().name(),
                    t.description(), t.reference(), t.signedAmount().toPlainString(),
                    t.balanceAfter().toPlainString()));
        }
        Path file = dir.resolve("statement_" + account.getAccountId() + ".csv");
        Files.createDirectories(dir);
        Files.write(file, out, StandardCharsets.UTF_8);
        return file;
    }

    private static List<Transaction> sorted(Account account) {
        List<Transaction> txs = new ArrayList<>(account.getTransactions());
        txs.sort(Comparator.comparingLong(Transaction::sequence));
        return txs;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private static String center(String s, int width) {
        int pad = Math.max(0, (width - s.length()) / 2);
        return " ".repeat(pad) + s;
    }
}
