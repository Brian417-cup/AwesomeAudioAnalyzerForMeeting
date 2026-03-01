package com.controller;

import com.gui.WaveformCanvas;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.fxml.Initializable;

import java.net.URL;
import java.io.File;
import java.util.ResourceBundle;
import javax.sound.sampled.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AudioPlayerController implements Initializable {

    @FXML
    private Button openBtn;

    @FXML
    private Button playBtn;

    @FXML
    private Button pauseBtn;

    @FXML
    private Button forwardBtn;

    @FXML
    private Button backwardBtn;

    @FXML
    private Slider progressSlider;

    @FXML
    private Label timeLabel;

    @FXML
    private TextField filePathField;

    @FXML
    private Label statusLabel;

    @FXML
    private WaveformCanvas waveformCanvas;

    @FXML
    private AudioCutterController audioCutterController;

    private Clip audioClip;
    private ScheduledExecutorService scheduler;
    private Stage primaryStage;
    private boolean isPlaying = false;
    private double lastPlayedRatio = 0.0;
    private File currentAudioFile;
    private double totalDurationSeconds = 0.0;
    private String currentFilePath = "";
    private double windowWidth = 800;

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;

        // 在这里设置窗口大小监听器，因为现在primaryStage不为null了
        if (primaryStage != null) {
            primaryStage.widthProperty().addListener((obs, oldVal, newVal) -> {
                windowWidth = newVal.doubleValue();
                updateLayoutSizes();
            });
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        setupEventHandlers();

        // 初始化剪辑控制组件
        initializeAudioCutter();

        // 初始化状态显示
        updateStatus("就绪");
    }

    private void initializeAudioCutter() {
        audioCutterController.setWaveformCanvas(waveformCanvas);
        audioCutterController.setPrimaryStage(primaryStage);
        audioCutterController.setAudioPlayerController(this);
    }


    private void updateLayoutSizes() {
        // 根据窗口宽度调整各组件尺寸
        double availableWidth = windowWidth - 80; // 减去padding和边距
        double componentWidth = Math.max(400, availableWidth); // 最小400像素

        // 同步更新波形画布和进度条的宽度
        waveformCanvas.setCanvasSize(componentWidth, 150);
        progressSlider.setPrefWidth(componentWidth);

        // 重新加载音频数据以适应新尺寸
        if (currentAudioFile != null) {
            try {
                waveformCanvas.loadAudioFile(currentAudioFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 提供当前播放时间给剪辑控制器
    public double getCurrentPlaybackTime() {
        if (audioClip != null) {
            return audioClip.getMicrosecondPosition() / 1_000_000.0;
        }
        return 0.0;
    }

    private void setupEventHandlers() {
        openBtn.setOnAction(e -> openFile());
        playBtn.setOnAction(e -> playAudio());
        pauseBtn.setOnAction(e -> pauseAudio());
        forwardBtn.setOnAction(e -> seekForward());
        backwardBtn.setOnAction(e -> seekBackward());

        progressSlider.setOnMouseReleased(e -> seekToPosition());
    }

    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Supported Audio Files", "*.wav", "*.au", "*.aiff")
        );

        File file = chooser.showOpenDialog(primaryStage);
        if (file != null) {
            try {
                currentAudioFile = file;
                currentFilePath = file.getAbsolutePath();
                loadAudioFile(file);

                // 更新文件路径显示
                filePathField.setText(currentFilePath);
                filePathField.setTooltip(new Tooltip(currentFilePath));

                String duration = formatDuration(getAudioDuration(file));
                timeLabel.setText("✓ 音频加载成功 (" + duration + ")");
                updateStatus("已加载: " + file.getName());

                waveformCanvas.loadAudioFile(file);

            } catch (Exception e) {
                e.printStackTrace();
                showError("文件加载失败", "无法加载音频文件: " + e.getMessage());
                updateStatus("加载失败");
            }
        }
    }

    // 复制文件路径到剪贴板
    @FXML
    private void copyFilePath() {
        if (currentFilePath != null && !currentFilePath.isEmpty()) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(currentFilePath);
            clipboard.setContent(content);
            updateStatus("路径已复制到剪贴板");
        }
    }

    private void loadAudioFile(File file) throws Exception {
        // 停止当前播放
        if (audioClip != null && audioClip.isRunning()) {
            audioClip.stop();
        }
        if (audioClip != null) {
            audioClip.close();
        }

        // 加载音频文件
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
        AudioFormat format = audioStream.getFormat();

        // 如果不是PCM格式，转换为PCM格式
        if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    format.getSampleRate(),
                    16,
                    format.getChannels(),
                    format.getChannels() * 2,
                    format.getSampleRate(),
                    false
            );
            audioStream = AudioSystem.getAudioInputStream(targetFormat, audioStream);
        }

        DataLine.Info info = new DataLine.Info(Clip.class, audioStream.getFormat());
        audioClip = (Clip) AudioSystem.getLine(info);
        audioClip.open(audioStream);

        // 设置进度条
        long durationInMicroseconds = audioClip.getMicrosecondLength();
        double durationInSeconds = durationInMicroseconds / 1_000_000.0;

        progressSlider.setMax(durationInSeconds);

        // 更新剪辑控制组件的信息
        totalDurationSeconds = durationInSeconds;
        audioCutterController.setAudioInfo(file, durationInSeconds);

        // 清除之前的选区
        waveformCanvas.clearSelection();

        // 设置监听器
        setupAudioListeners();
    }

    private void playAudio() {
        if (audioClip != null) {
            // 如果已经播放到末尾，重置到开始位置
            if (audioClip.getMicrosecondPosition() >= audioClip.getMicrosecondLength() - 100000) {
                audioClip.setMicrosecondPosition(0);
                lastPlayedRatio = 0.0;

                javafx.application.Platform.runLater(() -> {
                    resetUI();
                });
            }

            if (!audioClip.isRunning()) {
                audioClip.start();
                isPlaying = true;
                startProgressUpdater();
                updateStatus("播放中...");
            }
        }
    }

    private void pauseAudio() {
        if (audioClip != null && audioClip.isRunning()) {
            audioClip.stop();
            isPlaying = false;
            updateStatus("已暂停");
        }
    }

    private void seekForward() {
        if (audioClip != null) {
            long currentPosition = audioClip.getMicrosecondPosition();
            long newPosition = Math.min(
                    currentPosition + 5_000_000,
                    audioClip.getMicrosecondLength()
            );
            audioClip.setMicrosecondPosition(newPosition);
        }
    }

    private void seekBackward() {
        if (audioClip != null) {
            long currentPosition = audioClip.getMicrosecondPosition();
            long newPosition = Math.max(
                    currentPosition - 5_000_000,
                    0
            );
            audioClip.setMicrosecondPosition(newPosition);
        }
    }

    private void seekToPosition() {
        if (audioClip != null) {
            double seconds = progressSlider.getValue();
            long microseconds = (long) (seconds * 1_000_000);
            audioClip.setMicrosecondPosition(microseconds);

            double ratio = seconds / progressSlider.getMax();
            lastPlayedRatio = ratio;
            waveformCanvas.setPlayedRatio(ratio);
        }
    }

    // 跳转到指定时间的方法
    public void jumpToTime(double seconds) {
        if (audioClip != null) {
            // 保存当前播放状态
            boolean wasPlaying = isPlaying && audioClip.isRunning();

            // 停止当前播放（如果正在播放）
            if (audioClip.isRunning()) {
                audioClip.stop();
            }

            // 设置新的位置
            long microseconds = (long) (seconds * 1_000_000);
            audioClip.setMicrosecondPosition(microseconds);

            // 更新内部状态
            double ratio = seconds / progressSlider.getMax();
            lastPlayedRatio = ratio;
            waveformCanvas.setPlayedRatio(ratio);

            // 更新UI
            javafx.application.Platform.runLater(() -> {
                progressSlider.setValue(seconds);
                timeLabel.setText(formatTime(seconds) + " / " + formatTime(progressSlider.getMax()));
            });

            // 如果之前在播放，重新开始播放
            if (wasPlaying) {
                // 使用Timer延迟启动，确保状态同步
                java.util.Timer timer = new java.util.Timer();
                timer.schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        javafx.application.Platform.runLater(() -> {
                            if (isPlaying && audioClip != null) {
                                audioClip.start();
                            }
                        });
                        timer.cancel();
                    }
                }, 50); // 50毫秒延迟
            }
        }
    }

    private void resetUI() {
        progressSlider.setValue(0);
        waveformCanvas.setPlayedRatio(0.0);
        lastPlayedRatio = 0.0;
        if (audioClip != null) {
            long totalDuration = audioClip.getMicrosecondLength();
            double totalSeconds = totalDuration / 1_000_000.0;
            timeLabel.setText("00:00 / " + formatTime(totalSeconds));
        }
    }

    private void setupAudioListeners() {
        if (audioClip != null) {
            audioClip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    if (isPlaying && audioClip.getMicrosecondPosition() >= audioClip.getMicrosecondLength() - 100000) {
                        javafx.application.Platform.runLater(() -> {
                            resetUI();
                            isPlaying = false;
                            updateStatus("播放完成");
                        });
                    }
                }
            });
        }
    }

    private void startProgressUpdater() {
        scheduler.scheduleAtFixedRate(() -> {
            if (isPlaying && audioClip != null && audioClip.isRunning()) {
                updateProgressFromWaveform();
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
    }

    private void updateProgressFromWaveform() {
        if (audioClip == null) return;

        long currentPosition = audioClip.getMicrosecondPosition();
        long totalDuration = audioClip.getMicrosecondLength();

        if (totalDuration <= 0) return;

        double currentSeconds = (double) currentPosition / 1_000_000.0;
        double totalSeconds = (double) totalDuration / 1_000_000.0;
        double progressRatio = currentPosition / (double) totalDuration;

        if (Math.abs(progressRatio - lastPlayedRatio) > 0.0001) {
            lastPlayedRatio = progressRatio;

            javafx.application.Platform.runLater(() -> {
                try {
                    progressSlider.setValue(currentSeconds);
                    timeLabel.setText(formatTime(currentSeconds) + " / " + formatTime(totalSeconds));
                    waveformCanvas.setPlayedRatio(progressRatio);
                } catch (Exception e) {
                    // 静默处理异常
                }
            });
        }
    }

    // 更新状态显示
    private void updateStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status);
        }
    }

    private long getAudioDuration(File file) {
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(file);
            AudioFormat format = ais.getFormat();
            return ais.getFrameLength() * 1000 / (long) format.getFrameRate();
        } catch (Exception e) {
            return 0;
        }
    }

    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
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

    public void cleanup() {
        isPlaying = false;
        if (audioClip != null) {
            if (audioClip.isRunning()) {
                audioClip.stop();
            }
            audioClip.close();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
