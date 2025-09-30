package me.redlez.dragonLauncher.utils;

public class LoggerUtil {

    private final String name;

    // ANSI color codes for console
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";

    public LoggerUtil(String name) {
        this.name = name;
    }

    public void info(String msg) {
        System.out.println(String.format("[%s] %s[info]%s   : %s", name, GREEN,RESET, msg));
    }

    public void warning(String msg) {
    	System.out.println(String.format("[%s] %s[warning]%s   : %s", name, YELLOW,RESET, msg));
    }

    public void error(String msg) {
    	System.out.println(String.format("[%s] %s[error]%s   : %s", name, RED,RESET, msg));
    }
}
