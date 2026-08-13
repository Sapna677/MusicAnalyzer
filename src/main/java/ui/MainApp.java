package ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main JavaFX Application class.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainUI.fxml"));
        Parent root = loader.load();
        
        MainController controller = loader.getController();

        Scene scene = new Scene(root);
        // Load our custom aesthetic stylesheet
        scene.getStylesheets().add(getClass().getResource("/css/cute-theme.css").toExternalForm());

        primaryStage.setTitle("🎀 Music Analyzer & Player 🌸");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(640);
        
        // Handle clean shutdown of thread components on exit
        primaryStage.setOnCloseRequest(event -> {
            if (controller != null) {
                controller.shutdown();
            }
            Platform.exit();
            System.exit(0);
        });

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
