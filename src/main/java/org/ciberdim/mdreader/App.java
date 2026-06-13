package org.ciberdim.mdreader;

import org.ciberdim.mdreader.ui.MainWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * Main JavaFX Application class. Sets up the window scene,
 * styles, stage bindings, and launches the primary stage view.
 */
public class App extends Application {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    @Override
    public void start(Stage stage) {
        MainWindow mainWindow = new MainWindow();

        Scene scene = new Scene(mainWindow, 1020, 700);

        // Load application styling
        URL cssResource = getClass().getResource("/css/styles.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        } else {
            logger.error("Could not load styles.css. Check resources folder configuration.");
        }

        // Load application window icon
        try {
            java.io.InputStream iconStream = getClass().getResourceAsStream("/images/mark_text.png");
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            } else {
                logger.error("Application icon resource not found: /images/mark_text.png");
            }
        } catch (Exception e) {
            logger.error("Could not load application icon", e);
        }

        // Bind window title to open document name dynamically
        stage.titleProperty().bind(mainWindow.windowTitleProperty());

        // Configure window stage
        stage.setScene(scene);
        stage.setMinWidth(700);
        stage.setMinHeight(500);
        stage.show();
    }

    /**
     * Entry point to launch the application.
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
