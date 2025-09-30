package me.redlez.dragonLauncher;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import me.redlez.dragonLauncher.ui.MainScene;

public class Main extends Application {
	
	public static MainScene mainScene = new MainScene();

    @Override
    public void start(Stage stage) {
        stage.setTitle("DragonLauncher");
        stage.getIcons().add(
        	    new Image(getClass().getResourceAsStream("/icons/launcher.png"))
        	);
        Scene scene = mainScene.create(stage);

        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
    
    public static MainScene getmc() {
    	return mainScene;
    }
    	
		
		
}
