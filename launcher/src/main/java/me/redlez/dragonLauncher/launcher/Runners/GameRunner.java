package me.redlez.dragonLauncher.launcher.Runners;

import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

public interface GameRunner {
    void launch(String playerName, ProgressBar progressBar, Label progressLabel) throws Exception;
    
    public static String humanReadableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String unit = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), unit);
    }
}
