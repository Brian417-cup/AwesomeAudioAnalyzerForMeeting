package com.recognition;

import javafx.stage.FileChooser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class SherpaOnnxConfigStore {

    private static final String SECTION_GENERAL = "基础运行设置";
    private static final String SECTION_AUDIO = "音频导入设置";
    private static final String SECTION_PARAFORMER = "普通识别设置";
    private static final String SECTION_CONFORMER = "关键词增强识别";
    private static final String SECTION_VAD = "静音分段设置";
    private static final String SECTION_SPEAKER = "多人发言区分与库匹配";

    private static final List<String> SECTION_ORDER = Arrays.asList(
            SECTION_GENERAL,
            SECTION_AUDIO,
            SECTION_PARAFORMER,
            SECTION_CONFORMER,
            SECTION_VAD,
            SECTION_SPEAKER
    );

    private static final List<ConfigFieldDefinition> FIELD_DEFINITIONS = Arrays.asList(
            ConfigFieldDefinition.text("common.numThreads", SECTION_GENERAL, "处理速度线程数", "控制识别时使用多少个处理线程。一般保持 2 到 4 即可。"),
            ConfigFieldDefinition.text("common.sampleRate", SECTION_GENERAL, "标准采样率", "系统内部统一处理音频时使用的采样率，通常保持 16000。"),
            ConfigFieldDefinition.select("common.debug", SECTION_GENERAL, "显示调试日志", "开启后会打印更多底层日志，通常只有排查问题时才需要打开。", "false", "true"),
            ConfigFieldDefinition.select("common.provider", SECTION_GENERAL, "推理设备", "当前建议保持 cpu。", "cpu"),

            ConfigFieldDefinition.select("audio.autoConvertWithFfmpeg", SECTION_AUDIO, "自动转标准音频", "非 WAV 音频导入时，是否自动转成系统最适合处理的标准 WAV。", "true", "false"),
            ConfigFieldDefinition.file("audio.ffmpegPath", SECTION_AUDIO, "音频转换工具路径", "用于把 mp3、m4a、aac 等格式自动转成标准 WAV。", "*.exe", "*.bat", "*.*"),
            ConfigFieldDefinition.text("audio.inputFormats", SECTION_AUDIO, "允许导入的音频格式", "用英文逗号分隔扩展名，例如 wav,mp3,m4a。"),
            ConfigFieldDefinition.directory("audio.tempDirectory", SECTION_AUDIO, "临时音频目录", "非 WAV 转换、波形预览、声纹匹配、现场剪切等临时文件都会放在这里。建议放在项目根目录下，便于统一查看和清理。"),

            ConfigFieldDefinition.file("paraformer.model", SECTION_PARAFORMER, "普通识别模型", "常规转写时使用的主模型文件。", "*.onnx", "*.*"),
            ConfigFieldDefinition.file("paraformer.tokens", SECTION_PARAFORMER, "普通识别词表", "普通识别模型对应的词表文件。", "*.txt", "*.*"),
            ConfigFieldDefinition.select("paraformer.decodingMethod", SECTION_PARAFORMER, "普通识别解码方式", "大多数场景保持 greedy_search 即可。", "greedy_search"),

            ConfigFieldDefinition.file("conformer.encoder", SECTION_CONFORMER, "关键词增强 Encoder", "开启关键词增强后使用的编码模型。", "*.onnx", "*.*"),
            ConfigFieldDefinition.file("conformer.decoder", SECTION_CONFORMER, "关键词增强 Decoder", "开启关键词增强后使用的解码模型。", "*.onnx", "*.*"),
            ConfigFieldDefinition.file("conformer.joiner", SECTION_CONFORMER, "关键词增强 Joiner", "关键词增强识别使用的拼接模型。", "*.onnx", "*.*"),
            ConfigFieldDefinition.file("conformer.tokens", SECTION_CONFORMER, "关键词增强词表", "关键词增强模型对应的词表文件。", "*.txt", "*.*"),
            ConfigFieldDefinition.select("conformer.modelingUnit", SECTION_CONFORMER, "建模单位", "中文模型通常保持 cjkchar。", "cjkchar", "bpe", "char"),
            ConfigFieldDefinition.select("conformer.decodingMethod", SECTION_CONFORMER, "关键词增强解码方式", "通常保持 modified_beam_search。", "modified_beam_search", "greedy_search"),
            ConfigFieldDefinition.text("conformer.maxActivePaths", SECTION_CONFORMER, "搜索深度", "数值越大越仔细，但处理会更慢。"),
            ConfigFieldDefinition.text("conformer.hotwordsScore", SECTION_CONFORMER, "关键词强化力度", "数值越高，系统越倾向于识别你提供的关键词。"),

            ConfigFieldDefinition.file("vad.sileroModel", SECTION_VAD, "静音分段模型", "用于判断哪里是讲话、哪里是停顿。", "*.onnx", "*.*"),
            ConfigFieldDefinition.text("vad.windowSize", SECTION_VAD, "分段观察窗口", "系统每次分析多长的一小段音频。"),
            ConfigFieldDefinition.text("vad.threshold", SECTION_VAD, "讲话判断灵敏度", "越高越保守，越低越容易把背景声当成讲话。"),
            ConfigFieldDefinition.text("vad.minSilenceDuration", SECTION_VAD, "最短停顿时长", "停顿达到这个时长，系统才会把前后内容分成两段。"),
            ConfigFieldDefinition.text("vad.minSpeechDuration", SECTION_VAD, "最短讲话时长", "太短的弱语音会被忽略，避免杂音进入识别。"),
            ConfigFieldDefinition.text("vad.maxSpeechDuration", SECTION_VAD, "单段最长讲话时长", "超过这个时长会强制切成多段，避免单段过长。"),

            ConfigFieldDefinition.file("speaker.segmentationModel", SECTION_SPEAKER, "多人区分模型", "用于判断不同发言人的切换边界。", "*.onnx", "*.*"),
            ConfigFieldDefinition.file("speaker.embeddingModel", SECTION_SPEAKER, "发言人特征模型", "用于提取发言人特征并做聚类或库匹配。", "*.onnx", "*.*"),
            ConfigFieldDefinition.text("speaker.clusteringThreshold", SECTION_SPEAKER, "多人区分敏感度", "越低越容易分成不同人，越高越容易合并成同一个人。"),
            ConfigFieldDefinition.text("speaker.minDurationOn", SECTION_SPEAKER, "最短有效讲话时长", "太短的讲话片段会被过滤掉。"),
            ConfigFieldDefinition.text("speaker.minDurationOff", SECTION_SPEAKER, "最短分隔停顿时长", "用于控制发言人切换时对停顿的敏感程度。"),
            ConfigFieldDefinition.text("speaker.matchThreshold", SECTION_SPEAKER, "智能替换/声纹库匹配门槛", "越高越严格，越低越容易把当前片段匹配到已注册发言人。")
    );

    private final File configFile;
    private final Properties properties = new Properties();

    private SherpaOnnxConfigStore(File configFile) {
        this.configFile = configFile;
    }

    public static SherpaOnnxConfigStore loadDefaultStore() throws Exception {
        File configFile = new File(SherpaOnnxConfig.DEFAULT_CONFIG_FILE).getAbsoluteFile();
        if (!configFile.exists()) {
            throw new IllegalStateException(
                    "找不到配置文件: " + configFile.getAbsolutePath()
                            + "。请先复制 sherpa_onnx.properties.example 为 sherpa_onnx.properties。"
            );
        }

        SherpaOnnxConfigStore store = new SherpaOnnxConfigStore(configFile);
        store.load();
        return store;
    }

    public void load() throws Exception {
        properties.clear();
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(configFile),
                StandardCharsets.UTF_8
        )) {
            properties.load(reader);
        }

        boolean changed = applyDefaultsIfMissing();
        if (changed) {
            save();
        }
    }

    public void save() throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(configFile),
                StandardCharsets.UTF_8
        ))) {
            writer.write("# sherpa_onnx 纯 Java 调用配置");
            writer.newLine();
            writer.write("# 也可以通过系统菜单中的“识别配置”界面修改这些参数");
            writer.newLine();

            for (String section : SECTION_ORDER) {
                writer.newLine();
                writer.write("# -------- " + section + " --------");
                writer.newLine();

                for (ConfigFieldDefinition definition : FIELD_DEFINITIONS) {
                    if (!section.equals(definition.getSection())) {
                        continue;
                    }
                    String value = normalizeValueForSave(definition, properties.getProperty(definition.getKey(), ""));
                    writer.write(definition.getKey() + "=" + value);
                    writer.newLine();
                }
            }
        }
    }

    public File getConfigFile() {
        return configFile;
    }

    public String getValue(String key) {
        return properties.getProperty(key, "").trim();
    }

    public void setValue(String key, String value) {
        properties.setProperty(key, value == null ? "" : value.trim());
    }

    public String getDefaultValue(String key) {
        return defaultValueFor(key);
    }

    public List<ConfigFieldDefinition> getFieldDefinitions() {
        return FIELD_DEFINITIONS;
    }

    public List<String> getSupportedAudioExtensions() {
        String raw = getValue("audio.inputFormats");
        Set<String> values = new LinkedHashSet<String>();
        if (raw != null && !raw.trim().isEmpty()) {
            String[] parts = raw.split(",");
            for (String part : parts) {
                String normalized = normalizeExtension(part);
                if (!normalized.isEmpty()) {
                    values.add(normalized);
                }
            }
        }

        if (values.isEmpty()) {
            values.addAll(Arrays.asList("wav", "mp3", "m4a", "aac"));
        }
        return new ArrayList<String>(values);
    }

    public List<String> getSupportedAudioPatterns() {
        List<String> patterns = new ArrayList<String>();
        for (String extension : getSupportedAudioExtensions()) {
            patterns.add("*." + extension);
        }
        return patterns;
    }

    public FileChooser.ExtensionFilter buildAudioExtensionFilter() {
        return new FileChooser.ExtensionFilter("音频文件", getSupportedAudioPatterns());
    }

    public boolean isAutoConvertWithFfmpeg() {
        String raw = getValue("audio.autoConvertWithFfmpeg");
        return "1".equals(raw) || "true".equalsIgnoreCase(raw) || "yes".equalsIgnoreCase(raw);
    }

    public File getFfmpegFile() {
        String rawPath = getValue("audio.ffmpegPath");
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return null;
        }
        return resolvePath(rawPath);
    }

    public File getAudioTempDirectory() {
        String rawPath = getValue("audio.tempDirectory");
        if (rawPath == null || rawPath.trim().isEmpty()) {
            rawPath = defaultValueFor("audio.tempDirectory");
        }
        File dir = resolvePath(rawPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public File getAudioTempSubDirectory(String relativePath) {
        File baseDir = getAudioTempDirectory();
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return baseDir;
        }
        String normalized = relativePath.replace("\\", "/").trim();
        File dir = new File(baseDir, normalized.replace("/", File.separator));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public boolean isSupportedAudioFile(File file) {
        if (file == null) {
            return false;
        }
        String extension = normalizeExtension(getFileExtension(file));
        return getSupportedAudioExtensions().contains(extension);
    }

    private File resolvePath(String rawPath) {
        File path = new File(rawPath);
        if (path.isAbsolute()) {
            return path;
        }
        File baseDir = configFile.getParentFile() == null
                ? new File(".").getAbsoluteFile()
                : configFile.getParentFile().getAbsoluteFile();
        return new File(baseDir, rawPath).getAbsoluteFile();
    }

    private boolean applyDefaultsIfMissing() {
        boolean changed = false;
        for (ConfigFieldDefinition definition : FIELD_DEFINITIONS) {
            if (!properties.containsKey(definition.getKey())) {
                properties.setProperty(definition.getKey(), defaultValueFor(definition.getKey()));
                changed = true;
            }
        }
        return changed;
    }

    private String defaultValueFor(String key) {
        if ("common.numThreads".equals(key)) {
            return "2";
        }
        if ("common.sampleRate".equals(key)) {
            return "16000";
        }
        if ("common.debug".equals(key)) {
            return "false";
        }
        if ("common.provider".equals(key)) {
            return "cpu";
        }
        if ("audio.autoConvertWithFfmpeg".equals(key)) {
            return "true";
        }
        if ("audio.ffmpegPath".equals(key)) {
            return "src/com/resource/ffmpeg.exe";
        }
        if ("audio.inputFormats".equals(key)) {
            return "wav,mp3,m4a,aac,flac,ogg,wma,amr";
        }
        if ("audio.tempDirectory".equals(key)) {
            return "temp_audio_workspace";
        }
        if ("paraformer.model".equals(key)) {
            return "models/paraformer-zh/model.int8.onnx";
        }
        if ("paraformer.tokens".equals(key)) {
            return "models/paraformer-zh/tokens.txt";
        }
        if ("paraformer.decodingMethod".equals(key)) {
            return "greedy_search";
        }
        if ("conformer.encoder".equals(key)) {
            return "models/conformer-zh-stateless2/encoder-epoch-99-avg-1.onnx";
        }
        if ("conformer.decoder".equals(key)) {
            return "models/conformer-zh-stateless2/decoder-epoch-99-avg-1.onnx";
        }
        if ("conformer.joiner".equals(key)) {
            return "models/conformer-zh-stateless2/joiner-epoch-99-avg-1.onnx";
        }
        if ("conformer.tokens".equals(key)) {
            return "models/conformer-zh-stateless2/tokens.txt";
        }
        if ("conformer.modelingUnit".equals(key)) {
            return "cjkchar";
        }
        if ("conformer.decodingMethod".equals(key)) {
            return "modified_beam_search";
        }
        if ("conformer.maxActivePaths".equals(key)) {
            return "20";
        }
        if ("conformer.hotwordsScore".equals(key)) {
            return "30.0";
        }
        if ("vad.sileroModel".equals(key)) {
            return "models/vad/silero_vad.onnx";
        }
        if ("vad.windowSize".equals(key)) {
            return "512";
        }
        if ("vad.threshold".equals(key)) {
            return "0.55";
        }
        if ("vad.minSilenceDuration".equals(key)) {
            return "0.35";
        }
        if ("vad.minSpeechDuration".equals(key)) {
            return "0.60";
        }
        if ("vad.maxSpeechDuration".equals(key)) {
            return "5.0";
        }
        if ("speaker.segmentationModel".equals(key)) {
            return "models/speaker/pyannote-segmentation-3-0.onnx";
        }
        if ("speaker.embeddingModel".equals(key)) {
            return "models/speaker/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx";
        }
        if ("speaker.clusteringThreshold".equals(key)) {
            return "0.5";
        }
        if ("speaker.minDurationOn".equals(key)) {
            return "0.3";
        }
        if ("speaker.minDurationOff".equals(key)) {
            return "0.6";
        }
        if ("speaker.matchThreshold".equals(key)) {
            return "0.82";
        }
        return "";
    }

    private String normalizeValueForSave(ConfigFieldDefinition definition, String value) {
        String normalized = value == null ? "" : value.trim();
        if (definition.getInputType() == ConfigFieldDefinition.InputType.FILE
                || definition.getInputType() == ConfigFieldDefinition.InputType.DIRECTORY) {
            normalized = normalized.replace("\\", "/");
        }
        return normalized;
    }

    private static String getFileExtension(File file) {
        String name = file == null ? "" : file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= name.length() - 1) {
            return "";
        }
        return name.substring(dotIndex + 1);
    }

    private static String normalizeExtension(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim().toLowerCase();
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
