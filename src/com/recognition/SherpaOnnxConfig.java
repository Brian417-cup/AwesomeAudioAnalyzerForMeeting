package com.recognition;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class SherpaOnnxConfig {

    public static final String DEFAULT_CONFIG_FILE = "sherpa_onnx.properties";

    private final int numThreads;
    private final int sampleRate;
    private final boolean debug;
    private final String provider;

    private final File paraformerModelFile;
    private final File paraformerTokensFile;
    private final String paraformerDecodingMethod;

    private final File conformerEncoderFile;
    private final File conformerDecoderFile;
    private final File conformerJoinerFile;
    private final File conformerTokensFile;
    private final String conformerModelingUnit;
    private final String conformerDecodingMethod;
    private final int conformerMaxActivePaths;
    private final float conformerHotwordsScore;

    private final File sileroVadModelFile;
    private final int vadWindowSize;
    private final float vadThreshold;
    private final float vadMinSilenceDuration;
    private final float vadMinSpeechDuration;
    private final float vadMaxSpeechDuration;

    private final File speakerSegmentationModelFile;
    private final File speakerEmbeddingModelFile;
    private final float speakerClusteringThreshold;
    private final float speakerMinDurationOn;
    private final float speakerMinDurationOff;
    private final float speakerMatchThreshold;

    private SherpaOnnxConfig(
            int numThreads,
            int sampleRate,
            boolean debug,
            String provider,
            File paraformerModelFile,
            File paraformerTokensFile,
            String paraformerDecodingMethod,
            File conformerEncoderFile,
            File conformerDecoderFile,
            File conformerJoinerFile,
            File conformerTokensFile,
            String conformerModelingUnit,
            String conformerDecodingMethod,
            int conformerMaxActivePaths,
            float conformerHotwordsScore,
            File sileroVadModelFile,
            int vadWindowSize,
            float vadThreshold,
            float vadMinSilenceDuration,
            float vadMinSpeechDuration,
            float vadMaxSpeechDuration,
            File speakerSegmentationModelFile,
            File speakerEmbeddingModelFile,
            float speakerClusteringThreshold,
            float speakerMinDurationOn,
            float speakerMinDurationOff,
            float speakerMatchThreshold
    ) {
        this.numThreads = numThreads;
        this.sampleRate = sampleRate;
        this.debug = debug;
        this.provider = provider;
        this.paraformerModelFile = paraformerModelFile;
        this.paraformerTokensFile = paraformerTokensFile;
        this.paraformerDecodingMethod = paraformerDecodingMethod;
        this.conformerEncoderFile = conformerEncoderFile;
        this.conformerDecoderFile = conformerDecoderFile;
        this.conformerJoinerFile = conformerJoinerFile;
        this.conformerTokensFile = conformerTokensFile;
        this.conformerModelingUnit = conformerModelingUnit;
        this.conformerDecodingMethod = conformerDecodingMethod;
        this.conformerMaxActivePaths = conformerMaxActivePaths;
        this.conformerHotwordsScore = conformerHotwordsScore;
        this.sileroVadModelFile = sileroVadModelFile;
        this.vadWindowSize = vadWindowSize;
        this.vadThreshold = vadThreshold;
        this.vadMinSilenceDuration = vadMinSilenceDuration;
        this.vadMinSpeechDuration = vadMinSpeechDuration;
        this.vadMaxSpeechDuration = vadMaxSpeechDuration;
        this.speakerSegmentationModelFile = speakerSegmentationModelFile;
        this.speakerEmbeddingModelFile = speakerEmbeddingModelFile;
        this.speakerClusteringThreshold = speakerClusteringThreshold;
        this.speakerMinDurationOn = speakerMinDurationOn;
        this.speakerMinDurationOff = speakerMinDurationOff;
        this.speakerMatchThreshold = speakerMatchThreshold;
    }

    public static SherpaOnnxConfig loadDefault() throws Exception {
        File configFile = new File(DEFAULT_CONFIG_FILE).getAbsoluteFile();
        if (!configFile.exists()) {
            throw new IllegalStateException(
                    "找不到配置文件: " + configFile.getAbsolutePath()
                            + "。请先复制 sherpa_onnx.properties.example 为 sherpa_onnx.properties，并确认项目 models 目录完整。"
            );
        }
        return loadFromFile(configFile);
    }

    public static SherpaOnnxConfig loadFromFile(File configFile) throws Exception {
        Properties p = new Properties();
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(configFile), StandardCharsets.UTF_8
        )) {
            p.load(reader);
        }

        File baseDir = configFile.getParentFile() == null
                ? new File(".").getAbsoluteFile()
                : configFile.getParentFile().getAbsoluteFile();

        int numThreads = parseInt(getOrDefault(p, "common.numThreads", "2"), 2);
        int sampleRate = parseInt(getOrDefault(p, "common.sampleRate", "16000"), 16000);
        boolean debug = parseBoolean(getOrDefault(p, "common.debug", "false"));
        String provider = getOrDefault(p, "common.provider", "cpu");

        File paraformerModel = resolvePath(baseDir, p.getProperty("paraformer.model"));
        File paraformerTokens = resolvePath(baseDir, p.getProperty("paraformer.tokens"));
        String paraformerDecodingMethod = getOrDefault(p, "paraformer.decodingMethod", "greedy_search");

        File conformerEncoder = resolvePath(baseDir, p.getProperty("conformer.encoder"));
        File conformerDecoder = resolvePath(baseDir, p.getProperty("conformer.decoder"));
        File conformerJoiner = resolvePath(baseDir, p.getProperty("conformer.joiner"));
        File conformerTokens = resolvePath(baseDir, p.getProperty("conformer.tokens"));
        String conformerModelingUnit = getOrDefault(p, "conformer.modelingUnit", "cjkchar");
        String conformerDecodingMethod = getOrDefault(p, "conformer.decodingMethod", "modified_beam_search");
        int conformerMaxActivePaths = parseInt(getOrDefault(p, "conformer.maxActivePaths", "20"), 20);
        float conformerHotwordsScore = parseFloat(getOrDefault(p, "conformer.hotwordsScore", "30.0"), 30.0f);

        File sileroVadModel = resolvePath(baseDir, p.getProperty("vad.sileroModel"));
        int vadWindowSize = parseInt(getOrDefault(p, "vad.windowSize", "512"), 512);
        float vadThreshold = parseFloat(getOrDefault(p, "vad.threshold", "0.55"), 0.55f);
        float vadMinSilenceDuration = parseFloat(getOrDefault(p, "vad.minSilenceDuration", "0.35"), 0.35f);
        float vadMinSpeechDuration = parseFloat(getOrDefault(p, "vad.minSpeechDuration", "0.6"), 0.6f);
        float vadMaxSpeechDuration = parseFloat(getOrDefault(p, "vad.maxSpeechDuration", "5.0"), 5.0f);

        File speakerSegmentationModel = resolvePath(baseDir, p.getProperty("speaker.segmentationModel"));
        File speakerEmbeddingModel = resolvePath(baseDir, p.getProperty("speaker.embeddingModel"));
        float speakerClusteringThreshold = parseFloat(getOrDefault(p, "speaker.clusteringThreshold", "0.5"), 0.5f);
        float speakerMinDurationOn = parseFloat(getOrDefault(p, "speaker.minDurationOn", "0.3"), 0.3f);
        float speakerMinDurationOff = parseFloat(getOrDefault(p, "speaker.minDurationOff", "0.6"), 0.6f);
        float speakerMatchThreshold = parseFloat(getOrDefault(p, "speaker.matchThreshold", "0.82"), 0.82f);

        return new SherpaOnnxConfig(
                numThreads,
                sampleRate,
                debug,
                provider,
                paraformerModel,
                paraformerTokens,
                paraformerDecodingMethod,
                conformerEncoder,
                conformerDecoder,
                conformerJoiner,
                conformerTokens,
                conformerModelingUnit,
                conformerDecodingMethod,
                conformerMaxActivePaths,
                conformerHotwordsScore,
                sileroVadModel,
                vadWindowSize,
                vadThreshold,
                vadMinSilenceDuration,
                vadMinSpeechDuration,
                vadMaxSpeechDuration,
                speakerSegmentationModel,
                speakerEmbeddingModel,
                speakerClusteringThreshold,
                speakerMinDurationOn,
                speakerMinDurationOff,
                speakerMatchThreshold
        );
    }

    public void validateForParaformer() {
        ensureFileExists("paraformer.model", paraformerModelFile);
        ensureFileExists("paraformer.tokens", paraformerTokensFile);
        ensureFileExists("vad.sileroModel", sileroVadModelFile);
    }

    public void validateForConformer() {
        ensureFileExists("conformer.encoder", conformerEncoderFile);
        ensureFileExists("conformer.decoder", conformerDecoderFile);
        ensureFileExists("conformer.joiner", conformerJoinerFile);
        ensureFileExists("conformer.tokens", conformerTokensFile);
        ensureFileExists("vad.sileroModel", sileroVadModelFile);
    }

    public void validateForSpeakerDiarization() {
        ensureFileExists("speaker.segmentationModel", speakerSegmentationModelFile);
        ensureFileExists("speaker.embeddingModel", speakerEmbeddingModelFile);
    }

    public int getNumThreads() {
        return numThreads;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public boolean isDebug() {
        return debug;
    }

    public String getProvider() {
        return provider;
    }

    public File getParaformerModelFile() {
        return paraformerModelFile;
    }

    public File getParaformerTokensFile() {
        return paraformerTokensFile;
    }

    public String getParaformerDecodingMethod() {
        return paraformerDecodingMethod;
    }

    public File getConformerEncoderFile() {
        return conformerEncoderFile;
    }

    public File getConformerDecoderFile() {
        return conformerDecoderFile;
    }

    public File getConformerJoinerFile() {
        return conformerJoinerFile;
    }

    public File getConformerTokensFile() {
        return conformerTokensFile;
    }

    public String getConformerModelingUnit() {
        return conformerModelingUnit;
    }

    public String getConformerDecodingMethod() {
        return conformerDecodingMethod;
    }

    public int getConformerMaxActivePaths() {
        return conformerMaxActivePaths;
    }

    public float getConformerHotwordsScore() {
        return conformerHotwordsScore;
    }

    public File getSileroVadModelFile() {
        return sileroVadModelFile;
    }

    public int getVadWindowSize() {
        return vadWindowSize;
    }

    public float getVadThreshold() {
        return vadThreshold;
    }

    public float getVadMinSilenceDuration() {
        return vadMinSilenceDuration;
    }

    public float getVadMinSpeechDuration() {
        return vadMinSpeechDuration;
    }

    public float getVadMaxSpeechDuration() {
        return vadMaxSpeechDuration;
    }

    public File getSpeakerSegmentationModelFile() {
        return speakerSegmentationModelFile;
    }

    public File getSpeakerEmbeddingModelFile() {
        return speakerEmbeddingModelFile;
    }

    public float getSpeakerClusteringThreshold() {
        return speakerClusteringThreshold;
    }

    public float getSpeakerMinDurationOn() {
        return speakerMinDurationOn;
    }

    public float getSpeakerMinDurationOff() {
        return speakerMinDurationOff;
    }

    public float getSpeakerMatchThreshold() {
        return speakerMatchThreshold;
    }

    private static File resolvePath(File baseDir, String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return null;
        }
        File path = new File(rawPath.trim());
        return path.isAbsolute() ? path : new File(baseDir, rawPath.trim()).getAbsoluteFile();
    }

    private static void ensureFileExists(String key, File file) {
        if (file == null) {
            throw new IllegalStateException("配置项缺失: " + key);
        }
        if (!file.exists()) {
            throw new IllegalStateException("配置路径无效: " + key + " -> " + file.getAbsolutePath());
        }
    }

    private static String getOrDefault(Properties p, String key, String defaultValue) {
        String v = p.getProperty(key);
        return (v == null || v.trim().isEmpty()) ? defaultValue : v.trim();
    }

    private static int parseInt(String raw, int defaultVal) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private static float parseFloat(String raw, float defaultVal) {
        try {
            return Float.parseFloat(raw);
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private static boolean parseBoolean(String raw) {
        return "1".equals(raw) || "true".equalsIgnoreCase(raw) || "yes".equalsIgnoreCase(raw);
    }
}
