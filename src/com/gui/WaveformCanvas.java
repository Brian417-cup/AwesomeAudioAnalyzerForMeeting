package com.gui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.File;

public class WaveformCanvas extends Canvas {
    //    播放区域相关变量
    private double[] samples;
    private double playedRatio = 0.0; // 已播放的比例
    private ProgressChangeListener progressListener; // 进度变化监听器

    // 剪辑区域相关字段（保持私有）
    private double selectionStartRatio = -1.0;
    private double selectionEndRatio = -1.0;
    private boolean isSelecting = false;
    private double selectionStartX = 0;
    private SelectionChangeListener selectionListener;


    // 设置进度变化监听器
    public void setProgressChangeListener(ProgressChangeListener listener) {
        this.progressListener = listener;
    }


    // 添加设置尺寸的方法
    public void setCanvasSize(double width, double height) {
        setWidth(width);
        setHeight(height);
        if (samples != null && samples.length > 0) {
            redrawWaveform();
        }
    }

    // 设置选择变化监听器
    public void setSelectionChangeListener(SelectionChangeListener listener) {
        this.selectionListener = listener;
    }

    public WaveformCanvas() {
        this(760, 150); // 使用固定尺寸匹配FXML
    }

    public WaveformCanvas(double width, double height) {
        super(width, height);
    }


    // 添加设置播放进度的方法
    public void setPlayedRatio(double ratio) {
        double clampedRatio = Math.max(0.0, Math.min(1.0, ratio));
        if (Math.abs(clampedRatio - this.playedRatio) > 0.001) { // 只有真正变化时才更新
            this.playedRatio = clampedRatio;
            redrawWaveform();

            // 通知进度变化监听器
            if (progressListener != null) {
                progressListener.onProgressChanged(clampedRatio);
            }
        }
    }

    public void loadAudioFile(File file) {
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(file);
            AudioFormat format = ais.getFormat();

            // 检查是否需要转换格式
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
                ais = AudioSystem.getAudioInputStream(targetFormat, ais);
                format = targetFormat;
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] temp = new byte[4096];
            int bytesRead;

            while ((bytesRead = ais.read(temp)) != -1) {
                buffer.write(temp, 0, bytesRead);
            }

            byte[] bytes = buffer.toByteArray();
            processAudioData(bytes, format);

        } catch (Exception e) {
            e.printStackTrace();
            drawErrorMessage("无法加载音频文件: " + e.getMessage());
        }
    }

    // 获取剪辑状态下选择的时间范围
    public double[] getSelectionTimeRange(double totalDurationSeconds) {
        if (selectionStartRatio < 0 || selectionEndRatio < 0) {
            return new double[]{0, totalDurationSeconds};
        }
        return new double[]{
                selectionStartRatio * totalDurationSeconds,
                selectionEndRatio * totalDurationSeconds
        };
    }

    //    清除选择
    public void clearSelection() {
        selectionStartRatio = -1.0;
        selectionEndRatio = -1.0;
        redrawWaveform();

        if (selectionListener != null) {
            selectionListener.onSelectionChanged(-1, -1);
        }
    }

    private void processAudioData(byte[] audioBytes, AudioFormat format) {
        int sampleSize = format.getSampleSizeInBits() / 8;
        int numSamples = audioBytes.length / sampleSize;

        if (numSamples <= 0) {
            drawErrorMessage("音频格式不支持");
            return;
        }

        samples = new double[Math.min(numSamples, 10000)]; // 限制样本数量以提高性能
        int step = Math.max(1, numSamples / samples.length);

        // 提取样本数据
        for (int i = 0; i < samples.length && i * step < numSamples; i++) {
            int index = i * step;
            if (sampleSize == 2) {
                // 16位音频
                int LSB = audioBytes[index * 2] & 0xff;
                int MSB = audioBytes[index * 2 + 1];
                samples[i] = (MSB << 8 | LSB) / 32768.0;
            } else if (sampleSize == 1) {
                // 8位音频
                samples[i] = (audioBytes[index] & 0xff) / 128.0 - 1.0;
            }
        }

        redrawWaveform();
    }

    private void redrawWaveform() {
        if (samples != null && samples.length > 0) {
            drawWaveformWithProgress(samples);
        }
    }

    private void drawWaveformWithProgress(double[] samples) {
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());

        // 添加背景网格
        drawBackgroundGrid();

        double canvasWidth = getWidth();    // 使用画布的实际宽度
        double height = getHeight();
        double center = height / 2;

        // 计算已播放和未播放的分界点
        int playedSamples = (int) (playedRatio * samples.length);

        // 绘制已播放部分（高透明度）
        gc.setStroke(Color.DODGERBLUE);
        gc.setGlobalAlpha(1.0);
        gc.setLineWidth(1.5);
        drawWaveformSegment(gc, samples, 0, playedSamples, center, canvasWidth);

        // 绘制未播放部分（低透明度）
        gc.setStroke(Color.LIGHTBLUE);
        gc.setGlobalAlpha(0.4);
        gc.setLineWidth(1.0);
        drawWaveformSegment(gc, samples, playedSamples, samples.length, center, canvasWidth);

        // 绘制选择区域（如果有的话）
        if (selectionStartRatio >= 0 && selectionEndRatio >= 0) {
            double startX = selectionStartRatio * canvasWidth;  // 使用canvasWidth而不是width
            double endX = selectionEndRatio * canvasWidth;
            double selectWidth = endX - startX;

            gc.setGlobalAlpha(0.3);
            gc.setFill(Color.YELLOW);
            gc.fillRect(startX, 0, selectWidth, height);

            gc.setGlobalAlpha(1.0);
            gc.setStroke(Color.ORANGE);
            gc.setLineWidth(2.0);
            gc.strokeRect(startX, 0, selectWidth, height);

            gc.setStroke(Color.RED);
            gc.setLineWidth(1.5);
            gc.strokeLine(startX, 0, startX, height);
            gc.strokeLine(endX, 0, endX, height);
        }

        gc.setGlobalAlpha(1.0);
        gc.setLineWidth(1.0);
    }

    private void drawWaveformSegment(GraphicsContext gc, double[] samples, int startIndex, int endIndex, double center, double canvasWidth) {
        if (startIndex >= endIndex) return;

        // 使用canvasWidth而不是固定的width变量
        double segmentWidth = canvasWidth * ((double) (endIndex - startIndex) / samples.length);
        double startX = canvasWidth * ((double) startIndex / samples.length);

        for (int i = startIndex; i < endIndex - 1 && i < samples.length - 1; i++) {
            int x1 = (int) (startX + (i - startIndex) * segmentWidth / (endIndex - startIndex - 1));
            int x2 = (int) (startX + (i + 1 - startIndex) * segmentWidth / (endIndex - startIndex - 1));

            if (x1 < canvasWidth && x2 < canvasWidth) {  // 使用canvasWidth进行边界检查
                double amp1 = samples[i];
                double amp2 = samples[i + 1];

                amp1 = Math.max(-0.9, Math.min(0.9, amp1));
                amp2 = Math.max(-0.9, Math.min(0.9, amp2));

                gc.strokeLine(x1, center - amp1 * (center - 15),
                        x2, center - amp2 * (center - 15));
            }
        }
    }

    // 修改背景网格绘制，使用实际画布宽度
    private void drawBackgroundGrid() {
        GraphicsContext gc = getGraphicsContext2D();
        double canvasWidth = getWidth();  // 使用实际宽度
        double height = getHeight();
        double center = height / 2;

        // 绘制中心线
        gc.setStroke(Color.LIGHTGRAY);
        gc.setLineWidth(0.5);
        gc.strokeLine(0, center, canvasWidth, center);

        // 绘制垂直网格线
        gc.setStroke(Color.LIGHTGRAY.deriveColor(0, 1, 1, 0.3));
        for (int x = 0; x < canvasWidth; x += 50) {
            gc.strokeLine(x, 0, x, height);
        }
    }

    private void drawErrorMessage(String message) {
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());
        gc.setFill(Color.RED);
        gc.fillText(message, 10, 20);
    }


}