package com.codenova.bank;

import com.codenova.bank.repository.CsvAccountRepository;
import com.codenova.bank.service.BankService;
import com.codenova.bank.ui.BankConsoleApp;
import com.codenova.bank.ui.DemoRunner;

import java.nio.file.Path;
import java.util.Scanner;

/**
 * Entry point.
 * <pre>
 *   java -cp out com.codenova.bank.Main            # interactive menu (data in ./data)
 *   java -cp out com.codenova.bank.Main --demo     # scripted walkthrough (data in ./demo-data)
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        boolean demo = args.length > 0 && args[0].equals("--demo");
        Path dataDir = Path.of(demo ? "demo-data" : "data");
        BankService service = new BankService(new CsvAccountRepository(dataDir));
        if (demo) {
            new DemoRunner(service, dataDir.resolve("statements")).run();
        } else {
            new BankConsoleApp(service, dataDir.resolve("statements"), new Scanner(System.in)).run();
        }
    }
}
