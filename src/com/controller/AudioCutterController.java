package com.controller;

import com.gui.WaveformCanvas;
import com.util.AudioCutter;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class AudioCutterController extends HBox {

    public interface ExportSuccessListener {
        void onExportSuccess(File outputFile, double startTime, double endTime);
    }

    private WaveformCanvas waveformCanvas;
    private Label selectionInfoLabel;
    private Button setStartBtn;
    private Button setEndBtn;
    private Button exportBtn;
    private Button jumpToStartBtn;
    private Button jumpToEndBtn;
    private Stage primaryStage;
    private File currentAudioFile;
    private double cutStartTime = -1.0;
    private double cutEndTime = -1.0;
    private double audioDurationSeconds = 0.0;
    private AudioPlayerController audioPlayerController;
    private ExportSuccessListener exportSuccessListener;
    private boolean syncingWaveformSelection = false;

    public AudioCutterController() {
        initializeComponents();
        setupEventHandlers();
    }

    private void initializeComponents() {
        setSpacing(12);
        setPadding(new Insets(12));
        setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-radius: 6; -fx-background-radius: 6;");
        setMinHeight(65);
        setMaxWidth(Double.MAX_VALUE);

        Label titleLabel = new Label("剪切设置:");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #495057; -fx-font-size: 14px;");

        selectionInfoLabel = new Label("未设置剪切区间");
        selectionInfoLabel.setMinWidth(280);
        selectionInfoLabel.setMaxWidth(Double.MAX_VALUE);
        selectionInfoLabel.setStyle("-fx-background-color: #f8d7da; -fx-text-fill: #721c24; -fx-padding: 6; -fx-border-radius: 4; -fx-font-size: 13px;");

        setStartBtn = new Button("设置起点");
        setStartBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 8 14; -fx-font-size: 12px;");

        setEndBtn = new Button("设置终点");
        setEndBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 8 14; -fx-font-size: 12px;");

        jumpToStartBtn = new Button("跳到起点");
        jumpToStartBtn.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-padding: 8 14; -fx-font-size: 12px;");
        jumpToStartBtn.setDisable(true);

        jumpToEndBtn = new Button("跳到终点");
        jumpToEndBtn.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-padding: 8 14; -fx-font-size: 12px;");
        jumpToEndBtn.setDisable(true);

        exportBtn = new Button("导出剪切片段");
        exportBtn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-padding: 8 14; -fx-font-size: 12px;");
        exportBtn.setDisable(true);

        getChildren().addAll(titleLabel, selectionInfoLabel, setStartBtn, setEndBtn, jumpToStartBtn, jumpToEndBtn, exportBtn);
    }

    private void setupEventHandlers() {
        setStartBtn.setOnAction(e -> setCutStartPoint());
        setEndBtn.setOnAction(e -> setCutEndPoint());
        exportBtn.setOnAction(e -> exportCutAudio());
        jumpToStartBtn.setOnAction(e -> jumpToStartTime());
        jumpToEndBtn.setOnAction(e -> jumpToEndTime());
    }

    public void setAudioPlayerController(AudioPlayerController controller) {
        this.audioPlayerController = controller;
    }

    public void setWaveformCanvas(WaveformCanvas canvas) {
        this.waveformCanvas = canvas;
        if (this.waveformCanvas != null) {
            this.waveformCanvas.setSelectionChangeListener(this::onWaveformSelectionChanged);
        }
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    public void setExportSuccessListener(ExportSuccessListener listener) {
        this.exportSuccessListener = listener;
    }

    public void setAudioInfo(File audioFile, double durationSeconds) {
        this.currentAudioFile = audioFile;
        this.audioDurationSeconds = Math.max(0.0, durationSeconds);
        this.cutStartTime = -1.0;
        this.cutEndTime = -1.0;
        updateJumpButtonsState();
        updateSelectionInfo();
        if (waveformCanvas != null) {
            syncingWaveformSelection = true;
            try {
                waveformCanvas.clearSelection();
            } finally {
                syncingWaveformSelection = false;
            }
        }
    }

    private void onWaveformSelectionChanged(double startRatio, double endRatio) {
        if (syncingWaveformSelection) {
            return;
        }

        if (audioDurationSeconds <= 0 || startRatio < 0 || endRatio < 0) {
            this.cutStartTime = -1.0;
            this.cutEndTime = -1.0;
            updateSelectionInfo();
            updateJumpButtonsState();
            return;
        }

        double start = Math.min(startRatio, endRatio) * audioDurationSeconds;
        double end = Math.max(startRatio, endRatio) * audioDurationSeconds;
        setCutRangeInternal(start, end, false);
    }

    private void setCutStartPoint() {
        if (audioPlayerController != null) {
            cutStartTime = audioPlayerController.getCurrentPlaybackTime();
            normalizeRangeIfNeeded();
            syncWaveformFromRange();
            updateSelectionInfo();
            updateJumpButtonsState();
        }
    }

    private void setCutEndPoint() {
        if (audioPlayerController != null) {
            cutEndTime = audioPlayerController.getCurrentPlaybackTime();
            normalizeRangeIfNeeded();
            syncWaveformFromRange();
            updateSelectionInfo();
            updateJumpButtonsState();
        }
    }

    private void normalizeRangeIfNeeded() {
        if (cutStartTime >= 0 && cutEndTime >= 0 && cutStartTime > cutEndTime) {
            double tmp = cutStartTime;
            cutStartTime = cutEndTime;
            cutEndTime = tmp;
        }
    }

    private void jumpToStartTime() {
        if (audioPlayerController != null && cutStartTime >= 0) {
            audioPlayerController.jumpToTime(cutStartTime);
        }
    }

    private void jumpToEndTime() {
        if (audioPlayerController != null && cutEndTime >= 0) {
            audioPlayerController.jumpToTime(cutEndTime);
        }
    }

    public void setCutStartPoint(double time) {
        this.cutStartTime = Math.max(0.0, time);
        normalizeRangeIfNeeded();
        syncWaveformFromRange();
        updateSelectionInfo();
        updateJumpButtonsState();
    }

    public void setCutEndPoint(double time) {
        this.cutEndTime = Math.max(0.0, time);
        normalizeRangeIfNeeded();
        syncWaveformFromRange();
        updateSelectionInfo();
        updateJumpButtonsState();
    }

    public void setCutRange(double startTime, double endTime) {
        setCutRangeInternal(startTime, endTime, true);
    }

    private void setCutRangeInternal(double startTime, double endTime, boolean syncWaveform) {
        if (startTime < 0 || endTime < 0) {
            this.cutStartTime = -1.0;
            this.cutEndTime = -1.0;
        } else {
            this.cutStartTime = Math.min(startTime, endTime);
            this.cutEndTime = Math.max(startTime, endTime);
        }

        if (syncWaveform) {
            syncWaveformFromRange();
        }

        updateSelectionInfo();
        updateJumpButtonsState();
    }

    private void syncWaveformFromRange() {
        if (waveformCanvas == null || audioDurationSeconds <= 0) {
            return;
        }

        syncingWaveformSelection = true;
        try {
            if (cutStartTime >= 0 && cutEndTime >= 0 && cutEndTime > cutStartTime) {
                waveformCanvas.setSelectionRatio(cutStartTime / audioDurationSeconds, cutEndTime / audioDurationSeconds);
            } else {
                waveformCanvas.clearSelection();
            }
        } finally {
            syncingWaveformSelection = false;
        }
    }

    private void updateJumpButtonsState() {
        jumpToStartBtn.setDisable(cutStartTime < 0);
        jumpToEndBtn.setDisable(cutEndTime < 0);
        jumpToStartBtn.setStyle("-fx-background-color: " + (cutStartTime >= 0 ? "#17a2b8" : "#6c757d") + "; -fx-text-fill: white; -fx-padding: 8 14; -fx-font-size: 12px;");
        jumpToEndBtn.setStyle("-fx-background-color: " + (cutEndTime >= 0 ? "#17a2b8" : "#6c757d") + "; -fx-text-fill: white; -fx-padding: 8 14; -fx-font-size: 12px;");
    }

    public double[] getCutRange() {
        return new double[]{cutStartTime, cutEndTime};
    }

    public double getCutStartTime() {
        return cutStartTime;
    }

    public double getCutEndTime() {
        return cutEndTime;
    }

    public boolean canExport() {
        return cutStartTime >= 0 && cutEndTime >= 0 && cutEndTime > cutStartTime && currentAudioFile != null;
    }

    private void updateSelectionInfo() {
        if (cutStartTime < 0 && cutEndTime < 0) {
            selectionInfoLabel.setText("未设置剪切区间");
            selectionInfoLabel.setStyle("-fx-background-color: #f8d7da; -fx-text-fill: #721c24; -fx-padding: 6; -fx-border-radius: 4;");
            exportBtn.setDisable(true);
            return;
        }

        StringBuilder info = new StringBuilder();
        info.append(cutStartTime >= 0 ? "起点: " + formatTime(cutStartTime) : "起点: 未设置");
        info.append("  |  ");
        info.append(cutEndTime >= 0 ? "终点: " + formatTime(cutEndTime) : "终点: 未设置");

        if (cutStartTime >= 0 && cutEndTime >= 0) {
            info.append("  |  时长: ").append(formatTime(Math.max(0, cutEndTime - cutStartTime)));
        }

        selectionInfoLabel.setText(info.toString());

        if (canExport()) {
            selectionInfoLabel.setStyle("-fx-background-color: #d4edda; -fx-text-fill: #155724; -fx-padding: 6; -fx-border-radius: 4;");
        } else {
            selectionInfoLabel.setStyle("-fx-background-color: #fff3cd; -fx-text-fill: #856404; -fx-padding: 6; -fx-border-radius: 4;");
        }

        exportBtn.setDisable(!canExport());
    }

    private void exportCutAudio() {
        if (currentAudioFile == null) {
            showError("导出错误", "请先加载音频文件");
            return;
        }
        if (cutStartTime < 0 || cutEndTime < 0) {
            showError("导出错误", "请先设置起点和终点");
            return;
        }
        if (cutEndTime <= cutStartTime) {
            showError("导出错误", "终点必须晚于起点");
            return;
        }

        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("导出剪切音频");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV 文件", "*.wav"));

            String originalName = currentAudioFile.getName();
            String nameWithoutExt = originalName.contains(".")
                    ? originalName.substring(0, originalName.lastIndexOf('.'))
                    : originalName;
            String startTimeStr = formatTime(cutStartTime).replace(":", "");
            String endTimeStr = formatTime(cutEndTime).replace(":", "");
            fileChooser.setInitialFileName(nameWithoutExt + "_剪切_" + startTimeStr + "_" + endTimeStr + ".wav");

            File outputFile = fileChooser.showSaveDialog(primaryStage);
            if (outputFile == null) {
                return;
            }

            AudioCutter.cut(currentAudioFile, outputFile, cutStartTime, cutEndTime);
            if (exportSuccessListener != null) {
                exportSuccessListener.onExportSuccess(outputFile, cutStartTime, cutEndTime);
            }

            Platform.runLater(() -> {
                Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                successAlert.setTitle("导出完成");
                successAlert.setHeaderText(null);
                successAlert.setContentText(String.format(
                        "剪切成功\n时间范围: %s - %s\n时长: %s\n保存位置: %s",
                        formatTime(cutStartTime),
                        formatTime(cutEndTime),
                        formatTime(cutEndTime - cutStartTime),
                        outputFile.getAbsolutePath()
                ));
                successAlert.showAndWait();
            });
        } catch (Exception e) {
            showError("导出失败", "剪切过程中发生错误: " + e.getMessage());
        }
    }

    private String formatTime(double seconds) {
        int totalSeconds = (int) seconds;
        int minutes = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
