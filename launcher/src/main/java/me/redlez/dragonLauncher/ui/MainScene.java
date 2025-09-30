package me.redlez.dragonLauncher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import me.redlez.dragonLauncher.launcher.GameLauncher;
import me.redlez.dragonLauncher.utils.VersionHandler;

import java.util.Map;

import com.google.gson.JsonObject;

public class MainScene {

    private final GameLauncher launcher = new GameLauncher();
    private final VersionHandler vHandler = new VersionHandler();

    private ComboBox<String> versionBox = new ComboBox<>();
    private TextField playerName = new TextField();
    private CheckBox snapshotBox = new CheckBox("Snapshots version");

    private Button refreshButton = new Button("Refresh");
    private Button playButton = new Button("Play");

    private ProgressBar progressBar = new ProgressBar(0);
    private Label progressLabel = new Label("");

    private HBox topControls;
    private VBox rightContent;
    private BorderPane root;

    private VBox sidebar;
    private Label homeLabel, settingsLabel, aboutLabel;

    public Scene create(Stage stage) {
        root = new BorderPane();

        // --- Sidebar ---
        sidebar = new VBox(25);
        sidebar.setPadding(new Insets(25));
        sidebar.setStyle("-fx-background-color: #2b2b2b;");
        sidebar.setPrefWidth(180);

        homeLabel = createSidebarLabel("Home", true);
        settingsLabel = createSidebarLabel("Settings", false);
        aboutLabel = createSidebarLabel("About", false);

        sidebar.getChildren().addAll(homeLabel, settingsLabel, aboutLabel);
        root.setLeft(sidebar);

        // Sidebar click handlers
        homeLabel.setOnMouseClicked(e -> {
            selectSidebarLabel(homeLabel, settingsLabel, aboutLabel);
            root.setCenter(rightContent);
        });

        settingsLabel.setOnMouseClicked(e -> {
            selectSidebarLabel(settingsLabel, homeLabel, aboutLabel);
            openSettings();
        });

        aboutLabel.setOnMouseClicked(e -> {
            selectSidebarLabel(aboutLabel, homeLabel, settingsLabel);
            
        });

        // --- Right content (Main UI) ---
        playerName.setPromptText("Enter player name");
        playerName.setStyle("-fx-text-fill: white; -fx-prompt-text-fill: #aaaaaa;");

        versionBox.setPrefWidth(200);
        snapshotBox.setTextFill(javafx.scene.paint.Color.WHITE);

        refreshButton.setOnAction(e -> refresh());
        playButton.setOnAction(e -> startGame());

        topControls = new HBox(12, playerName, versionBox, refreshButton, playButton);
        topControls.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        rightContent = new VBox(20, topControls, snapshotBox, spacer, progressLabel, progressBar);
        rightContent.setAlignment(Pos.TOP_LEFT);
        rightContent.setPadding(new Insets(20));
        rightContent.setStyle("-fx-background-color: #3c3f41; -fx-text-fill: white;");

        progressBar.setVisible(false);
        progressBar.setPrefHeight(40);
        progressBar.setPrefWidth(500);
        progressLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");

        root.setCenter(rightContent);

        Scene scene = new Scene(root, 900, 500);
        scene.getStylesheets().add(getClass().getResource("/application.css").toExternalForm());

        refresh();
        return scene;
    }
    

    private void startGame() {
        toggleDownloadUi(true);
        new Thread(() -> {
            try {
                launcher.launchGame(playerName, versionBox, progressBar, progressLabel);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    
    public void toggleDownloadUi(boolean isit) {
        playerName.setVisible(!isit);
        versionBox.setVisible(!isit);
        refreshButton.setVisible(!isit);
        playButton.setVisible(!isit);
        snapshotBox.setVisible(!isit);

        progressBar.setVisible(isit);
        progressBar.setProgress(0);
    }
    
    private void refresh() {
        try {
            versionBox.getItems().clear();

            JsonObject json = vHandler.fetchVersions();
            boolean includeSnapshots = snapshotBox.isSelected();

            // Fetch forge versions once
            Map<String, String> forgeVersions = vHandler.getForgeVersions();

            for (var v : json.getAsJsonArray("versions")) {
                JsonObject obj = v.getAsJsonObject();
                String type = obj.get("type").getAsString();
                String mcVersion = obj.get("id").getAsString();

                if (includeSnapshots || "release".equals(type)) {
                	
                    versionBox.getItems().add(mcVersion);

                    for (var entry : forgeVersions.entrySet()) {
                        String fullVersion = entry.getKey();
                        if (fullVersion.startsWith(mcVersion + "-")) {
                            String forgeVer = fullVersion.substring(mcVersion.length() + 1);
                            versionBox.getItems().add(mcVersion + " (Forge " + forgeVer + ")");
                        }
                    }
                }
            }

            if (!versionBox.getItems().isEmpty()) {
                versionBox.getSelectionModel().selectFirst();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    private Label createSidebarLabel(String text, boolean selected) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + (selected ? "#e74c3c" : "#aaaaaa") + "; -fx-font-size: 16px;");
        return label;
    }

    private void selectSidebarLabel(Label clicked, Label... others) {
        clicked.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 16px;");
        for (Label other : others) {
            other.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 16px;");
        }
    }

    private void openSettings() {
        SettingsUI settingsUI = new SettingsUI(() -> root.setCenter(rightContent));
        VBox settingsContent = settingsUI.createContent();
        root.setCenter(settingsContent);
    }
    private void openAbout() {
    	AboutUI aboutUI = new AboutUI(() -> root.setCenter(rightContent));
    	VBox aboutContent = null;
        root.setCenter(aboutContent);
    }
}
