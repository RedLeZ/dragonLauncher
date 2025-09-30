package me.redlez.dragonLauncher.ui;

import java.lang.management.ManagementFactory;

import com.sun.management.OperatingSystemMXBean;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class AboutUI {
	
	private Runnable backCallback;
	
	
	public AboutUI(Runnable backCallback) {
        this.backCallback = backCallback;
    }
	
	public VBox createContent() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #3c3f41;");
        
        
        Label lver = new Label("Launcher Version : 0.0.1-SNAPSHOT");
        lver.setTextFill(Color.WHITE);
        Label wc = new Label("Created By RedLeZ");
        wc.setTextFill(Color.WHITE);
        Label hc = new Label("");
        hc.setTextFill(Color.WHITE);
        

        root.getChildren().addAll();
        return root;
    }

}
