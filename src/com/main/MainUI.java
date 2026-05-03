package com.main;

import com.controller.AudioPlayerController;
import com.resource.FrontProfile;
import com.util.FontUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class MainUI extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        URL fxmlLocation = getClass().getResource(FrontProfile.MAIN_VIEW_PATH);
        if (fxmlLocation == null) {
            throw new IOException("Cannot find FXML file at " + FrontProfile.MAIN_VIEW_PATH);
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();

        AudioPlayerController controller = loader.getController();
        controller.setPrimaryStage(primaryStage);

        Scene scene = new Scene(root, FrontProfile.WINDOW_WIDTH, FrontProfile.WINDOW_HEIGHT);
        primaryStage.setTitle(FrontProfile.APP_NAME);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void init() throws Exception {
        super.init();
        try {
            FontUtil.loadChineseFont(FrontProfile.FONT_PATH);
        } catch (Exception e) {
            System.err.println("警告：中文字体加载失败，界面可能显示异常");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
