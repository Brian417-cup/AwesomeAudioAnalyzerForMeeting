package com.recognition;

import java.io.File;

public class RecognitionRequest {
    private final File audioFile;
    private final boolean useHotWords;
    private final File hotWordsFile;
    private final boolean useVoicePrint;
    private final boolean useFixedSpeakerCount;
    private final int speakerCount;

    public RecognitionRequest(
            File audioFile,
            boolean useHotWords,
            File hotWordsFile,
            boolean useVoicePrint,
            boolean useFixedSpeakerCount,
            int speakerCount
    ) {
        if (audioFile == null) {
            throw new IllegalArgumentException("音频文件不能为空。");
        }
        if (!audioFile.exists()) {
            throw new IllegalArgumentException("音频文件不存在: " + audioFile.getAbsolutePath());
        }
        this.audioFile = audioFile;
        this.useHotWords = useHotWords;
        this.hotWordsFile = hotWordsFile;
        this.useVoicePrint = useVoicePrint;
        this.useFixedSpeakerCount = useFixedSpeakerCount;
        this.speakerCount = Math.max(speakerCount, 1);
    }

    public File getAudioFile() {
        return audioFile;
    }

    public boolean isUseHotWords() {
        return useHotWords;
    }

    public File getHotWordsFile() {
        return hotWordsFile;
    }

    public boolean isUseVoicePrint() {
        return useVoicePrint;
    }

    public boolean isUseFixedSpeakerCount() {
        return useFixedSpeakerCount;
    }

    public int getSpeakerCount() {
        return speakerCount;
    }
}
