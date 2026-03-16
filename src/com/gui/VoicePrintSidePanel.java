package com.gui;

import com.model.VoicePrint;
import com.controller.VoicePrintLibraryController;
import javafx.animation.TranslateTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.util.List;

public class VoicePrintSidePanel extends VBox {

    private VoicePrintLibraryController voicePrintLibraryController;
    private ListView<VoicePrint> voicePrintListView;
    private TextField userNameField;
    private TextField voiceNameField;
    private Label filePathLabel;
    private Button registerBtn;
    private Button deleteBtn;
    private Button clearAllBtn;

    private File currentAudioFile;
    private BorderPane parentContainer;
    private StackPane contentContainer;
    private Pane slidePane;
    private Runnable onCloseCallback;

    public VoicePrintSidePanel(BorderPane container, Pane slidePane) {
        this.parentContainer = container;
        this.slidePane = slidePane;
        this.voicePrintLibraryController = VoicePrintLibraryController.getInstance();

        initializeComponents();
    }

    public void setOnCloseCallback(Runnable callback) {
        this.onCloseCallback = callback;
    }

    private void initializeComponents() {
        setPadding(new Insets(15));
        setSpacing(15);
        setStyle("-fx-background-color: white;");
        setPrefWidth(450);

        HBox titleBox = createTitleBar();
        VBox formBox = createRegistrationForm();
        VBox listBox = createVoicePrintList();

        Separator separator = new Separator();

        getChildren().addAll(titleBox, formBox, separator, listBox);
    }

    private HBox createTitleBar() {
        HBox titleBox = new HBox(10);
        titleBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label titleLabel = new Label("🎭 声纹库管理");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 16px; -fx-padding: 5 10; -fx-border-radius: 4; -fx-cursor: hand;");
        closeBtn.setOnMouseClicked(e -> closeWithAnimation());

        titleBox.getChildren().addAll(titleLabel, spacer, closeBtn);
        return titleBox;
    }


    private VBox createRegistrationForm() {
        VBox formBox = new VBox(12);
        formBox.setPadding(new Insets(10));
        formBox.setStyle("-fx-background-color: #f8f9fa; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label formTitle = new Label("📝 注册新声纹");
        formTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #495057;");

        HBox userBox = new HBox(10);
        userBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label userLabel = new Label("👤 用户名:");
        userLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        userNameField = new TextField();
        userNameField.setPromptText("请输入用户名称");
        userNameField.setPrefWidth(250);
        userNameField.setStyle(getInputStyle());
        userNameField.textProperty().addListener((obs, oldVal, newVal) -> checkCanRegister());
        userBox.getChildren().addAll(userLabel, userNameField);

        HBox voiceBox = new HBox(10);
        voiceBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label voiceLabel = new Label("🎙 声纹名:");
        voiceLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        voiceNameField = new TextField();
        voiceNameField.setPromptText("例如：正式发言、电话录音等");
        voiceNameField.setPrefWidth(250);
        voiceNameField.setStyle(getInputStyle());
        voiceNameField.textProperty().addListener((obs, oldVal, newVal) -> checkCanRegister());
        voiceBox.getChildren().addAll(voiceLabel, voiceNameField);

        VBox fileBox = new VBox(8);
        Label fileLabel = new Label("🎵 音频样本:");
        fileLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        HBox filePathBox = new HBox(10);
        filePathBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        filePathLabel = new Label("未选择文件");
        filePathLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");
        filePathLabel.setPrefWidth(200);

        Button browseBtn = new Button("📂 选择音频");
        browseBtn.setStyle(getButtonStyle("#9b59b6"));
        browseBtn.setOnMouseClicked(e -> selectAudioFile());

        filePathBox.getChildren().addAll(filePathLabel, browseBtn);
        fileBox.getChildren().addAll(fileLabel, filePathBox);

        registerBtn = new Button("✅ 注册声纹");
        registerBtn.setStyle(getButtonStyle("#27ae60"));
        registerBtn.setPrefWidth(Double.MAX_VALUE);
        registerBtn.setDisable(true);
        registerBtn.setOnMouseClicked(e -> registerVoicePrint());

        formBox.getChildren().addAll(formTitle, userBox, voiceBox, fileBox, registerBtn);
        return formBox;
    }

    private VBox createVoicePrintList() {
        VBox listBox = new VBox(10);
        VBox.setVgrow(listBox, Priority.ALWAYS);

        HBox listHeader = new HBox(10);
        listHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label listTitle = new Label("📋 已注册声纹");
        listTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #495057;");

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 添加分组开关
        CheckBox groupCheckBox = new CheckBox("按用户分组");
        groupCheckBox.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-cursor: hand;");
        groupCheckBox.setSelected(true);
        groupCheckBox.setOnAction(e -> refreshVoicePrintList());

        clearAllBtn = new Button("🗑 清空全部");
        clearAllBtn.setStyle(getButtonStyle("#e74c3c"));
        clearAllBtn.setDisable(true);
        clearAllBtn.setOnMouseClicked(e -> clearAllVoicePrints());

        listHeader.getChildren().addAll(listTitle, groupCheckBox, spacer, clearAllBtn);

        voicePrintListView = new ListView<>();
        voicePrintListView.setItems(voicePrintLibraryController.getVoicePrintList());

        // ✅ 这里仍然需要设置单元格工厂
        voicePrintListView.setCellFactory(param -> createVoicePrintCell());

        // 设置选择模式为单选
        voicePrintListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        // 自定义选中样式
        voicePrintListView.setStyle(
                "-fx-background: #f8f9fa; " +
                        "-fx-border-color: #dee2e6; " +
                        "-fx-selection-bar: #3498db; " +
                        "-fx-selection-bar-non-focused: #bdc3c7;"
        );

        VBox.setVgrow(voicePrintListView, Priority.ALWAYS);

        deleteBtn = new Button("❌ 删除选中");
        deleteBtn.setStyle(getButtonStyle("#e74c3c"));
        deleteBtn.setDisable(true);
        deleteBtn.setOnMouseClicked(e -> deleteSelectedVoicePrint());

        listBox.getChildren().addAll(listHeader, voicePrintListView, deleteBtn);

        voicePrintListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            deleteBtn.setDisable(newVal == null);
        });

        // 初始加载时刷新列表（排序）
        refreshVoicePrintList();

        return listBox;
    }

    private void refreshVoicePrintList() {
        ObservableList<VoicePrint> allPrints = voicePrintLibraryController.getVoicePrintList();

        // 创建一个新的 ArrayList 避免直接修改原始列表
        List<VoicePrint> sortedList = new java.util.ArrayList<>(allPrints);

        // 简单排序：按用户名分组显示
        sortedList.sort((vp1, vp2) -> {
            int userCompare = vp1.getUserName().compareTo(vp2.getUserName());
            if (userCompare != 0) {
                return userCompare;
            }
            return vp1.getVoiceName().compareTo(vp2.getVoiceName());
        });

        // 使用新的排序后的列表设置 ListView
        voicePrintListView.setItems(FXCollections.observableArrayList(sortedList));
    }

    /**
     * ✅ 这个方法仍然需要保留
     * 用于创建 ListView 中每个单元格的可视化组件
     */
    private ListCell<VoicePrint> createVoicePrintCell() {
        return new ListCell<VoicePrint>() {
            private BorderPane wrapperContainer;
            private VBox container;
            private Label userNameLabel;
            private Label voiceNameLabel;
            private Label timeLabel;

            {
                container = new VBox(6);
                container.setPadding(new Insets(10));
                container.setStyle(
                        "-fx-background-color: white; " +
                                "-fx-border-color: #e0e0e0; " +
                                "-fx-border-radius: 6; " +
                                "-fx-background-radius: 6;"
                );

                userNameLabel = new Label();
                userNameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-font-size: 14px;");

                voiceNameLabel = new Label();
                voiceNameLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");

                timeLabel = new Label();
                timeLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 11px;");

                container.getChildren().addAll(userNameLabel, voiceNameLabel, timeLabel);

                // 使用 BorderPane 作为外层容器，便于添加边框效果
                wrapperContainer = new BorderPane(container);
                wrapperContainer.setStyle("");
            }

            @Override
            protected void updateItem(VoicePrint vp, boolean empty) {
                super.updateItem(vp, empty);

                if (empty || vp == null) {
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    userNameLabel.setText("👤 " + vp.getUserName());
                    voiceNameLabel.setText("🎙 " + vp.getVoiceName());

                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
                    timeLabel.setText("⏰ " + sdf.format(new java.util.Date(vp.getCreateTime())));

                    setGraphic(wrapperContainer);
                    setStyle("-fx-background-color: transparent; -fx-padding: 4;");

                    // 根据是否选中设置不同的样式
                    if (isSelected()) {
                        wrapperContainer.setStyle(
                                "-fx-border-color: #3498db; " +
                                        "-fx-border-width: 2; " +
                                        "-fx-border-radius: 6; " +
                                        "-fx-background-radius: 6; " +
                                        "-fx-background-color: derive(#3498db, 90%);"
                        );
                        container.setStyle(
                                "-fx-background-color: derive(#3498db, 95%); " +
                                        "-fx-border-color: #3498db; " +
                                        "-fx-border-radius: 6; " +
                                        "-fx-background-radius: 6;"
                        );
                    } else {
                        wrapperContainer.setStyle("");
                        container.setStyle(
                                "-fx-background-color: white; " +
                                        "-fx-border-color: #e0e0e0; " +
                                        "-fx-border-radius: 6; " +
                                        "-fx-background-radius: 6;"
                        );
                    }
                }
            }
        };
    }


    private void selectAudioFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择声纹注册音频");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("音频文件", "*.wav", "*.mp3", "*.m4a")
        );

        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            currentAudioFile = file;
            filePathLabel.setText(file.getName());
            filePathLabel.setTooltip(new Tooltip(file.getAbsolutePath()));
            checkCanRegister();
        }
    }

    private void checkCanRegister() {
        boolean canRegister = !userNameField.getText().trim().isEmpty() &&
                !voiceNameField.getText().trim().isEmpty() &&
                currentAudioFile != null;
        registerBtn.setDisable(!canRegister);
    }

    private void registerVoicePrint() {
        if (currentAudioFile == null) {
            showError("请选择音频文件");
            return;
        }

        String userName = userNameField.getText().trim();
        String voiceName = voiceNameField.getText().trim();

        if (userName.isEmpty() || voiceName.isEmpty()) {
            showError("请填写用户名和声纹名称");
            return;
        }

        voicePrintLibraryController.addVoicePrint(userName, voiceName, currentAudioFile);

        userNameField.clear();
        voiceNameField.clear();
        filePathLabel.setText("未选择文件");
        currentAudioFile = null;
        registerBtn.setDisable(true);

        showInfo("注册成功", "声纹已成功添加到库中");
    }

    private void deleteSelectedVoicePrint() {
        VoicePrint selected = voicePrintListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("确认删除");
            alert.setHeaderText(null);
            alert.setContentText("确定要删除 \"" + selected.getUserName() + " - " + selected.getVoiceName() + "\" 吗？");

            if (alert.showAndWait().get() == ButtonType.OK) {
                voicePrintLibraryController.removeVoicePrint(selected.getId());
            }
        }
    }

    private void clearAllVoicePrints() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认清空");
        alert.setHeaderText(null);
        alert.setContentText("确定要清空所有声纹吗？此操作不可恢复！");

        if (alert.showAndWait().get() == ButtonType.OK) {
            voicePrintLibraryController.clearLibrary();
        }
    }

    private void closePanel() {
        System.out.println("点击了关闭按钮，开始关闭侧边栏...");

        if (parentContainer != null) {
            parentContainer.setRight(null);
            System.out.println("已从 BorderPane 移除");

            // 回调通知主界面更新状态
            if (onCloseCallback != null) {
                onCloseCallback.run();
            }
        }
    }

    private void closeWithAnimation() {
        System.out.println("使用动画关闭侧边栏...");

        if (parentContainer == null) {
            return;
        }

        VoicePrintSidePanel sidePanel = this;

        // 平移动画：从左向右滑出
        TranslateTransition transition = new TranslateTransition(Duration.millis(300), sidePanel);
        transition.setFromX(0);       // 从当前位置
        transition.setToX(450);       // 移动到右侧外面
        transition.setOnFinished(e -> {
            parentContainer.setRight(null);      // 移除侧边栏
            System.out.println("侧边栏已移除，状态重置");

            // 回调通知主界面更新状态
            if (onCloseCallback != null) {
                onCloseCallback.run();
            }
        });

        transition.play();
        System.out.println("开始播放关闭动画");
    }

    private String getButtonStyle(String color) {
        return String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-padding: 8 16; -fx-font-size: 13px; -fx-border-radius: 4; -fx-cursor: hand;",
                color
        );
    }

    private String getInputStyle() {
        return "-fx-background-color: white; -fx-border-color: #dde1e7; -fx-border-radius: 4; -fx-padding: 6; -fx-font-size: 13px;";
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
