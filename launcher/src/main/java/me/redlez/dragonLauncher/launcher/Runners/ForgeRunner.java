package me.redlez.dragonLauncher.launcher.Runners;

import com.google.gson.*;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import me.redlez.dragonLauncher.utils.*;

import java.io.File;
import java.nio.file.*;

public class ForgeRunner implements GameRunner {

    private final String version;
    private Label progressLabel;
    private final Path baseDir = Paths.get(System.getProperty("user.home"), ".minecraft");
    private static final LoggerUtil LOGGER = new LoggerUtil("ForgeRunner");

    public ForgeRunner(String version, Label prglb) {
        this.version = version;
        this.progressLabel = prglb;
    }
    private void updateStatus(String message) {
        Platform.runLater(() -> progressLabel.setText(message));
    }

    
    private void ensureForgeLibraries(String forgeVersion, Path baseDir) throws Exception {
    	LOGGER.info("checking forge libs");
        Path jsonFile = Paths.get(System.getProperty("user.home"), ".minecraft", "versions",forgeVersion, forgeVersion + ".json");
        if (!Files.exists(jsonFile)) {
            throw new IllegalStateException("Forge JSON for " + forgeVersion + " not found!");
        }

        JsonObject forgeJson = JsonParser.parseReader(Files.newBufferedReader(jsonFile)).getAsJsonObject();
        JsonArray libraries = forgeJson.getAsJsonArray("libraries");

        for (JsonElement libElem : libraries) {
            JsonObject lib = libElem.getAsJsonObject();
            JsonObject downloads = lib.getAsJsonObject("downloads");
            if (!downloads.has("artifact")) continue;

            JsonObject artifact = downloads.getAsJsonObject("artifact");
            String path = artifact.get("path").getAsString();
            String url = artifact.get("url").getAsString();

            Path localPath = baseDir.resolve(path);
            if (!Files.exists(localPath)) {
                LOGGER.info("Downloading missing library: " + path);
                Files.createDirectories(localPath.getParent());
                DownloadManager.downloadFile(url, localPath);
            }
        }
    }


    @Override
    public void launch(String playerName, ProgressBar progressBar, Label progressLabel) throws Exception {
        // Parse Forge version
        String[] parts = version.split(" ");
        String vanillaVersion = parts[0];
        String forgeBuild = parts[2].replace(")", "");
        String forgeVersion = vanillaVersion + "-forge-" + forgeBuild;

        // Ensure Forge installer exists
        Path installerDir = Paths.get(System.getProperty("user.home"), ".dragonLauncher", "forge", "installer");
        Files.createDirectories(installerDir);
        Path installerJar = installerDir.resolve(vanillaVersion + "-" + forgeBuild + "-installer.jar");

        if (!Files.exists(installerJar)) {
            String url = ForgeFetcher.getForgeInstallerUrl(vanillaVersion + "-" + forgeBuild);
            DownloadManager.downloadFile(url, installerJar);
        }

        // Run Forge installer
        String javaExec = SettingsUtil.getJavaPath();
        if (javaExec.isEmpty() || !new File(javaExec).exists()) {
            javaExec = JavaManager.JavaPath(vanillaVersion).toString();
        }
        
        updateStatus("Checking forge Libs... support Forge through https://www.patreon.com/LexManos/");
        ProcessBuilder pb = new ProcessBuilder(
                javaExec, "-jar", installerJar.toString(),
                "--installClient", baseDir.toString()
        );
        pb.inheritIO();
        Process p = pb.start();
        p.waitFor();

        ensureForgeLibraries(forgeVersion, baseDir.resolve("libraries"));
        LOGGER.info("Done Checking libs");
        VanillaRunner vanillaRunner = new VanillaRunner(vanillaVersion);
        vanillaRunner.setForgeModLibraries(forgeVersion); // inject Forge libraries and jar
        vanillaRunner.launch(playerName, progressBar, progressLabel);
    }
}
