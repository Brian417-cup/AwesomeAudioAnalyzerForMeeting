package com.controller;

import com.model.SpeechRecognitionUnit;
import com.recognition.RecognitionRequest;
import com.recognition.RecognitionSegment;
import com.recognition.SherpaOnnxJavaRecognizer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RecognitionBackendController {

    public interface RecognitionCallback {
        void onProgress(int progress);

        void onSuccess(List<SpeechRecognitionUnit> result);

        void onError(String error);

        default void onSegmentDetected(SpeechRecognitionUnit unit, int index) {
        }

        default void onSegmentReady(SpeechRecognitionUnit unit, int index) {
        }
    }

    private final SherpaOnnxJavaRecognizer sherpaRecognizer = new SherpaOnnxJavaRecognizer();

    public void recognize(File audioFile, RecognitionCallback callback) {
        recognizeWithSettings(audioFile, false, null, false, false, 1, callback);
    }

    public void recognizeWithSettings(
            File audioFile,
            boolean useHotWords,
            File hotWordsFile,
            boolean useVoicePrint,
            boolean useFixedSpeakerCount,
            int speakerCount,
            RecognitionCallback callback
    ) {
        Thread recognizeThread = new Thread(() -> {
            try {
                callback.onProgress(3);

                RecognitionRequest request = new RecognitionRequest(
                        audioFile,
                        useHotWords,
                        hotWordsFile,
                        useVoicePrint,
                        useFixedSpeakerCount,
                        speakerCount
                );

                List<SpeechRecognitionUnit> units = new ArrayList<SpeechRecognitionUnit>();
                int[] decodedIndexHolder = new int[]{0};

                List<RecognitionSegment> segments = sherpaRecognizer.recognize(
                        request,
                        callback::onProgress,
                        segment -> {
                            int index = units.size();
                            String speaker = buildSpeaker(segment.getSpeakerLabel(), index, request.getSpeakerCount());
                            SpeechRecognitionUnit placeholderUnit = new SpeechRecognitionUnit(
                                    speaker,
                                    segment.getStartTime(),
                                    segment.getEndTime(),
                                    "正在转译..."
                            );
                            units.add(placeholderUnit);
                            callback.onSegmentDetected(placeholderUnit, index);
                        },
                        segment -> {
                            int index = decodedIndexHolder[0]++;
                            SpeechRecognitionUnit unit;
                            if (index < units.size()) {
                                unit = units.get(index);
                                unit.setStartTime(segment.getStartTime());
                                unit.setEndTime(segment.getEndTime());
                            } else {
                                String speaker = buildSpeaker(segment.getSpeakerLabel(), index, request.getSpeakerCount());
                                unit = new SpeechRecognitionUnit(
                                        speaker,
                                        segment.getStartTime(),
                                        segment.getEndTime(),
                                        ""
                                );
                                units.add(unit);
                            }
                            unit.setSpeaker(buildSpeaker(segment.getSpeakerLabel(), index, request.getSpeakerCount()));
                            unit.setContent(formatRecognizedText(segment.getText()));
                            callback.onSegmentReady(unit, index);
                        }
                );

                if (units.isEmpty()) {
                    for (int i = 0; i < segments.size(); i++) {
                        RecognitionSegment segment = segments.get(i);
                        String speaker = buildSpeaker(segment.getSpeakerLabel(), i, request.getSpeakerCount());
                        units.add(new SpeechRecognitionUnit(
                                speaker,
                                segment.getStartTime(),
                                segment.getEndTime(),
                                formatRecognizedText(segment.getText())
                        ));
                    }
                }

                callback.onProgress(100);
                callback.onSuccess(units);
            } catch (Exception e) {
                callback.onError(buildFriendlyError(e));
            }
        }, "sherpa-onnx-recognizer");

        recognizeThread.setDaemon(true);
        recognizeThread.start();
    }

    private String buildSpeaker(String preferredSpeaker, int index, int speakerCount) {
        if (preferredSpeaker != null && !preferredSpeaker.trim().isEmpty()) {
            return preferredSpeaker.trim();
        }
        if (speakerCount <= 1) {
            return "说话人1";
        }
        return "说话人" + ((index % speakerCount) + 1);
    }

    private String buildFriendlyError(Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().trim();
        return "识别失败: " + msg;
    }

    private String formatRecognizedText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "（该时间片未识别出文本）";
        }
        return text.trim();
    }
}
