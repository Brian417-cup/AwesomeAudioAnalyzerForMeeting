package com.recognition;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AudioSourceResolver {

    private static final long MIN_VALID_WAV_BYTES = 44L;
    private static final String CACHE_CONVERTED_DIR = "converted";

    public static class ResolvedAudio {
        private final File originalFile;
        private final File workingFile;
        private final boolean converted;

        public ResolvedAudio(File originalFile, File workingFile, boolean converted) {
            this.originalFile = originalFile;
            this.workingFile = workingFile;
            this.converted = converted;
        }

        public File getOriginalFile() {
            return originalFile;
        }

        public File getWorkingFile() {
            return workingFile;
        }

        public boolean isConverted() {
            return converted;
        }
    }

    public ResolvedAudio resolveForProcessing(File inputFile) throws Exception {
        return resolveForProcessing(inputFile, "general");
    }

    public ResolvedAudio resolveForProcessing(File inputFile, String category) throws Exception {
        if (inputFile == null || !inputFile.exists()) {
            throw new IllegalArgumentException("音频文件不存在: " + (inputFile == null ? "null" : inputFile.getAbsolutePath()));
        }

        SherpaOnnxConfigStore configStore = SherpaOnnxConfigStore.loadDefaultStore();
        if (!configStore.isSupportedAudioFile(inputFile)) {
            throw new IllegalArgumentException(
                    "当前配置不允许导入该音频格式: " + inputFile.getName()
                            + "。允许格式: " + String.join(", ", configStore.getSupportedAudioExtensions())
            );
        }

        String extension = getFileExtension(inputFile);
        if ("wav".equals(extension)) {
            return new ResolvedAudio(inputFile, inputFile, false);
        }

        if (!configStore.isAutoConvertWithFfmpeg()) {
            throw new IllegalStateException("当前已关闭 ffmpeg 自动转 WAV，请在配置中开启后再导入非 WAV 音频。");
        }

        File ffmpegFile = configStore.getFfmpegFile();
        if (ffmpegFile == null || !ffmpegFile.exists()) {
            throw new IllegalStateException("找不到 ffmpeg 程序，请在配置中设置正确的 ffmpeg 路径。");
        }

        File outputFile = buildCacheFile(inputFile, category, configStore);
        if (outputFile.exists()
                && outputFile.lastModified() >= inputFile.lastModified()
                && outputFile.length() > MIN_VALID_WAV_BYTES) {
            return new ResolvedAudio(inputFile, outputFile, true);
        }

        convertToWav(ffmpegFile, inputFile, outputFile);
        return new ResolvedAudio(inputFile, outputFile, true);
    }

    private File buildCacheFile(File inputFile, String category, SherpaOnnxConfigStore configStore) {
        File cacheDir = new File(
                configStore.getAudioTempSubDirectory(CACHE_CONVERTED_DIR),
                sanitizeCategory(category) + File.separator + sanitizeCategory(getFileExtension(inputFile))
        );
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        String fileName = inputFile.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String safeName = baseName.replaceAll("[^a-zA-Z0-9\\-_\\.]", "_");
        String sourceExt = getFileExtension(inputFile);
        if (sourceExt == null || sourceExt.trim().isEmpty()) {
            sourceExt = "unknown";
        }
        String uniquePart = Integer.toHexString(inputFile.getAbsolutePath().hashCode())
                + "_" + inputFile.lastModified();
        return new File(cacheDir, safeName + "__from_" + sourceExt + "__" + uniquePart + ".wav");
    }

    private void convertToWav(File ffmpegFile, File inputFile, File outputFile) throws Exception {
        String extension = getFileExtension(inputFile);
        String lastMessage = "";

        String[][] strategies = new String[][]{
                new String[0],
                new String[]{"-probesize", "10000000", "-analyzeduration", "10000000"}
        };

        for (String[] strategy : strategies) {
            deleteIfExists(outputFile);
            String message = runFfmpegToWav(ffmpegFile, inputFile, outputFile, strategy);
            if (isValidWav(outputFile)) {
                return;
            }
            lastMessage = message;
        }

        throw new IllegalStateException(buildFriendlyFfmpegMessage(extension, inputFile, lastMessage));
    }

    private String runFfmpegToWav(
            File ffmpegFile,
            File inputFile,
            File outputFile,
            String[] inputArgs
    ) throws Exception {
        List<String> command = buildFfmpegBaseCommand(ffmpegFile, inputArgs, inputFile);
        command.add("-c:a");
        command.add("pcm_s16le");
        command.add("-ac");
        command.add("1");
        command.add("-ar");
        command.add("16000");
        command.add(outputFile.getAbsolutePath());
        return executeFfmpeg(command);
    }

    private List<String> buildFfmpegBaseCommand(File ffmpegFile, String[] inputArgs, File inputFile) {
        List<String> command = new ArrayList<String>();
        command.add(ffmpegFile.getAbsolutePath());
        command.add("-y");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.add("-nostdin");
        command.add("-vn");

        if (inputArgs != null) {
            for (String arg : inputArgs) {
                command.add(arg);
            }
        }

        command.add("-i");
        command.add(inputFile.getAbsolutePath());
        return command;
    }

    private String executeFfmpeg(List<String> command) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
        byte[] temp = new byte[2048];
        int read;
        while ((read = process.getInputStream().read(temp)) != -1) {
            outputBuffer.write(temp, 0, read);
        }

        process.waitFor();
        return new String(outputBuffer.toByteArray(), "UTF-8").trim();
    }

    private boolean isValidWav(File file) {
        return file != null && file.exists() && file.length() > MIN_VALID_WAV_BYTES;
    }

    private void deleteIfExists(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private String buildFriendlyFfmpegMessage(String extension, File inputFile, String rawMessage) {
        StringBuilder builder = new StringBuilder("ffmpeg 转换失败");
        if (rawMessage != null && !rawMessage.trim().isEmpty()) {
            builder.append(": ").append(rawMessage.trim());
        } else {
            builder.append("。");
        }

        if ("aac".equals(extension)) {
            builder.append(" 当前已按 ffmpeg 直接识别输入并转换为标准 WAV，不再额外做 AAC/LOAS 强制解析或重封装。");
            builder.append(" 文件: ").append(inputFile.getName());
        }
        return builder.toString();
    }

    private String getFileExtension(File file) {
        String name = file == null ? "" : file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= name.length() - 1) {
            return "";
        }
        return name.substring(dotIndex + 1).toLowerCase();
    }

    private String sanitizeCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return "general";
        }
        String normalized = category.replace('\\', '/').trim();
        String[] parts = normalized.split("/");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(File.separatorChar);
            }
            builder.append(part.replaceAll("[^a-zA-Z0-9\\-_\\.]", "_"));
        }
        if (builder.length() == 0) {
            return "general";
        }
        return builder.toString();
    }
}
