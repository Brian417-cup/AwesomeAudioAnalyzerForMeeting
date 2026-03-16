package com.controller;

import com.model.SpeechRecognitionUnit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RecognitionBackendController {

    public interface RecognitionCallback {
        void onProgress(int progress);
        void onSuccess(List<SpeechRecognitionUnit> result);
        void onError(String error);
    }

    public void recognize(File audioFile, RecognitionCallback callback) {
        recognizeWithSettings(audioFile, false, null, false, 1, callback);
    }

    public void recognizeWithSettings(
            File audioFile,
            boolean useHotWords,
            File hotWordsFile,
            boolean useVoicePrint,
            int speakerCount,
            RecognitionCallback callback
    ) {
        new Thread(() -> {
            try {
                List<SpeechRecognitionUnit> fakeResult = generateFakeRecognition(
                        audioFile,
                        useHotWords,
                        hotWordsFile,
                        useVoicePrint,
                        speakerCount
                );

                for (int i = 0; i <= 100; i += 5) {
                    callback.onProgress(i);
                    Thread.sleep(150);
                }

                callback.onSuccess(fakeResult);
            } catch (Exception e) {
                callback.onError("识别失败：" + e.getMessage());
            }
        }).start();
    }

    private List<SpeechRecognitionUnit> generateFakeRecognition(
            File file,
            boolean useHotWords,
            File hotWordsFile,
            boolean useVoicePrint,
            int speakerCount
    ) throws Exception {
        List<SpeechRecognitionUnit> units = new ArrayList<>();

        String[] baseSpeakers = {"张三", "李四", "王五", "赵六", "钱七"};
        String[] speakers;

        if (useVoicePrint && speakerCount > 0) {
            speakers = new String[speakerCount];
            for (int i = 0; i < speakerCount && i < baseSpeakers.length; i++) {
                speakers[i] = baseSpeakers[i] + " (声纹#" + (i + 1) + ")";
            }
            if (speakerCount > baseSpeakers.length) {
                for (int i = baseSpeakers.length; i < speakerCount; i++) {
                    speakers[i] = "说话人" + (i + 1) + " (声纹#" + (i + 1) + ")";
                }
            }
        } else {
            speakers = baseSpeakers;
        }

        String[] texts = {
                "各位好，今天我们来讨论一下项目的整体进展情况。",
                "我觉得这个方案从技术角度是可行的，但需要考虑成本。",
                "我同意你的看法，不过我们还需要更多数据支持。",
                "关于这个问题，我有一些不同的想法想分享一下。",
                "那我们下一步的工作重点应该放在哪里呢？",
                "我建议我们先做一个原型出来验证一下核心功能。",
                "好的，我来负责前端部分的开发和测试工作。",
                "后端接口我会尽快完成，预计下周可以联调。",
                "测试用例我已经准备好了，可以随时开始测试。",
                "那我们约定下周三再开个会对齐一下进度吧。"
        };

        if (useHotWords && hotWordsFile != null) {
            texts = loadHotWords(hotWordsFile, texts);
        }

        Random random = new Random();
        double currentTime = 0.0;
        double totalDuration = 300.0;

        while (currentTime < totalDuration) {
            String speaker = speakers[random.nextInt(speakers.length)];
            String text = texts[random.nextInt(texts.length)];

            double duration = 4.0 + random.nextDouble() * 8.0;
            double endTime = Math.min(currentTime + duration, totalDuration);

            SpeechRecognitionUnit unit = new SpeechRecognitionUnit(speaker, currentTime, endTime, text);
            units.add(unit);

            currentTime = endTime + 1.0 + random.nextDouble() * 3.0;
        }

        return units;
    }

    private String[] loadHotWords(File hotWordsFile, String[] defaultTexts) {
        try {
            java.util.Scanner scanner = new java.util.Scanner(hotWordsFile, "UTF-8");
            List<String> hotWordsList = new ArrayList<>();

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (!line.isEmpty()) {
                    hotWordsList.add(line);
                }
            }
            scanner.close();

            if (!hotWordsList.isEmpty()) {
                return hotWordsList.toArray(new String[0]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return defaultTexts;
    }
}
