package com.recognition;

import com.model.SpeechRecognitionUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RecognitionTextSegmenter {

    public List<SpeechRecognitionUnit> toUnits(String text, File audioFile, int speakerCount) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            throw new IllegalStateException("识别文本为空。");
        }

        List<String> sentences = splitSentences(normalized);
        double totalDuration = getAudioDurationSeconds(audioFile);
        if (totalDuration <= 0) {
            totalDuration = Math.max(5, sentences.size() * 4.0);
        }

        int totalChars = 0;
        for (String s : sentences) {
            totalChars += Math.max(1, s.length());
        }

        List<SpeechRecognitionUnit> result = new ArrayList<>();
        double cursor = 0.0;
        int actualSpeakerCount = Math.max(1, speakerCount);

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            double ratio = Math.max(1, sentence.length()) / (double) totalChars;
            double segDuration = (i == sentences.size() - 1)
                    ? Math.max(0.1, totalDuration - cursor)
                    : Math.max(0.1, totalDuration * ratio);

            double start = cursor;
            double end = Math.min(totalDuration, start + segDuration);
            cursor = end;

            // 当前版本暂未做真正说话人分离，先按轮转方式标注，保证列表展示效果稳定。
            String speaker = "说话人" + ((i % actualSpeakerCount) + 1);
            result.add(new SpeechRecognitionUnit(speaker, start, end, sentence));
        }

        return result;
    }

    private List<String> splitSentences(String text) {
        String[] raw = text.split("(?<=[。！？!?；;\\n])");
        List<String> list = new ArrayList<>();
        for (String r : raw) {
            String s = r.trim();
            if (!s.isEmpty()) {
                list.add(s);
            }
        }
        if (list.isEmpty()) {
            list.add(text);
        }
        return list;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\r', '\n')
                .replaceAll("\\n{2,}", "\n")
                .trim();
    }

    private double getAudioDurationSeconds(File file) {
        if (file == null || !file.exists()) {
            return 0.0;
        }
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {
            AudioFormat format = ais.getFormat();
            long frameLength = ais.getFrameLength();
            if (frameLength <= 0 || format.getFrameRate() <= 0) {
                return 0.0;
            }
            return frameLength / format.getFrameRate();
        } catch (Exception e) {
            return 0.0;
        }
    }
}
