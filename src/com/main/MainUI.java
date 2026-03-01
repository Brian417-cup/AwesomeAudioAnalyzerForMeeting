package com.main;

import com.resource.Profile;
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
        // 获取FXML资源URL
        URL fxmlLocation = getClass().getResource(Profile.MAIN_VIEW_PATH);
        if (fxmlLocation == null) {
            throw new IOException("Cannot find FXML file at " + Profile.MAIN_VIEW_PATH);
        }

        // 使用 FXMLLoader 实例以便获取控制器
        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();

        // 获取控制器并设置主舞台
        com.controller.AudioPlayerController controller = loader.getController();
        controller.setPrimaryStage(primaryStage);

        Scene scene = new Scene(root, Profile.WINDOW_WIDTH, Profile.WINDOW_HEIGHT);
        primaryStage.setTitle(Profile.APP_NAME);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void init() throws Exception {
        super.init();
        // 加载中文字体支持
        try {
            FontUtil.loadChineseFont(Profile.FONT_PATH);
        } catch (Exception e) {
            System.err.println("警告：中文字体加载失败，界面可能显示异常");
            e.printStackTrace();
            // 继续启动（但中文可能显示为方框）
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
