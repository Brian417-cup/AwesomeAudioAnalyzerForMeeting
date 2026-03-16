package com.main;

import com.controller.RecognitionBackendController;
import com.model.SpeechRecognitionUnit;
import com.gui.VoicePrintSidePanel;

import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.List;

public class MeetingSummaryApp extends Application {

    private RecognitionBackendController recognitionBackendController;
    private ObservableList<SpeechRecognitionUnit> recognitionList;

    private Button loadBtn;
    private Button startBtn;
    private Button exportBtn;
    private Label filePathLabel;
    private Label statusLabel;
    private ProgressBar progressBar;
    private TextField searchField;

    private CheckBox hotWordsCheckBox;
    private TextField hotWordsPathField;
    private Button browseHotWordsBtn;
    private CheckBox voicePrintCheckBox;
    private TextField speakerCountField;
    private Button voicePrintLibBtn;

    private File currentAudioFile;
    private File hotWordsFile;
    private boolean isRecognizing = false;

    private StackPane rootContainer;
    private BorderPane mainContent;
    private Pane slidePane;
    private boolean isSidePanelOpen = false;

    private ListView<SpeechRecognitionUnit> listView;

    @Override
    public void start(Stage primaryStage) {
        System.out.println("=== MeetingSummaryApp 启动 ===");

        recognitionBackendController = new RecognitionBackendController();
        recognitionList = FXCollections.observableArrayList();

        initUI(primaryStage);
    }


    private void initUI(Stage stage) {
        // 使用 BorderPane 作为根容器，更易于控制侧边栏
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f6fa;");

        mainContent = createMainContent();

        // 将主内容放在中心
        root.setCenter(mainContent);

        Scene scene = new Scene(root, 1100, 750);
        stage.setTitle("📋 会议总结助手 v1.0");
        stage.setScene(scene);
        stage.show();

        System.out.println("窗口已显示");
    }

    private BorderPane createMainContent() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #f5f6fa;");

        VBox topBox = createTopToolbar();
        root.setTop(topBox);

        CenterPanel centerPanel = new CenterPanel();
        root.setCenter(centerPanel);

        HBox bottomBox = createStatusBar();
        root.setBottom(bottomBox);

        return root;
    }

    private VBox createTopToolbar() {
        VBox toolbar = new VBox(12);
        toolbar.setPadding(new Insets(0, 0, 15, 0));
        toolbar.setStyle("-fx-background-color: transparent;");

        HBox fileBox = new HBox(10);
        fileBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        fileBox.setStyle("-fx-background-color: transparent;");

        loadBtn = new Button("📁 加载音频");
        styleButton(loadBtn, "#3498db");
        loadBtn.setOnMouseClicked(this::loadAudioFile);

        startBtn = new Button("▶ 开始识别");
        styleButton(startBtn, "#27ae60");
        startBtn.setDisable(true);
        startBtn.setOnMouseClicked(e -> startRecognition());

        exportBtn = new Button("💾 导出结果");
        styleButton(exportBtn, "#f39c12");
        exportBtn.setDisable(true);
        exportBtn.setOnMouseClicked(e -> exportResults());

        voicePrintLibBtn = new Button("🎭 声纹库");
        styleButton(voicePrintLibBtn, "#8e44ad");
        voicePrintLibBtn.setOnMouseClicked(e -> toggleSidePanel());

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        filePathLabel = new Label("未选择文件");
        filePathLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 13px;");
        filePathLabel.setPrefWidth(400);

        fileBox.getChildren().addAll(loadBtn, startBtn, exportBtn,
                voicePrintLibBtn,
                spacer,
                filePathLabel);

        HBox optionBox = new HBox(15);
        optionBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        optionBox.setPadding(new Insets(8, 0, 0, 0));
        optionBox.setStyle("-fx-background-color: transparent;");

        hotWordsCheckBox = new CheckBox("🔑 热词增强");
        hotWordsCheckBox.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        hotWordsCheckBox.setDisable(true);
        hotWordsCheckBox.setOnAction(e -> handleHotWordsToggle());

        hotWordsPathField = new TextField();
        hotWordsPathField.setPromptText("热词文件路径 (.txt)");
        hotWordsPathField.setEditable(false);
        hotWordsPathField.setPrefWidth(300);
        hotWordsPathField.setStyle(getInputStyle());
        hotWordsPathField.setDisable(true);

        browseHotWordsBtn = new Button("📂 选择热词");
        styleButton(browseHotWordsBtn, "#9b59b6");
        browseHotWordsBtn.setDisable(true);
        browseHotWordsBtn.setOnMouseClicked(e -> browseHotWordsFile());

        voicePrintCheckBox = new CheckBox("🎭 声纹匹配");
        voicePrintCheckBox.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        voicePrintCheckBox.setDisable(true);
        voicePrintCheckBox.setOnAction(e -> {
            if (voicePrintCheckBox.isSelected()) {
                openSidePanel();
            } else {
                closeSidePanel();
            }
        });

        Label speakerCountLabel = new Label("👥 人数:");
        speakerCountLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        speakerCountField = new TextField("1");
        speakerCountField.setPrefWidth(50);
        speakerCountField.setStyle(getInputStyle());
        speakerCountField.setDisable(true);

        optionBox.getChildren().addAll(
                hotWordsCheckBox,
                hotWordsPathField,
                browseHotWordsBtn,
                new Separator(),
                voicePrintCheckBox,
                speakerCountLabel,
                speakerCountField
        );

        HBox searchBox = new HBox(10);
        searchBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        searchBox.setStyle("-fx-background-color: transparent;");

        Label searchIcon = new Label("🔍");
        searchIcon.setStyle("-fx-font-size: 16px;");

        searchField = new TextField();
        searchField.setPromptText("搜索发言人或内容...");
        searchField.setPrefWidth(600);
        searchField.setStyle(getInputStyle());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                listView.setItems(recognitionList);
            } else {
                filterResults(newVal.trim().toLowerCase());
            }
        });

        Button clearBtn = new Button("清空");
        styleButton(clearBtn, "#95a5a6");
        clearBtn.setOnMouseClicked(e -> {
            searchField.clear();
            listView.setItems(recognitionList);
        });

        searchBox.getChildren().addAll(searchIcon, searchField, clearBtn);

        toolbar.getChildren().addAll(fileBox, optionBox, searchBox);

        return toolbar;
    }

    private void styleButton(Button button, String color) {
        button.setStyle(String.format(
                "-fx-background-color: %s; " +
                        "-fx-text-fill: white; " +
                        "-fx-padding: 10 20; " +
                        "-fx-font-size: 14px; " +
                        "-fx-border-radius: 4; " +
                        "-fx-cursor: hand;",
                color
        ));

        button.addEventFilter(MouseEvent.MOUSE_ENTERED, e ->
                button.setStyle(String.format(
                        "-fx-background-color: derive(%s, -10%%); " +
                                "-fx-text-fill: white; " +
                                "-fx-padding: 10 20; " +
                                "-fx-font-size: 14px; " +
                                "-fx-border-radius: 4; " +
                                "-fx-cursor: hand;",
                        color
                ))
        );

        button.addEventFilter(MouseEvent.MOUSE_EXITED, e ->
                button.setStyle(String.format(
                        "-fx-background-color: %s; " +
                                "-fx-text-fill: white; " +
                                "-fx-padding: 10 20; " +
                                "-fx-font-size: 14px; " +
                                "-fx-border-radius: 4; " +
                                "-fx-cursor: hand;",
                        color
                ))
        );
    }

    private CenterPanel createCenterPanel() {
        return new CenterPanel();
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(12, 0, 0, 0));
        statusBar.setStyle("-fx-border-color: #bdc3c7; -fx-border-width: 1 0 0 0; -fx-padding: 10 0 0 0; -fx-background-color: transparent;");

        statusLabel = new Label("就绪");
        statusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 13px;");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        progressBar.setVisible(false);

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label countLabel = new Label("共 0 条记录");
        countLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 12px;");

        recognitionList.addListener((javafx.collections.ListChangeListener.Change<? extends SpeechRecognitionUnit> c) -> {
            countLabel.setText("共 " + recognitionList.size() + " 条记录");
        });

        statusBar.getChildren().addAll(statusLabel, progressBar, spacer, countLabel);

        return statusBar;
    }

    private void loadAudioFile(MouseEvent event) {
        System.out.println("=========== 点击了加载音频按钮 ===========");
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("选择音频文件");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("音频文件", "*.wav", "*.mp3", "*.m4a", "*.aac")
            );

            File file = chooser.showOpenDialog(loadBtn.getScene().getWindow());
            if (file != null) {
                currentAudioFile = file;
                filePathLabel.setText(file.getAbsolutePath());
                filePathLabel.setTooltip(new Tooltip(file.getAbsolutePath()));
                startBtn.setDisable(false);
                hotWordsCheckBox.setDisable(false);
                voicePrintCheckBox.setDisable(false);
                speakerCountField.setDisable(false);
                recognitionList.clear();
                exportBtn.setDisable(true);
                statusLabel.setText("✓ 已加载：" + file.getName());
                System.out.println("文件加载成功：" + file.getAbsolutePath());
            } else {
                System.out.println("用户取消了文件选择");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("加载失败：" + e.getMessage());
            showError("加载失败：" + e.getMessage());
        }
    }

    private void handleHotWordsToggle() {
        boolean enabled = hotWordsCheckBox.isSelected();
        hotWordsPathField.setDisable(!enabled);
        browseHotWordsBtn.setDisable(!enabled);

        if (enabled && hotWordsFile == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("选择热词文件");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("文本文件", "*.txt")
            );

            File file = chooser.showOpenDialog(hotWordsCheckBox.getScene().getWindow());
            if (file != null) {
                hotWordsFile = file;
                hotWordsPathField.setText(file.getAbsolutePath());
                hotWordsPathField.setTooltip(new Tooltip(file.getAbsolutePath()));
            } else {
                hotWordsCheckBox.setSelected(false);
                hotWordsPathField.setDisable(true);
                browseHotWordsBtn.setDisable(true);
            }
        } else if (!enabled) {
            hotWordsFile = null;
            hotWordsPathField.clear();
        }
    }

    private void browseHotWordsFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择热词文件");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("文本文件", "*.txt")
        );

        File file = chooser.showOpenDialog(browseHotWordsBtn.getScene().getWindow());
        if (file != null) {
            hotWordsFile = file;
            hotWordsPathField.setText(file.getAbsolutePath());
            hotWordsPathField.setTooltip(new Tooltip(file.getAbsolutePath()));
        }
    }

    private void toggleSidePanel() {
        System.out.println("=========== 点击了声纹库按钮 ===========");
        if (isSidePanelOpen) {
            System.out.println("关闭侧边栏");
            closeSidePanel();
        } else {
            System.out.println("打开侧边栏");
            openSidePanel();
        }
    }

    private void openSidePanel() {
        System.out.println("开始打开侧边栏...");
        if (isSidePanelOpen) {
            System.out.println("侧边栏已经打开，直接返回");
            return;
        }

        try {
            // 创建侧边栏面板（白色背景）
            BorderPane root = (BorderPane) mainContent.getScene().getRoot();
            VoicePrintSidePanel sidePanel = new VoicePrintSidePanel(root, null);
            sidePanel.setPrefWidth(450);
            sidePanel.setMaxWidth(450);

            // 设置关闭回调
            sidePanel.setOnCloseCallback(() -> {
                isSidePanelOpen = false;
                voicePrintCheckBox.setSelected(false);
                System.out.println("侧边栏已关闭，状态已更新");
            });

            // 初始位置：隐藏在右侧外面
            sidePanel.setTranslateX(450);

            // 添加到 BorderPane 的右侧
            root.setRight(sidePanel);

            System.out.println("侧边栏已添加到右侧");

            // 平移动画：从右向左滑入
            TranslateTransition transition = new TranslateTransition(Duration.millis(300), sidePanel);
            transition.setFromX(450);  // 从右侧 450px 处开始
            transition.setToX(0);      // 移动到 0（贴紧右边缘）
            transition.setOnFinished(e -> {
                isSidePanelOpen = true;
                System.out.println("动画完成，侧边栏已打开");
            });

            System.out.println("开始播放动画...");
            transition.play();

            voicePrintCheckBox.setSelected(true);
            System.out.println("声纹匹配复选框已选中");
        } catch (Exception e) {
            System.err.println("打开侧边栏时发生异常：" + e.getMessage());
            e.printStackTrace();
            showError("打开声纹库失败：" + e.getMessage());
        }
    }

    private void closeSidePanel() {
        System.out.println("开始关闭侧边栏...");
        if (!isSidePanelOpen) {
            System.out.println("侧边栏未打开");
            return;
        }

        try {
            BorderPane root = (BorderPane) mainContent.getScene().getRoot();
            VoicePrintSidePanel sidePanel = (VoicePrintSidePanel) root.getRight();

            if (sidePanel == null) {
                System.out.println("侧边栏为空");
                return;
            }

            System.out.println("找到侧边面板，当前位置：" + sidePanel.getTranslateX());

            // 平移动画：从左向右滑出
            TranslateTransition transition = new TranslateTransition(Duration.millis(300), sidePanel);
            transition.setFromX(0);       // 从当前位置
            transition.setToX(450);       // 移动到右侧外面
            transition.setOnFinished(e -> {
                root.setRight(null);      // 移除侧边栏
                isSidePanelOpen = false;
                System.out.println("侧边栏已移除，状态重置");
            });

            transition.play();
            System.out.println("开始播放关闭动画");

            voicePrintCheckBox.setSelected(false);
        } catch (Exception e) {
            System.err.println("关闭侧边栏时发生异常：" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startRecognition() {
        if (currentAudioFile == null) {
            showError("请先加载音频文件");
            return;
        }

        isRecognizing = true;
        startBtn.setDisable(true);
        startBtn.setText("⏳ 识别中...");
        exportBtn.setDisable(true);
        progressBar.setVisible(true);
        recognitionList.clear();

        boolean useHotWords = hotWordsCheckBox.isSelected();
        boolean useVoicePrint = voicePrintCheckBox.isSelected();
        int speakerCount = 1;

        try {
            speakerCount = Integer.parseInt(speakerCountField.getText().trim());
            if (speakerCount < 1) {
                speakerCount = 1;
            }
        } catch (NumberFormatException e) {
            speakerCount = 1;
        }

        final int finalSpeakerCount = speakerCount;

        recognitionBackendController.recognizeWithSettings(
                currentAudioFile,
                useHotWords,
                hotWordsFile,
                useVoicePrint,
                finalSpeakerCount,
                new RecognitionBackendController.RecognitionCallback() {
                    @Override
                    public void onProgress(int progress) {
                        javafx.application.Platform.runLater(() -> {
                            progressBar.setProgress(progress / 100.0);
                            statusLabel.setText("🔄 识别进度：" + progress + "%");
                        });
                    }

                    @Override
                    public void onSuccess(List<SpeechRecognitionUnit> result) {
                        javafx.application.Platform.runLater(() -> {
                            recognitionList.addAll(result);
                            isRecognizing = false;
                            startBtn.setDisable(false);
                            startBtn.setText("▶ 重新识别");
                            exportBtn.setDisable(false);
                            statusLabel.setText("✅ 识别完成");
                            progressBar.setVisible(false);

                            String settings = "";
                            if (useHotWords) settings += " [热词：是]";
                            if (useVoicePrint) settings += " [声纹：是]";
                            settings += " [人数：" + finalSpeakerCount + "]";
                            statusLabel.setText("✅ 识别完成" + settings);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        javafx.application.Platform.runLater(() -> {
                            isRecognizing = false;
                            startBtn.setDisable(false);
                            startBtn.setText("▶ 开始识别");
                            progressBar.setVisible(false);
                            statusLabel.setText("❌ " + error);
                            showError(error);
                        });
                    }
                }
        );
    }

    private void filterResults(String keyword) {
        ObservableList<SpeechRecognitionUnit> filtered = FXCollections.observableArrayList();

        for (SpeechRecognitionUnit unit : recognitionList) {
            if (unit.getSpeaker().toLowerCase().contains(keyword) ||
                    unit.getContent().toLowerCase().contains(keyword)) {
                filtered.add(unit);
            }
        }

        listView.setItems(filtered);
    }

    private void exportResults() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出识别结果");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("文本文件", "*.txt")
        );
        chooser.setInitialFileName("会议识别结果.txt");

        File file = chooser.showSaveDialog(listView.getScene().getWindow());
        if (file != null) {
            try {
                java.io.PrintWriter writer = new java.io.PrintWriter(file, "UTF-8");

                for (SpeechRecognitionUnit unit : recognitionList) {
                    writer.println(String.format("[%s] %s: %s",
                            unit.getFormattedTime(), unit.getSpeaker(), unit.getContent()));
                }

                writer.close();
                showInfo("导出成功", "结果已保存到：" + file.getAbsolutePath());
            } catch (Exception e) {
                showError("导出失败：" + e.getMessage());
            }
        }
    }

    private String getButtonStyle(String color) {
        return String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-padding: 8 16; -fx-font-size: 14px; -fx-border-radius: 4;",
                color
        );
    }

    private String getInputStyle() {
        return "-fx-background-color: white; -fx-border-color: #dde1e7; -fx-border-radius: 4; -fx-padding: 6; -fx-font-size: 13px;";
    }

    private void showError(String message) {
        showAlert("错误", message, Alert.AlertType.ERROR);
    }

    private void showInfo(String title, String message) {
        showAlert(title, message, Alert.AlertType.INFORMATION);
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    class CenterPanel extends VBox {
        public CenterPanel() {
            super(10);
            setPadding(new Insets(10, 0, 0, 0));
            setStyle("-fx-background-color: transparent;");

            Label titleLabel = new Label("📝 识别结果列表");
            titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

            listView = new ListView<>();
            listView.setItems(recognitionList);
            listView.setCellFactory(param -> createRecognitionCell());

            ScrollPane scrollPane = new ScrollPane(listView);
            scrollPane.setFitToWidth(true);
            scrollPane.setStyle("-fx-background: white; -fx-border-color: #dde1e7; -fx-border-radius: 4;");
            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            getChildren().addAll(titleLabel, scrollPane);
        }

        private ListCell<SpeechRecognitionUnit> createRecognitionCell() {
            return new ListCell<SpeechRecognitionUnit>() {
                private VBox container;
                private HBox headerBox;
                private Label speakerLabel;
                private Label timeLabel;
                private Label contentLabel;

                {
                    container = new VBox(8);
                    container.setPadding(new Insets(12));
                    container.setStyle(
                            "-fx-background-color: white; " +
                                    "-fx-border-color: #e0e0e0; " +
                                    "-fx-border-radius: 6; " +
                                    "-fx-background-radius: 6; " +
                                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 8, 0, 0, 3);"
                    );

                    headerBox = new HBox(12);
                    headerBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                    speakerLabel = new Label();
                    speakerLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-font-size: 14px;");

                    timeLabel = new Label();
                    timeLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 12px;");

                    Pane spacer = new Pane();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    headerBox.getChildren().addAll(speakerLabel, timeLabel, spacer);

                    contentLabel = new Label();
                    contentLabel.setWrapText(true);
                    contentLabel.setStyle("-fx-text-fill: #34495e; -fx-font-size: 13px; -fx-line-spacing: 1.5;");

                    container.getChildren().addAll(headerBox, contentLabel);

                    container.setCursor(Cursor.HAND);
                }

                @Override
                protected void updateItem(SpeechRecognitionUnit unit, boolean empty) {
                    super.updateItem(unit, empty);

                    if (empty || unit == null) {
                        setGraphic(null);
                        setStyle("-fx-background-color: transparent;");
                    } else {
                        speakerLabel.setText("👤 " + unit.getSpeaker());
                        timeLabel.setText("⏰ " + unit.getFormattedTime());
                        contentLabel.setText(unit.getContent());

                        setGraphic(container);
                        setStyle("-fx-background-color: transparent; -fx-padding: 6;");

                        container.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                            System.out.println("点击了列表项：" + unit.getContent());
                        });
                    }
                }
            };
        }
    }

    public static void main(String[] args) {
        System.out.println("=== 启动 MeetingSummaryApp ===");
        launch(args);
    }
}
