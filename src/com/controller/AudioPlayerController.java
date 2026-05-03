package com.controller;

import com.gui.WaveformCanvas;
import com.recognition.AudioSourceResolver;
import com.recognition.SherpaOnnxConfigStore;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AudioPlayerController implements Initializable {

    private final AudioSourceResolver audioSourceResolver = new AudioSourceResolver();

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
    private TextField jumpTimeField;
    @FXML
    private Button jumpTimeBtn;
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
    private String currentFilePath = "";
    private double windowWidth = 800;
    private boolean progressUpdaterStarted = false;
    private File previewAudioFile;

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
        initializeAudioCutter();
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
        initializeAudioCutter();
        startProgressUpdater();
        updatePlaybackButtonState(false);
        updateStatus("就绪");
    }

    private void initializeAudioCutter() {
        if (audioCutterController != null) {
            audioCutterController.setWaveformCanvas(waveformCanvas);
            audioCutterController.setPrimaryStage(primaryStage);
            audioCutterController.setAudioPlayerController(this);
        }
    }

    private void updateLayoutSizes() {
        double availableWidth = windowWidth - 80;
        double componentWidth = Math.max(400, availableWidth);
        waveformCanvas.setCanvasSize(componentWidth, 150);
        progressSlider.setMinWidth(componentWidth);
        progressSlider.setPrefWidth(componentWidth);
        progressSlider.setMaxWidth(componentWidth);

        if (currentAudioFile != null) {
            try {
                waveformCanvas.loadAudioFile(currentAudioFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public double getCurrentPlaybackTime() {
        if (audioClip != null) {
            return audioClip.getMicrosecondPosition() / 1_000_000.0;
        }
        return 0.0;
    }

    public File getCurrentAudioFile() {
        return currentAudioFile;
    }

    public AudioCutterController getAudioCutterController() {
        return audioCutterController;
    }

    public void setCutRange(double startSeconds, double endSeconds) {
        if (audioCutterController != null) {
            audioCutterController.setCutRange(startSeconds, endSeconds);
        }
    }

    public void setOpenButtonEnabled(boolean enabled) {
        if (openBtn != null) {
            openBtn.setDisable(!enabled);
        }
    }

    public void openExternalFile(File file) throws Exception {
        if (file == null) {
            return;
        }

        currentAudioFile = file;
        currentFilePath = file.getAbsolutePath();
        loadAudioFile(file);

        filePathField.setText(currentFilePath);
        filePathField.setTooltip(new Tooltip(currentFilePath));

        String duration = formatDuration(getAudioDuration(file));
        timeLabel.setText("音频加载成功 (" + duration + ")");
        updateStatus("已加载: " + file.getName());
        updatePlaybackButtonState(false);
    }

    private void setupEventHandlers() {
        openBtn.setOnAction(e -> openFile());
        playBtn.setOnAction(e -> playAudio());
        pauseBtn.setOnAction(e -> pauseAudio());
        forwardBtn.setOnAction(e -> seekForward());
        backwardBtn.setOnAction(e -> seekBackward());
        progressSlider.setOnMouseReleased(e -> seekToPosition());
        if (jumpTimeField != null) {
            jumpTimeField.setTooltip(new Tooltip("输入 mm:ss 或 hh:mm:ss 后回车，可跳转到指定时间。"));
            jumpTimeField.setOnAction(e -> jumpFromTimeField());
        }
        if (jumpTimeBtn != null) {
            jumpTimeBtn.setTooltip(new Tooltip("跳转到左侧输入的时间点。"));
            jumpTimeBtn.setOnAction(e -> jumpFromTimeField());
        }
    }

    private void openFile() {
        FileChooser chooser = new FileChooser();
        configureAudioChooser(chooser);

        File file = chooser.showOpenDialog(primaryStage);
        if (file != null) {
            try {
                openExternalFile(file);
            } catch (Exception e) {
                e.printStackTrace();
                showError("文件加载失败", "无法加载音频文件: " + e.getMessage());
                updateStatus("加载失败");
            }
        }
    }

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
        if (audioClip != null && audioClip.isRunning()) {
            audioClip.stop();
        }
        if (audioClip != null) {
            audioClip.close();
        }

        AudioSourceResolver.ResolvedAudio resolvedAudio = audioSourceResolver.resolveForProcessing(file, "player/editor-preview");
        previewAudioFile = resolvedAudio.getWorkingFile();

        AudioInputStream audioStream = AudioSystem.getAudioInputStream(previewAudioFile);
        AudioFormat format = audioStream.getFormat();

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

        long durationInMicroseconds = audioClip.getMicrosecondLength();
        double durationInSeconds = durationInMicroseconds / 1_000_000.0;
        progressSlider.setMax(durationInSeconds);

        if (audioCutterController != null) {
            audioCutterController.setAudioInfo(file, durationInSeconds);
        }
        waveformCanvas.clearSelection();
        waveformCanvas.loadAudioFile(previewAudioFile);

        setupAudioListeners();
        resetUI();
    }

    private void playAudio() {
        if (audioClip != null) {
            if (audioClip.getMicrosecondPosition() >= audioClip.getMicrosecondLength() - 100000) {
                audioClip.setMicrosecondPosition(0);
                lastPlayedRatio = 0.0;
                Platform.runLater(this::resetUI);
            }

            if (!audioClip.isRunning()) {
                audioClip.start();
                isPlaying = true;
                updateStatus("播放中...");
                updatePlaybackButtonState(true);
            }
        }
    }

    private void pauseAudio() {
        if (audioClip != null && audioClip.isRunning()) {
            audioClip.stop();
            isPlaying = false;
            updateStatus("已暂停");
        }
        updatePlaybackButtonState(false);
    }

    private void seekForward() {
        if (audioClip != null) {
            double currentSeconds = audioClip.getMicrosecondPosition() / 1_000_000.0;
            jumpToTime(currentSeconds + 5);
        }
    }

    private void seekBackward() {
        if (audioClip != null) {
            double currentSeconds = audioClip.getMicrosecondPosition() / 1_000_000.0;
            jumpToTime(currentSeconds - 5);
        }
    }

    private void seekToPosition() {
        if (audioClip != null) {
            double seconds = progressSlider.getValue();
            jumpToTime(seconds);
        }
    }

    public void jumpToTime(double seconds) {
        if (audioClip != null) {
            boolean wasPlaying = isPlaying && audioClip.isRunning();
            if (audioClip.isRunning()) {
                audioClip.stop();
            }

            double maxSeconds = progressSlider != null ? progressSlider.getMax() : audioClip.getMicrosecondLength() / 1_000_000.0;
            double targetSeconds = Math.max(0, Math.min(seconds, maxSeconds));
            long microseconds = (long) (targetSeconds * 1_000_000);
            audioClip.setMicrosecondPosition(microseconds);

            double ratio = progressSlider.getMax() > 0 ? targetSeconds / progressSlider.getMax() : 0;
            lastPlayedRatio = ratio;
            waveformCanvas.setPlayedRatio(ratio);

            Platform.runLater(() -> {
                progressSlider.setValue(targetSeconds);
                timeLabel.setText(formatTime(targetSeconds) + " / " + formatTime(progressSlider.getMax()));
                if (jumpTimeField != null) {
                    jumpTimeField.setText(formatTime(targetSeconds));
                }
            });

            if (wasPlaying) {
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Platform.runLater(() -> {
                            if (isPlaying && audioClip != null) {
                                audioClip.start();
                            }
                        });
                        timer.cancel();
                    }
                }, 50);
            }
        }
    }

    private void jumpFromTimeField() {
        if (audioClip == null) {
            showError("无法定位", "请先加载音频文件。");
            return;
        }
        if (jumpTimeField == null) {
            return;
        }

        try {
            double seconds = parseTimeToSeconds(jumpTimeField.getText());
            jumpToTime(seconds);
            updateStatus("已定位到 " + formatTime(Math.min(seconds, progressSlider.getMax())));
        } catch (IllegalArgumentException e) {
            showError("时间格式不正确", e.getMessage());
        }
    }

    private void resetUI() {
        progressSlider.setValue(0);
        waveformCanvas.setPlayedRatio(0.0);
        lastPlayedRatio = 0.0;
        updatePlaybackButtonState(false);
        if (audioClip != null) {
            long totalDuration = audioClip.getMicrosecondLength();
            double totalSeconds = totalDuration / 1_000_000.0;
            timeLabel.setText("00:00 / " + formatTime(totalSeconds));
            if (jumpTimeField != null) {
                jumpTimeField.setText("00:00");
            }
        }
    }

    private void setupAudioListeners() {
        if (audioClip != null) {
            audioClip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    if (isPlaying && audioClip.getMicrosecondPosition() >= audioClip.getMicrosecondLength() - 100000) {
                        Platform.runLater(() -> {
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
        if (progressUpdaterStarted) {
            return;
        }
        progressUpdaterStarted = true;

        scheduler.scheduleAtFixedRate(() -> {
            if (isPlaying && audioClip != null && audioClip.isRunning()) {
                updateProgressFromWaveform();
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
    }

    private void updateProgressFromWaveform() {
        if (audioClip == null) {
            return;
        }

        long currentPosition = audioClip.getMicrosecondPosition();
        long totalDuration = audioClip.getMicrosecondLength();
        if (totalDuration <= 0) {
            return;
        }

        double currentSeconds = (double) currentPosition / 1_000_000.0;
        double totalSeconds = (double) totalDuration / 1_000_000.0;
        double progressRatio = currentPosition / (double) totalDuration;

        if (Math.abs(progressRatio - lastPlayedRatio) > 0.0001) {
            lastPlayedRatio = progressRatio;
            Platform.runLater(() -> {
                try {
                    progressSlider.setValue(currentSeconds);
                    timeLabel.setText(formatTime(currentSeconds) + " / " + formatTime(totalSeconds));
                    waveformCanvas.setPlayedRatio(progressRatio);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private void updateStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status);
        }
    }

    private void updatePlaybackButtonState(boolean playing) {
        if (playBtn != null) {
            playBtn.setStyle(buildPlaybackButtonStyle("#27ae60", playing));
        }
        if (pauseBtn != null) {
            pauseBtn.setStyle(buildPlaybackButtonStyle("#e74c3c", !playing));
        }
    }

    private String buildPlaybackButtonStyle(String color, boolean selected) {
        String background = selected ? "derive(" + color + ", -12%)" : color;
        String border = selected ? "#1f2d3d" : "transparent";
        String borderWidth = selected ? "2" : "0";
        return "-fx-background-color: " + background + "; "
                + "-fx-text-fill: white; "
                + "-fx-padding: 8 16; "
                + "-fx-font-size: 14px; "
                + "-fx-font-weight: bold; "
                + "-fx-border-radius: 4; "
                + "-fx-background-radius: 4; "
                + "-fx-border-color: " + border + "; "
                + "-fx-border-width: " + borderWidth + ";";
    }

    private long getAudioDuration(File file) {
        try {
            File sourceFile = previewAudioFile != null ? previewAudioFile : file;
            AudioInputStream ais = AudioSystem.getAudioInputStream(sourceFile);
            AudioFormat format = ais.getFormat();
            return ais.getFrameLength() * 1000 / (long) format.getFrameRate();
        } catch (Exception e) {
            return 0;
        }
    }

    private void configureAudioChooser(FileChooser chooser) {
        chooser.getExtensionFilters().clear();
        try {
            SherpaOnnxConfigStore configStore = SherpaOnnxConfigStore.loadDefaultStore();
            chooser.getExtensionFilters().add(configStore.buildAudioExtensionFilter());
        } catch (Exception e) {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("音频文件", "*.wav", "*.mp3", "*.m4a", "*.aac")
            );
        }
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("所有文件", "*.*"));
    }

    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String formatTime(double seconds) {
        int totalSeconds = (int) Math.max(0, Math.round(seconds));
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int secs = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%02d:%02d", minutes, secs);
    }

    private double parseTimeToSeconds(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入要跳转的时间，例如 03:15。");
        }

        String text = raw.trim();
        if (text.matches("\\d+(\\.\\d+)?")) {
            return Double.parseDouble(text);
        }

        String[] parts = text.split(":");
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException("时间格式应为 mm:ss 或 hh:mm:ss。");
        }

        double seconds = 0;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (!part.matches("\\d+(\\.\\d+)?")) {
                throw new IllegalArgumentException("时间格式应为数字，例如 03:15。");
            }
            double value = Double.parseDouble(part);
            if (i > 0 && value >= 60) {
                throw new IllegalArgumentException("分钟和秒数应小于 60。");
            }
            seconds = seconds * 60 + value;
        }

        return seconds;
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

    public void cleanup() {
        isPlaying = false;
        updatePlaybackButtonState(false);
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
