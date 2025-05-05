package org.example.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.client.network.ServerConnection;
import org.example.client.ui.EditorUI;


import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // Create server connection
        ServerConnection connection = new ServerConnection();
        // Initialize the EditorUI with the primary stage and connection
        new EditorUI(stage, connection);
    }

    public static void main(String[] args) {
        launch();
    }
}