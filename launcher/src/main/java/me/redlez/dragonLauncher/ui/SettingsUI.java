package me.redlez.dragonLauncher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.Properties;

import com.sun.management.OperatingSystemMXBean;

public class SettingsUI {

    private Properties config = new Properties();
    private Path configPath = Paths.get(System.getProperty("user.home"), ".dragonLauncher", "config.cfg");
    private Runnable backCallback;

    public SettingsUI(Runnable backCallback) {
        this.backCallback = backCallback;
        loadConfig();
    }

    // Return a Node instead of Scene
	public VBox createContent() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #3c3f41;");

     // --- RAM Slider ---
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        long totalPhysicalMemory = osBean.getTotalPhysicalMemorySize();
        long totalGB = totalPhysicalMemory / (1024 * 1024 * 1024);

        int defaultRam = Integer.parseInt(config.getProperty("ram", "2"));

        Slider ramSlider = new Slider(1, totalGB, defaultRam);
        ramSlider.setMajorTickUnit(1);
        ramSlider.setMinorTickCount(0);
        ramSlider.setSnapToTicks(true);
        ramSlider.setShowTickLabels(true);
        ramSlider.setShowTickMarks(true);
        ramSlider.setStyle(
            "-fx-control-inner-background: #2b2b2b; " +
            "-fx-padding: 10px; " +
            "-fx-font-size: 24px;"
        );

        Label ramLabel = new Label("RAM: " + (int) ramSlider.getValue() + "G");
        ramLabel.setTextFill(Color.WHITE);
        ramSlider.valueProperty().addListener((obs, oldV, newV) -> ramLabel.setText("RAM: " + newV.intValue() + "G"));

        VBox ramBox = new VBox(5, ramLabel, ramSlider);
        ramBox.setPadding(new Insets(10, 0, 10, 0));

        // --- Java Path ---
        TextField javaPathField = new TextField(config.getProperty("javaPath", ""));
        javaPathField.setPromptText("Java Executable Path");
        javaPathField.setPrefSize(500,25);
        javaPathField.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: white; -fx-font-size: 12px;");
        Label javaLabel = new Label("Java Path:");
        javaLabel.setTextFill(Color.WHITE);
        HBox javaBox = new HBox(10, javaLabel, javaPathField);
        javaBox.setAlignment(Pos.CENTER_LEFT);

        // --- Window Width/Height ---
        TextField widthField = new TextField(config.getProperty("width", "800"));
        TextField heightField = new TextField(config.getProperty("height", "600"));
        widthField.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: white;");
        heightField.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: white;");
        Label widthLabel = new Label("Width:");
        widthLabel.setTextFill(Color.WHITE);
        Label heightLabel = new Label("Height:");
        heightLabel.setTextFill(Color.WHITE);
        HBox sizeBox = new HBox(10, widthLabel, widthField, heightLabel, heightField);
        sizeBox.setAlignment(Pos.CENTER_LEFT);

        // --- Advanced Toggle (Game Args) ---
        TextArea gameArgsArea = new TextArea(config.getProperty("gameArgs", ""));
        gameArgsArea.setPromptText("Game Arguments (advanced)");
        gameArgsArea.setStyle("-fx-control-inner-background: #2b2b2b; -fx-text-fill: white;");
        gameArgsArea.setVisible(false);

        Button advancedBtn = new Button("Advanced");
        advancedBtn.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: white;");
        advancedBtn.setOnAction(e -> gameArgsArea.setVisible(!gameArgsArea.isVisible()));

        // --- Apply & Back Buttons ---
        Button applyBtn = new Button("Apply");
        applyBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        applyBtn.setOnAction(e -> saveConfig(ramSlider, javaPathField, widthField, heightField, gameArgsArea));
        HBox buttons = new HBox(10, applyBtn);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(ramBox, javaBox, sizeBox, advancedBtn, gameArgsArea, buttons);
        return root;
    }

    private void loadConfig() {
        try {
            if (Files.exists(configPath)) {
                try (InputStream in = Files.newInputStream(configPath)) {
                    config.load(in);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveConfig(Slider ramSlider, TextField javaPath, TextField width, TextField height, TextArea gameArgs) {
        try {
            config.setProperty("ram", String.valueOf((int) ramSlider.getValue()));
            config.setProperty("javaPath", javaPath.getText().trim());
            config.setProperty("width", width.getText().trim());
            config.setProperty("height", height.getText().trim());
            config.setProperty("gameArgs", gameArgs.getText().trim());

            Files.createDirectories(configPath.getParent());
            try (OutputStream out = Files.newOutputStream(configPath)) {
                config.store(out, "DragonLauncher Configuration");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
