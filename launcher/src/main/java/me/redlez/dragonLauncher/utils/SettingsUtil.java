package me.redlez.dragonLauncher.utils;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class SettingsUtil {

    private static final Path CONFIG_PATH = Paths.get(System.getProperty("user.home"), ".dragonLauncher", "config.cfg");
    private static Properties config = new Properties();

    static {
        loadConfig();
    }

    private static void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                    config.load(in);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                config.store(out, "DragonLauncher Configuration");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // === Getters ===
    public static int getRam() {
        String value = config.getProperty("ram", "2");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    public static String getJavaPath() {
        return config.getProperty("javaPath", "");
    }

    public static int getWidth() {
        String value = config.getProperty("width", "800");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 800;
        }
    }

    public static int getHeight() {
        String value = config.getProperty("height", "600");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 600;
        }
    }

    public static String getGameArgs() {
        return config.getProperty("gameArgs", "");
    }

    // === Setters ===
    public static void setRam(int ram) {
        config.setProperty("ram", String.valueOf(ram));
        saveConfig();
    }

    public static void setJavaPath(String path) {
        config.setProperty("javaPath", path);
        saveConfig();
    }

    public static void setWidth(int width) {
        config.setProperty("width", String.valueOf(width));
        saveConfig();
    }

    public static void setHeight(int height) {
        config.setProperty("height", String.valueOf(height));
        saveConfig();
    }

    public static void setGameArgs(String args) {
        config.setProperty("gameArgs", args);
        saveConfig();
    }

    // Reload config from disk if it changes outside
    public static void reload() {
        loadConfig();
    }
}
