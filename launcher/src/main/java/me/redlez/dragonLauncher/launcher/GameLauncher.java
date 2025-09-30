package me.redlez.dragonLauncher.launcher;

import javafx.scene.control.*;
import me.redlez.dragonLauncher.launcher.Runners.ForgeRunner;
import me.redlez.dragonLauncher.launcher.Runners.GameRunner;
import me.redlez.dragonLauncher.launcher.Runners.VanillaRunner;
import me.redlez.dragonLauncher.utils.LoggerUtil;

public class GameLauncher {

    private static final LoggerUtil LOGGER = new LoggerUtil("GameLauncher");
    private boolean isLaunching = false;

    public void launchGame(TextField playerNameField, ComboBox<String> versionBox,
                           ProgressBar progressBar, Label progressLabel) throws Exception {
        String version = versionBox.getValue();
        String playerName = playerNameField.getText().trim();
        if (playerName.isEmpty()) playerName = "Player";
        isLaunching = false;

        if (isLaunching) {
            LOGGER.warning("Minecraft is already launching!");
            return;
        }
        isLaunching = true;

        GameRunner runner;
        if (version.contains("(Forge")) {
            runner = new ForgeRunner(version, progressLabel);
        } else {
            runner = new VanillaRunner(version);
        }

        LOGGER.info("Launching Minecraft " + version + " as " + playerName);

        runner.launch(playerName, progressBar, progressLabel);
        }
}
