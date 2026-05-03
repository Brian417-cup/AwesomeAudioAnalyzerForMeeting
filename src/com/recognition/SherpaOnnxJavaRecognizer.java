package com.recognition;

import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig;
import com.k2fsa.sherpa.onnx.SileroVadModelConfig;
import com.k2fsa.sherpa.onnx.SpeechSegment;
import com.k2fsa.sherpa.onnx.Vad;
import com.k2fsa.sherpa.onnx.VadModelConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SherpaOnnxJavaRecognizer {

    private static final double MIN_DECODE_DURATION_SECONDS = 0.30;
    private static final double MIN_DECODE_RMS_ENERGY = 0.012;
    private static final double MIN_DECODE_MEAN_ABS = 0.009;
    private static final double EDGE_TRIM_FRAME_SECONDS = 0.02;
    private static final double EDGE_TRIM_PADDING_SECONDS = 0.04;
    private static final double EDGE_TRIM_FRAME_RMS = 0.008;
    private static final double EDGE_TRIM_FRAME_MEAN_ABS = 0.006;

    @FunctionalInterface
    public interface ProgressSink {
        void onProgress(int progress);
    }

    @FunctionalInterface
    public interface SegmentSink {
        void onSegment(RecognitionSegment segment);
    }

    @FunctionalInterface
    public interface SegmentDetectedSink {
        void onSegmentDetected(RecognitionSegment segment);
    }

    private final AudioSampleLoader audioSampleLoader = new AudioSampleLoader();
    private final SpeakerDiarizationService speakerDiarizationService = new SpeakerDiarizationService();

    public List<RecognitionSegment> recognize(
            RecognitionRequest request,
            ProgressSink progressSink,
            SegmentSink segmentSink
    ) throws Exception {
        return recognize(request, progressSink, null, segmentSink);
    }

    public List<RecognitionSegment> recognize(
            RecognitionRequest request,
            ProgressSink progressSink,
            SegmentDetectedSink segmentDetectedSink,
            SegmentSink segmentSink
    ) throws Exception {
        SherpaOnnxConfig config = SherpaOnnxConfig.loadDefault();
        progressSink.onProgress(8);

        AudioSampleLoader.AudioData audioData = audioSampleLoader.loadAsMonoFloat(
                request.getAudioFile(),
                config.getSampleRate(),
                "recognition/input"
        );
        progressSink.onProgress(18);

        OfflineRecognizerConfig recognizerConfig = request.isUseHotWords()
                ? createConformerConfig(config, request)
                : createParaformerConfig(config);

        List<RecognitionSegment> segments = recognizeWithVad(
                recognizerConfig,
                request,
                config,
                audioData.getSamples(),
                audioData.getSampleRate(),
                progressSink,
                segmentDetectedSink,
                segmentSink
        );

        if (segments.isEmpty()) {
            String text = recognizeWholeAudio(recognizerConfig, audioData.getSamples(), audioData.getSampleRate());
            if (text != null && !text.trim().isEmpty()) {
                RecognitionSegment only = new RecognitionSegment(
                        0.0,
                        audioData.getSamples().length / (double) audioData.getSampleRate(),
                        text.trim(),
                        "说话人1"
                );
                segments.add(only);
                if (segmentSink != null) {
                    segmentSink.onSegment(only);
                }
            }
        }

        progressSink.onProgress(90);
        return segments;
    }

    private List<RecognitionSegment> recognizeWithVad(
            OfflineRecognizerConfig recognizerConfig,
            RecognitionRequest request,
            SherpaOnnxConfig config,
            float[] samples,
            int sampleRate,
            ProgressSink progressSink,
            SegmentDetectedSink segmentDetectedSink,
            SegmentSink segmentSink
    ) {
        Vad vad = null;
        List<VadChunk> vadChunks = new ArrayList<VadChunk>();
        List<RecognitionSegment> output = new ArrayList<RecognitionSegment>();

        try {
            vad = createVad(config);

            int window = Math.max(1, config.getVadWindowSize());
            int total = samples.length;
            int processed = 0;

            for (int start = 0; start < total; start += window) {
                int end = Math.min(total, start + window);
                float[] chunk = Arrays.copyOfRange(samples, start, end);
                vad.acceptWaveform(chunk);
                processed = end;

                collectVadChunks(vad, sampleRate, vadChunks);

                int p = 20 + (int) (35.0 * processed / Math.max(1, total));
                progressSink.onProgress(Math.min(55, p));
            }

            vad.flush();
            collectVadChunks(vad, sampleRate, vadChunks);
            progressSink.onProgress(vadChunks.isEmpty() ? 60 : 55);
        } finally {
            if (vad != null) {
                vad.release();
            }
        }

        if (vadChunks.isEmpty()) {
            return output;
        }

        List<SpeakerDiarizationService.TimedChunk> timedChunks = new ArrayList<SpeakerDiarizationService.TimedChunk>();
        for (VadChunk chunk : vadChunks) {
            timedChunks.add(new SpeakerDiarizationService.TimedChunk(
                    chunk.getStartSec(),
                    chunk.getEndSec(),
                    chunk.getSamples()
            ));
        }

        List<SpeakerDiarizationService.TimedChunk> refinedChunks = speakerDiarizationService.refineChunks(
                config,
                samples,
                sampleRate,
                timedChunks,
                request.isUseVoicePrint(),
                request.isUseFixedSpeakerCount(),
                request.getSpeakerCount()
        );
        if (refinedChunks == null || refinedChunks.isEmpty()) {
            refinedChunks = buildFallbackTimedChunks(
                    timedChunks,
                    request.isUseFixedSpeakerCount() ? request.getSpeakerCount() : 1
            );
        }

        OfflineRecognizer recognizer = null;
        try {
            recognizer = new OfflineRecognizer(recognizerConfig);
            for (int i = 0; i < refinedChunks.size(); i++) {
                SpeakerDiarizationService.TimedChunk originalChunk = refinedChunks.get(i);
                SpeakerDiarizationService.TimedChunk decodeChunk = normalizeChunkForDecode(originalChunk, sampleRate);
                if (!shouldDecodeChunk(decodeChunk)) {
                    int p = 55 + (int) (30.0 * (i + 1) / Math.max(1, refinedChunks.size()));
                    progressSink.onProgress(Math.min(85, p));
                    continue;
                }

                String speakerLabel = originalChunk.getSpeakerLabel();
                if (segmentDetectedSink != null) {
                    segmentDetectedSink.onSegmentDetected(new RecognitionSegment(
                            originalChunk.getStartTime(),
                            originalChunk.getEndTime(),
                            "",
                            speakerLabel
                    ));
                }

                OfflineStream stream = recognizer.createStream();
                try {
                    stream.acceptWaveform(decodeChunk.getSamples(), sampleRate);
                    recognizer.decode(stream);
                    String text = recognizer.getResult(stream).getText();
                    String trimmed = text == null ? "" : text.trim();
                    RecognitionSegment rs = new RecognitionSegment(
                            originalChunk.getStartTime(),
                            originalChunk.getEndTime(),
                            trimmed,
                            speakerLabel
                    );
                    output.add(rs);
                    if (segmentSink != null) {
                        segmentSink.onSegment(rs);
                    }
                } catch (Exception ignored) {
                    RecognitionSegment rs = new RecognitionSegment(
                            originalChunk.getStartTime(),
                            originalChunk.getEndTime(),
                            "",
                            speakerLabel
                    );
                    output.add(rs);
                    if (segmentSink != null) {
                        segmentSink.onSegment(rs);
                    }
                } finally {
                    stream.release();
                }

                int p = 55 + (int) (30.0 * (i + 1) / Math.max(1, refinedChunks.size()));
                progressSink.onProgress(Math.min(85, p));
            }
            return output;
        } finally {
            if (recognizer != null) {
                recognizer.release();
            }
        }
    }

    private SpeakerDiarizationService.TimedChunk normalizeChunkForDecode(
            SpeakerDiarizationService.TimedChunk chunk,
            int sampleRate
    ) {
        int[] activeRange = findActiveRange(chunk.getSamples(), sampleRate);
        if (activeRange[1] <= activeRange[0]) {
            return new SpeakerDiarizationService.TimedChunk(
                    chunk.getStartTime(),
                    chunk.getEndTime(),
                    new float[0],
                    chunk.getSpeakerId(),
                    chunk.getSpeakerLabel()
            );
        }

        float[] trimmedSamples = Arrays.copyOfRange(chunk.getSamples(), activeRange[0], activeRange[1]);
        double newStart = chunk.getStartTime() + activeRange[0] / (double) sampleRate;
        double newEnd = chunk.getStartTime() + activeRange[1] / (double) sampleRate;
        return new SpeakerDiarizationService.TimedChunk(
                newStart,
                newEnd,
                trimmedSamples,
                chunk.getSpeakerId(),
                chunk.getSpeakerLabel()
        );
    }

    private int[] findActiveRange(float[] samples, int sampleRate) {
        if (samples == null || samples.length == 0) {
            return new int[]{0, 0};
        }

        int frameSize = Math.max(1, (int) Math.round(sampleRate * EDGE_TRIM_FRAME_SECONDS));
        int frameCount = (samples.length + frameSize - 1) / frameSize;
        int firstActiveFrame = -1;
        int lastActiveFrame = -1;

        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            int start = frameIndex * frameSize;
            int end = Math.min(samples.length, start + frameSize);
            if (isActiveFrame(samples, start, end)) {
                if (firstActiveFrame < 0) {
                    firstActiveFrame = frameIndex;
                }
                lastActiveFrame = frameIndex;
            }
        }

        if (firstActiveFrame < 0 || lastActiveFrame < 0) {
            return new int[]{0, 0};
        }

        int padding = Math.max(1, (int) Math.round(sampleRate * EDGE_TRIM_PADDING_SECONDS));
        int start = Math.max(0, firstActiveFrame * frameSize - padding);
        int end = Math.min(samples.length, (lastActiveFrame + 1) * frameSize + padding);
        return new int[]{start, end};
    }

    private boolean isActiveFrame(float[] samples, int start, int end) {
        if (end <= start) {
            return false;
        }

        double squareSum = 0.0;
        double absSum = 0.0;
        for (int i = start; i < end; i++) {
            float sample = samples[i];
            squareSum += sample * sample;
            absSum += Math.abs(sample);
        }

        int count = end - start;
        double rms = Math.sqrt(squareSum / count);
        double meanAbs = absSum / count;
        return rms >= EDGE_TRIM_FRAME_RMS || meanAbs >= EDGE_TRIM_FRAME_MEAN_ABS;
    }

    private boolean shouldDecodeChunk(SpeakerDiarizationService.TimedChunk chunk) {
        float[] samples = chunk.getSamples();
        double duration = chunk.getDuration();
        if (samples == null || samples.length == 0 || duration < MIN_DECODE_DURATION_SECONDS) {
            return false;
        }

        double squareSum = 0.0;
        double absSum = 0.0;
        for (float sample : samples) {
            squareSum += sample * sample;
            absSum += Math.abs(sample);
        }

        double rms = Math.sqrt(squareSum / samples.length);
        double meanAbs = absSum / samples.length;
        return rms >= MIN_DECODE_RMS_ENERGY || meanAbs >= MIN_DECODE_MEAN_ABS;
    }

    private List<SpeakerDiarizationService.TimedChunk> buildFallbackTimedChunks(
            List<SpeakerDiarizationService.TimedChunk> source,
            int speakerCount
    ) {
        List<SpeakerDiarizationService.TimedChunk> fallback = new ArrayList<SpeakerDiarizationService.TimedChunk>();
        for (int i = 0; i < source.size(); i++) {
            SpeakerDiarizationService.TimedChunk chunk = source.get(i);
            String speaker = speakerCount <= 1 ? "说话人1" : "说话人" + ((i % speakerCount) + 1);
            fallback.add(new SpeakerDiarizationService.TimedChunk(
                    chunk.getStartTime(),
                    chunk.getEndTime(),
                    chunk.getSamples(),
                    i,
                    speaker
            ));
        }
        return fallback;
    }

    private void collectVadChunks(
            Vad vad,
            int sampleRate,
            List<VadChunk> output
    ) {
        while (!vad.empty()) {
            SpeechSegment segment = vad.front();
            double startSec = segment.getStart() / (double) sampleRate;
            float[] segSamples = segment.getSamples();
            double endSec = startSec + (segSamples.length / (double) sampleRate);
            output.add(new VadChunk(startSec, endSec, Arrays.copyOf(segSamples, segSamples.length)));
            vad.pop();
        }
    }

    private static class VadChunk {
        private final double startSec;
        private final double endSec;
        private final float[] samples;

        private VadChunk(double startSec, double endSec, float[] samples) {
            this.startSec = startSec;
            this.endSec = endSec;
            this.samples = samples;
        }

        public double getStartSec() {
            return startSec;
        }

        public double getEndSec() {
            return endSec;
        }

        public float[] getSamples() {
            return samples;
        }
    }

    private String recognizeWholeAudio(
            OfflineRecognizerConfig recognizerConfig,
            float[] samples,
            int sampleRate
    ) {
        OfflineRecognizer recognizer = null;
        OfflineStream stream = null;
        try {
            recognizer = new OfflineRecognizer(recognizerConfig);
            stream = recognizer.createStream();
            stream.acceptWaveform(samples, sampleRate);
            recognizer.decode(stream);
            return recognizer.getResult(stream).getText();
        } catch (Exception e) {
            return "";
        } finally {
            if (stream != null) {
                stream.release();
            }
            if (recognizer != null) {
                recognizer.release();
            }
        }
    }

    private Vad createVad(SherpaOnnxConfig config) {
        SileroVadModelConfig sileroConfig = SileroVadModelConfig.builder()
                .setModel(config.getSileroVadModelFile().getAbsolutePath())
                .setThreshold(config.getVadThreshold())
                .setMinSilenceDuration(config.getVadMinSilenceDuration())
                .setMinSpeechDuration(config.getVadMinSpeechDuration())
                .setWindowSize(config.getVadWindowSize())
                .setMaxSpeechDuration(config.getVadMaxSpeechDuration())
                .build();

        VadModelConfig vadModelConfig = VadModelConfig.builder()
                .setSileroVadModelConfig(sileroConfig)
                .setSampleRate(config.getSampleRate())
                .setNumThreads(config.getNumThreads())
                .setDebug(config.isDebug())
                .setProvider(config.getProvider())
                .build();
        return new Vad(vadModelConfig);
    }

    private OfflineRecognizerConfig createParaformerConfig(SherpaOnnxConfig config) {
        config.validateForParaformer();

        OfflineParaformerModelConfig paraformer =
                OfflineParaformerModelConfig.builder()
                        .setModel(config.getParaformerModelFile().getAbsolutePath())
                        .build();

        OfflineModelConfig modelConfig =
                OfflineModelConfig.builder()
                        .setParaformer(paraformer)
                        .setTokens(config.getParaformerTokensFile().getAbsolutePath())
                        .setNumThreads(config.getNumThreads())
                        .setDebug(config.isDebug())
                        .setProvider(config.getProvider())
                        .build();

        return OfflineRecognizerConfig.builder()
                .setOfflineModelConfig(modelConfig)
                .setDecodingMethod(config.getParaformerDecodingMethod())
                .build();
    }

    private OfflineRecognizerConfig createConformerConfig(SherpaOnnxConfig config, RecognitionRequest request) {
        config.validateForConformer();

        File hotwordsFile = request.getHotWordsFile();
        if (hotwordsFile == null || !hotwordsFile.exists()) {
            throw new IllegalArgumentException("已启用热词，但热词文件不存在。");
        }

        OfflineTransducerModelConfig transducer =
                OfflineTransducerModelConfig.builder()
                        .setEncoder(config.getConformerEncoderFile().getAbsolutePath())
                        .setDecoder(config.getConformerDecoderFile().getAbsolutePath())
                        .setJoiner(config.getConformerJoinerFile().getAbsolutePath())
                        .build();

        OfflineModelConfig modelConfig =
                OfflineModelConfig.builder()
                        .setTransducer(transducer)
                        .setTokens(config.getConformerTokensFile().getAbsolutePath())
                        .setNumThreads(config.getNumThreads())
                        .setDebug(config.isDebug())
                        .setProvider(config.getProvider())
                        .setModelingUnit(config.getConformerModelingUnit())
                        .build();

        return OfflineRecognizerConfig.builder()
                .setOfflineModelConfig(modelConfig)
                .setDecodingMethod(config.getConformerDecodingMethod())
                .setMaxActivePaths(config.getConformerMaxActivePaths())
                .setHotwordsFile(hotwordsFile.getAbsolutePath())
                .setHotwordsScore(config.getConformerHotwordsScore())
                .build();
    }
}
