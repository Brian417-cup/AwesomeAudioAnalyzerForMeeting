package com.controller;

import com.gui.WaveformCanvas;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import com.util.AudioCutter;

import java.io.File;

public class AudioCutterController extends HBox {
    private WaveformCanvas waveformCanvas;
    private Label selectionInfoLabel;
    private Button setStartBtn;
    private Button setEndBtn;
    private Button exportBtn;
    private Button jumpToStartBtn;  // 新增：跳转到起点按钮
    private Button jumpToEndBtn;    // 新增：跳转到终点按钮
    private Stage primaryStage;
    private File currentAudioFile;
    private double totalDurationSeconds;
    private double cutStartTime = -1.0;
    private double cutEndTime = -1.0;
    private AudioPlayerController audioPlayerController;

    public AudioCutterController() {
        initializeComponents();
        setupEventHandlers();
    }

    private void initializeComponents() {
        setSpacing(12);
        setPadding(new Insets(12));
        setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-radius: 5; -fx-background-radius: 5;");

        // 设置最小尺寸和弹性布局
        setMinHeight(65);
        setPrefWidth(USE_COMPUTED_SIZE);
        setMaxWidth(Double.MAX_VALUE);

        Label titleLabel = new Label("📋 剪辑设置:");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #495057; -fx-font-size: 15px;");

        selectionInfoLabel = new Label("❌ 未设置剪辑点");
        selectionInfoLabel.setMinWidth(220);
        selectionInfoLabel.setMaxWidth(Double.MAX_VALUE);
        selectionInfoLabel.setStyle("-fx-background-color: #f8d7da; -fx-text-fill: #721c24; -fx-padding: 6; -fx-border-radius: 3; -fx-font-size: 13px;");

        setStartBtn = new Button("📍 设置起点");
        setStartBtn.setStyle("-fx-background-color: #007bff; -fx-text-fill: white; -fx-padding: 10 18; -fx-font-size: 13px;");

        setEndBtn = new Button("🏁 设置终点");
        setEndBtn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-padding: 10 18; -fx-font-size: 13px;");

        // 跳转按钮
        jumpToStartBtn = new Button("⏮ 跳转起点");
        jumpToStartBtn.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-padding: 8 15; -fx-font-size: 12px;");
        jumpToStartBtn.setDisable(true);

        jumpToEndBtn = new Button("⏭ 跳转终点");
        jumpToEndBtn.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-padding: 8 15; -fx-font-size: 12px;");
        jumpToEndBtn.setDisable(true);

        exportBtn = new Button("💾 导出剪辑");
        exportBtn.setStyle("-fx-background-color: #ffc107; -fx-text-fill: #212529; -fx-padding: 10 18; -fx-font-size: 13px;");
        exportBtn.setDisable(true);

        getChildren().addAll(
                titleLabel,
                selectionInfoLabel,
                setStartBtn,
                setEndBtn,
                jumpToStartBtn,
                jumpToEndBtn,
                exportBtn
        );
    }
    private void setupEventHandlers() {
        setStartBtn.setOnAction(e -> setCutStartPoint());
        setEndBtn.setOnAction(e -> setCutEndPoint());
        exportBtn.setOnAction(e -> exportCutAudio());

        // 新增跳转事件处理
        jumpToStartBtn.setOnAction(e -> jumpToStartTime());
        jumpToEndBtn.setOnAction(e -> jumpToEndTime());
    }

    // 设置播放器控制器引用
    public void setAudioPlayerController(AudioPlayerController controller) {
        this.audioPlayerController = controller;
    }

    // 设置关联的波形画布
    public void setWaveformCanvas(WaveformCanvas canvas) {
        this.waveformCanvas = canvas;
    }

    // 设置主窗口引用
    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    // 设置当前音频文件和时长
    public void setAudioInfo(File audioFile, double durationSeconds) {
        this.currentAudioFile = audioFile;
        this.totalDurationSeconds = durationSeconds;
        this.cutStartTime = -1.0;
        this.cutEndTime = -1.0;
        updateJumpButtonsState();  // 更新跳转按钮状态
        updateSelectionInfo();
    }

    // 设置剪辑起点
    private void setCutStartPoint() {
        if (audioPlayerController != null) {
            cutStartTime = audioPlayerController.getCurrentPlaybackTime();
            updateSelectionInfo();
            updateJumpButtonsState();
        }
    }

    // 设置剪辑终点
    private void setCutEndPoint() {
        if (audioPlayerController != null) {
            cutEndTime = audioPlayerController.getCurrentPlaybackTime();
            updateSelectionInfo();
            updateJumpButtonsState();
        }
    }

    // 跳转到起点时间
    private void jumpToStartTime() {
        if (audioPlayerController != null && cutStartTime >= 0) {
            audioPlayerController.jumpToTime(cutStartTime);
        }
    }

    // 跳转到终点时间
    private void jumpToEndTime() {
        if (audioPlayerController != null && cutEndTime >= 0) {
            audioPlayerController.jumpToTime(cutEndTime);
        }
    }

    // 公共方法：设置剪辑起点（供外部调用）
    public void setCutStartPoint(double time) {
        this.cutStartTime = time;
        updateSelectionInfo();
        updateJumpButtonsState();
    }

    // 公共方法：设置剪辑终点（供外部调用）
    public void setCutEndPoint(double time) {
        this.cutEndTime = time;
        updateSelectionInfo();
        updateJumpButtonsState();
    }

    // 更新跳转按钮状态
    private void updateJumpButtonsState() {
        jumpToStartBtn.setDisable(cutStartTime < 0);
        jumpToEndBtn.setDisable(cutEndTime < 0);

        // 更新按钮样式和字体
        if (cutStartTime >= 0) {
            jumpToStartBtn.setStyle("-fx-background-color: #17a2b8; -fx-text-fill: white; -fx-padding: 8 15; -fx-font-size: 12px;");
        } else {
            jumpToStartBtn.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-padding: 8 15; -fx-font-size: 12px;");
        }

        if (cutEndTime >= 0) {
            jumpToEndBtn.setStyle("-fx-background-color: #17a2b8; -fx-text-fill: white; -fx-padding: 8 15; -fx-font-size: 12px;");
        } else {
            jumpToEndBtn.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-padding: 8 15; -fx-font-size: 12px;");
        }
    }
    // 公共方法：获取当前剪辑范围
    public double[] getCutRange() {
        return new double[]{cutStartTime, cutEndTime};
    }

    // 公共方法：检查是否可以导出
    public boolean canExport() {
        return cutStartTime >= 0 && cutEndTime >= 0 &&
                cutEndTime > cutStartTime &&
                currentAudioFile != null;
    }

    private void updateSelectionInfo() {
        if (cutStartTime < 0 && cutEndTime < 0) {
            selectionInfoLabel.setText("❌ 未设置剪辑点");
            selectionInfoLabel.setStyle("-fx-background-color: #f8d7da; -fx-text-fill: #721c24; -fx-padding: 5; -fx-border-radius: 3;");
            exportBtn.setDisable(true);
            return;
        }

        StringBuilder info = new StringBuilder();
        if (cutStartTime >= 0) {
            info.append("🟢 起点: ").append(formatTime(cutStartTime));
        } else {
            info.append("🔴 起点: 未设置");
        }
        info.append(" │ ");
        if (cutEndTime >= 0) {
            info.append("🔵 终点: ").append(formatTime(cutEndTime));
        } else {
            info.append("🔴 终点: 未设置");
        }

        if (cutStartTime >= 0 && cutEndTime >= 0) {
            double duration = Math.abs(cutEndTime - cutStartTime);
            info.append(" ⏱ 时长: ").append(formatTime(duration));
        }

        selectionInfoLabel.setText(info.toString());
        selectionInfoLabel.setStyle("-fx-background-color: #d4edda; -fx-text-fill: #155724; -fx-padding: 5; -fx-border-radius: 3;");
        updateExportButtonState();
    }

    private void updateExportButtonState() {
        boolean canExport = cutStartTime >= 0 && cutEndTime >= 0 &&
                cutEndTime > cutStartTime &&
                currentAudioFile != null;
        exportBtn.setDisable(!canExport);
    }

    private void exportCutAudio() {
        if (currentAudioFile == null) {
            showError("导出错误", "请先加载音频文件");
            return;
        }

        if (cutStartTime < 0 || cutEndTime < 0) {
            showError("导出错误", "请先设置剪辑起点和终点");
            return;
        }

        if (cutEndTime <= cutStartTime) {
            showError("导出错误", "终点必须晚于起点");
            return;
        }

        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("导出剪辑音频");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("WAV Files", "*.wav")
            );

            String originalName = currentAudioFile.getName();
            String nameWithoutExt = originalName.substring(0, originalName.lastIndexOf('.'));
            String startTimeStr = formatTime(cutStartTime).replace(":", "");
            String endTimeStr = formatTime(cutEndTime).replace(":", "");
            fileChooser.setInitialFileName(nameWithoutExt + "_剪辑_" + startTimeStr + "_" + endTimeStr + ".wav");

            File outputFile = fileChooser.showSaveDialog(primaryStage);

            if (outputFile != null) {
                AudioCutter.cut(currentAudioFile, outputFile, cutStartTime, cutEndTime);

                javafx.application.Platform.runLater(() -> {
                    javafx.scene.control.Alert successAlert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                    successAlert.setTitle("🎉 导出完成");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText(String.format(
                            "音频剪辑成功!\n时间范围: %s - %s\n时长: %s\n保存位置: %s",
                            formatTime(cutStartTime),
                            formatTime(cutEndTime),
                            formatTime(cutEndTime - cutStartTime),
                            outputFile.getAbsolutePath()
                    ));
                    successAlert.showAndWait();
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            showError("导出失败", "剪辑过程中发生错误: " + e.getMessage());
        }
    }

    private String formatTime(double seconds) {
        int totalSeconds = (int) seconds;
        int minutes = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    private void showError(String title, String message) {
        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
