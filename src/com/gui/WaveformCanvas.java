package com.gui;

import com.recognition.AudioSourceResolver;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayOutputStream;
import java.io.File;

public class WaveformCanvas extends Canvas {
    private final AudioSourceResolver audioSourceResolver = new AudioSourceResolver();
    private double[] samples;
    private double playedRatio = 0.0;
    private ProgressChangeListener progressListener;

    private double selectionStartRatio = -1.0;
    private double selectionEndRatio = -1.0;
    private SelectionChangeListener selectionListener;
    private boolean draggingSelection = false;
    private double dragStartRatio = -1.0;

    public void setProgressChangeListener(ProgressChangeListener listener) {
        this.progressListener = listener;
    }

    public void setCanvasSize(double width, double height) {
        setWidth(width);
        setHeight(height);
        if (samples != null && samples.length > 0) {
            redrawWaveform();
        }
    }

    public void setSelectionChangeListener(SelectionChangeListener listener) {
        this.selectionListener = listener;
    }

    public WaveformCanvas() {
        this(760, 150);
    }

    public WaveformCanvas(double width, double height) {
        super(width, height);
        setupMouseHandlers();
    }

    public void setPlayedRatio(double ratio) {
        double clampedRatio = Math.max(0.0, Math.min(1.0, ratio));
        if (Math.abs(clampedRatio - this.playedRatio) > 0.001) {
            this.playedRatio = clampedRatio;
            redrawWaveform();
            if (progressListener != null) {
                progressListener.onProgressChanged(clampedRatio);
            }
        }
    }

    public void loadAudioFile(File file) {
        try {
            AudioSourceResolver.ResolvedAudio resolvedAudio = audioSourceResolver.resolveForProcessing(file, "waveform/preview");
            AudioInputStream ais = AudioSystem.getAudioInputStream(resolvedAudio.getWorkingFile());
            AudioFormat format = ais.getFormat();

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

            processAudioData(buffer.toByteArray(), format);
        } catch (Exception e) {
            e.printStackTrace();
            drawErrorMessage("无法加载音频文件: " + e.getMessage());
        }
    }

    public double[] getSelectionTimeRange(double totalDurationSeconds) {
        if (selectionStartRatio < 0 || selectionEndRatio < 0) {
            return new double[]{0, totalDurationSeconds};
        }
        return new double[]{
                selectionStartRatio * totalDurationSeconds,
                selectionEndRatio * totalDurationSeconds
        };
    }

    public void clearSelection() {
        selectionStartRatio = -1.0;
        selectionEndRatio = -1.0;
        redrawWaveform();
        notifySelectionChanged();
    }

    public void setSelectionRatio(double startRatio, double endRatio) {
        if (startRatio < 0 || endRatio < 0) {
            clearSelection();
            return;
        }

        double start = clampRatio(Math.min(startRatio, endRatio));
        double end = clampRatio(Math.max(startRatio, endRatio));
        selectionStartRatio = start;
        selectionEndRatio = end;
        redrawWaveform();
        notifySelectionChanged();
    }

    private void processAudioData(byte[] audioBytes, AudioFormat format) {
        int sampleSize = format.getSampleSizeInBits() / 8;
        int numSamples = audioBytes.length / sampleSize;

        if (numSamples <= 0) {
            drawErrorMessage("音频格式不受支持");
            return;
        }

        samples = new double[Math.min(numSamples, 10000)];
        int step = Math.max(1, numSamples / samples.length);

        for (int i = 0; i < samples.length && i * step < numSamples; i++) {
            int index = i * step;
            if (sampleSize == 2) {
                int lsb = audioBytes[index * 2] & 0xff;
                int msb = audioBytes[index * 2 + 1];
                samples[i] = (msb << 8 | lsb) / 32768.0;
            } else if (sampleSize == 1) {
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
        drawBackgroundGrid();

        double canvasWidth = getWidth();
        double height = getHeight();
        double center = height / 2;

        gc.setStroke(Color.web("#5dade2"));
        gc.setGlobalAlpha(0.95);
        gc.setLineWidth(1.3);
        drawWaveformSegment(gc, samples, 0, samples.length, center, canvasWidth);

        if (playedRatio > 0) {
            double playedX = playedRatio * canvasWidth;
            gc.setGlobalAlpha(0.08);
            gc.setFill(Color.web("#2e86de"));
            gc.fillRect(0, 0, playedX, height);

            gc.setGlobalAlpha(1.0);
            gc.setStroke(Color.web("#1b4f72"));
            gc.setLineWidth(1.5);
            gc.strokeLine(playedX, 0, playedX, height);
        }

        if (selectionStartRatio >= 0 && selectionEndRatio >= 0) {
            double startX = selectionStartRatio * canvasWidth;
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
        if (startIndex >= endIndex) {
            return;
        }

        double segmentWidth = canvasWidth * ((double) (endIndex - startIndex) / samples.length);
        double startX = canvasWidth * ((double) startIndex / samples.length);

        for (int i = startIndex; i < endIndex - 1 && i < samples.length - 1; i++) {
            int x1 = (int) (startX + (i - startIndex) * segmentWidth / (endIndex - startIndex - 1));
            int x2 = (int) (startX + (i + 1 - startIndex) * segmentWidth / (endIndex - startIndex - 1));

            if (x1 < canvasWidth && x2 < canvasWidth) {
                double amp1 = Math.max(-0.9, Math.min(0.9, samples[i]));
                double amp2 = Math.max(-0.9, Math.min(0.9, samples[i + 1]));
                gc.strokeLine(x1, center - amp1 * (center - 15), x2, center - amp2 * (center - 15));
            }
        }
    }

    private void drawBackgroundGrid() {
        GraphicsContext gc = getGraphicsContext2D();
        double canvasWidth = getWidth();
        double height = getHeight();
        double center = height / 2;

        gc.setStroke(Color.LIGHTGRAY);
        gc.setLineWidth(0.5);
        gc.strokeLine(0, center, canvasWidth, center);

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

    private void setupMouseHandlers() {
        setOnMousePressed(this::handleMousePressed);
        setOnMouseDragged(this::handleMouseDragged);
        setOnMouseReleased(this::handleMouseReleased);
    }

    private void handleMousePressed(MouseEvent event) {
        if (!hasSamples()) {
            return;
        }

        draggingSelection = true;
        dragStartRatio = clampRatio(event.getX() / getWidth());
        selectionStartRatio = dragStartRatio;
        selectionEndRatio = dragStartRatio;
        redrawWaveform();
        notifySelectionChanged();
    }

    private void handleMouseDragged(MouseEvent event) {
        if (!draggingSelection || !hasSamples()) {
            return;
        }

        double currentRatio = clampRatio(event.getX() / getWidth());
        selectionStartRatio = Math.min(dragStartRatio, currentRatio);
        selectionEndRatio = Math.max(dragStartRatio, currentRatio);
        redrawWaveform();
        notifySelectionChanged();
    }

    private void handleMouseReleased(MouseEvent event) {
        if (!draggingSelection || !hasSamples()) {
            return;
        }

        draggingSelection = false;
        double currentRatio = clampRatio(event.getX() / getWidth());
        selectionStartRatio = Math.min(dragStartRatio, currentRatio);
        selectionEndRatio = Math.max(dragStartRatio, currentRatio);

        if (Math.abs(selectionEndRatio - selectionStartRatio) < 0.001) {
            selectionStartRatio = -1.0;
            selectionEndRatio = -1.0;
        }

        redrawWaveform();
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selectionStartRatio, selectionEndRatio);
        }
    }

    private boolean hasSamples() {
        return samples != null && samples.length > 0;
    }

    private double clampRatio(double ratio) {
        return Math.max(0.0, Math.min(1.0, ratio));
    }
}
