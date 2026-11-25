package com.codenova.bank.ui;

/** ANSI colour helpers for a readable terminal. Disabled automatically with NO_COLOR. */
final class ConsoleUi {

    private static final boolean COLOR = System.getenv("NO_COLOR") == null;

    static final String RESET = code("\u001B[0m");
    static final String BOLD = code("\u001B[1m");
    static final String GREEN = code("\u001B[32m");
    static final String RED = code("\u001B[31m");
    static final String YELLOW = code("\u001B[33m");
    static final String CYAN = code("\u001B[36m");
    static final String DIM = code("\u001B[2m");

    private ConsoleUi() {
    }

    private static String code(String c) {
        return COLOR ? c : "";
    }

    static void banner() {
        System.out.println(CYAN + BOLD);
        System.out.println("  +--------------------------------------------------------------+");
        System.out.println("  |          CODENOVA BANK  -  Transaction & Account Manager     |");
        System.out.println("  +--------------------------------------------------------------+" + RESET);
    }

    static void ok(String msg) {
        System.out.println(GREEN + "  [OK] " + msg + RESET);
    }

    static void error(String msg) {
        System.out.println(RED + "  [ERROR] " + msg + RESET);
    }

    static void info(String msg) {
        System.out.println(YELLOW + "  " + msg + RESET);
    }

    static void heading(String msg) {
        System.out.println();
        System.out.println(BOLD + CYAN + "  >> " + msg + RESET);
    }
}
