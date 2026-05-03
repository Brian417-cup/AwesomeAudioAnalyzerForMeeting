package com.recognition;

import com.controller.VoicePrintLibraryController;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig;
import com.model.VoicePrint;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VoicePrintSmartMatchService {

    private static final int TARGET_SAMPLE_RATE = 16000;
    private static final int MAX_CANDIDATES = 8;
    private static final double MIN_SEGMENT_DURATION_SECONDS = 0.25;

    private final AudioSampleLoader audioSampleLoader = new AudioSampleLoader();
    private File cachedAudioFile;
    private long cachedAudioLastModified = -1L;
    private AudioSampleLoader.AudioData cachedAudioData;
    private final Map<String, CachedEmbedding> voicePrintEmbeddingCache = new HashMap<String, CachedEmbedding>();

    public static class Candidate {
        private final String userName;
        private final String voiceName;
        private final double score;
        private final int voiceCount;

        public Candidate(String userName, String voiceName, double score, int voiceCount) {
            this.userName = userName;
            this.voiceName = voiceName;
            this.score = score;
            this.voiceCount = voiceCount;
        }

        public String getUserName() {
            return userName;
        }

        public String getVoiceName() {
            return voiceName;
        }

        public double getScore() {
            return score;
        }

        public int getVoiceCount() {
            return voiceCount;
        }

        @Override
        public String toString() {
            int percent = (int) Math.round(score * 100);
            String countInfo = voiceCount > 1 ? "（该用户已注册 " + voiceCount + " 条声纹）" : "";
            return userName + "  平均匹配度 " + percent + "%" + countInfo;
        }
    }

    private static class CandidateAccumulator {
        private String userName;
        private String voiceName;
        private double bestScore;
        private double totalScore;
        private int matchedVoiceCount;
        private int voiceCount;
    }

    private static class CachedEmbedding {
        private final String filePath;
        private final long lastModified;
        private final float[] embedding;

        private CachedEmbedding(String filePath, long lastModified, float[] embedding) {
            this.filePath = filePath;
            this.lastModified = lastModified;
            this.embedding = embedding;
        }
    }

    public synchronized List<Candidate> findCandidates(
            File audioFile,
            double startTime,
            double endTime,
            float threshold
    ) throws Exception {
        if (audioFile == null || !audioFile.exists()) {
            throw new IllegalArgumentException("当前音频文件不存在，暂时无法进行智能替换。");
        }
        if (endTime - startTime < MIN_SEGMENT_DURATION_SECONDS) {
            throw new IllegalArgumentException("这段讲话太短了，暂时无法稳定判断更像哪位已注册发言人。");
        }

        VoicePrintLibraryController libraryController = VoicePrintLibraryController.getInstance();
        List<VoicePrint> voicePrints = new ArrayList<VoicePrint>(libraryController.getVoicePrintList());
        if (voicePrints.isEmpty()) {
            return Collections.emptyList();
        }

        SherpaOnnxConfig config = SherpaOnnxConfig.loadDefault();
        config.validateForSpeakerDiarization();

        AudioSampleLoader.AudioData audioData = loadAudioData(audioFile);
        float[] segmentSamples = sliceSamples(audioData.getSamples(), audioData.getSampleRate(), startTime, endTime);
        if (segmentSamples.length == 0) {
            throw new IllegalArgumentException("当前片段没有可用的语音采样，暂时无法进行智能替换。");
        }

        SpeakerEmbeddingExtractor extractor = null;
        try {
            SpeakerEmbeddingExtractorConfig embeddingConfig =
                    SpeakerEmbeddingExtractorConfig.builder()
                            .setModel(config.getSpeakerEmbeddingModelFile().getAbsolutePath())
                            .setNumThreads(config.getNumThreads())
                            .setDebug(config.isDebug())
                            .build();

            extractor = new SpeakerEmbeddingExtractor(embeddingConfig);
            float[] segmentEmbedding = computeEmbedding(extractor, segmentSamples, audioData.getSampleRate());
            if (segmentEmbedding == null || segmentEmbedding.length == 0) {
                throw new IllegalArgumentException("当前片段没有提取出稳定的说话人特征，请换一段更清晰或更长一点的讲话再试。");
            }

            Map<String, Integer> userVoiceCounts = buildUserVoiceCounts(voicePrints);
            Map<String, CandidateAccumulator> candidateMap = new LinkedHashMap<String, CandidateAccumulator>();
            for (VoicePrint voicePrint : voicePrints) {
                File voiceAudioFile = resolveAudioFile(voicePrint);
                if (voiceAudioFile == null || !voiceAudioFile.exists()) {
                    continue;
                }

                float[] voiceEmbedding = getOrCreateVoicePrintEmbedding(extractor, voicePrint, voiceAudioFile);
                if (voiceEmbedding == null || voiceEmbedding.length == 0) {
                    continue;
                }

                double similarity = cosineSimilarity(segmentEmbedding, voiceEmbedding);
                CandidateAccumulator accumulator = candidateMap.get(voicePrint.getUserName());
                if (accumulator == null) {
                    accumulator = new CandidateAccumulator();
                    accumulator.userName = voicePrint.getUserName();
                    accumulator.voiceName = voicePrint.getVoiceName();
                    accumulator.bestScore = similarity;
                    accumulator.totalScore = similarity;
                    accumulator.matchedVoiceCount = 1;
                    accumulator.voiceCount = userVoiceCounts.containsKey(voicePrint.getUserName())
                            ? userVoiceCounts.get(voicePrint.getUserName()) : 1;
                    candidateMap.put(voicePrint.getUserName(), accumulator);
                } else {
                    accumulator.totalScore += similarity;
                    accumulator.matchedVoiceCount++;
                    if (similarity > accumulator.bestScore) {
                        accumulator.bestScore = similarity;
                        accumulator.voiceName = voicePrint.getVoiceName();
                    }
                }
            }

            List<Candidate> candidates = new ArrayList<Candidate>();
            for (CandidateAccumulator accumulator : candidateMap.values()) {
                if (accumulator.matchedVoiceCount <= 0) {
                    continue;
                }
                double averageScore = accumulator.totalScore / accumulator.matchedVoiceCount;
                if (averageScore < threshold) {
                    continue;
                }
                candidates.add(new Candidate(
                        accumulator.userName,
                        accumulator.voiceName,
                        averageScore,
                        accumulator.voiceCount
                ));
            }
            candidates.sort(new Comparator<Candidate>() {
                @Override
                public int compare(Candidate left, Candidate right) {
                    int scoreCompare = Double.compare(right.getScore(), left.getScore());
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    return left.getUserName().compareToIgnoreCase(right.getUserName());
                }
            });

            if (candidates.size() > MAX_CANDIDATES) {
                return new ArrayList<Candidate>(candidates.subList(0, MAX_CANDIDATES));
            }
            return candidates;
        } finally {
            if (extractor != null) {
                extractor.release();
            }
        }
    }

    private Map<String, Integer> buildUserVoiceCounts(List<VoicePrint> voicePrints) {
        Map<String, Integer> result = new HashMap<String, Integer>();
        for (VoicePrint voicePrint : voicePrints) {
            String userName = voicePrint.getUserName() == null ? "" : voicePrint.getUserName().trim();
            if (userName.isEmpty()) {
                continue;
            }
            Integer current = result.get(userName);
            result.put(userName, current == null ? 1 : current + 1);
        }
        return result;
    }

    private AudioSampleLoader.AudioData loadAudioData(File audioFile) throws Exception {
        String path = audioFile.getAbsolutePath();
        long lastModified = audioFile.lastModified();
        if (cachedAudioData != null
                && cachedAudioFile != null
                && path.equals(cachedAudioFile.getAbsolutePath())
                && lastModified == cachedAudioLastModified) {
            return cachedAudioData;
        }

        cachedAudioData = audioSampleLoader.loadAsMonoFloat(audioFile, TARGET_SAMPLE_RATE, "voiceprint/match/source");
        cachedAudioFile = audioFile;
        cachedAudioLastModified = lastModified;
        return cachedAudioData;
    }

    private float[] getOrCreateVoicePrintEmbedding(
            SpeakerEmbeddingExtractor extractor,
            VoicePrint voicePrint,
            File audioFile
    ) throws Exception {
        String cacheKey = voicePrint.getId() == null ? audioFile.getAbsolutePath() : voicePrint.getId();
        String path = audioFile.getAbsolutePath();
        long lastModified = audioFile.lastModified();

        CachedEmbedding cached = voicePrintEmbeddingCache.get(cacheKey);
        if (cached != null && path.equals(cached.filePath) && lastModified == cached.lastModified) {
            return cached.embedding;
        }

        AudioSampleLoader.AudioData audioData = audioSampleLoader.loadAsMonoFloat(audioFile, TARGET_SAMPLE_RATE, "voiceprint/match/library");
        float[] embedding = computeEmbedding(extractor, audioData.getSamples(), audioData.getSampleRate());
        if (embedding != null && embedding.length > 0) {
            voicePrintEmbeddingCache.put(cacheKey, new CachedEmbedding(path, lastModified, embedding));
        }
        return embedding;
    }

    private float[] computeEmbedding(SpeakerEmbeddingExtractor extractor, float[] samples, int sampleRate) {
        if (samples == null || samples.length == 0) {
            return null;
        }

        OnlineStream stream = extractor.createStream();
        try {
            stream.acceptWaveform(samples, sampleRate);
            stream.inputFinished();
            return extractor.compute(stream);
        } finally {
            stream.release();
        }
    }

    private File resolveAudioFile(VoicePrint voicePrint) {
        if (voicePrint.getAudioFile() != null) {
            return voicePrint.getAudioFile();
        }
        if (voicePrint.getFilePath() == null || voicePrint.getFilePath().trim().isEmpty()) {
            return null;
        }
        return new File(voicePrint.getFilePath());
    }

    private float[] sliceSamples(float[] fullSamples, int sampleRate, double startTime, double endTime) {
        int start = Math.max(0, (int) Math.round(startTime * sampleRate));
        int end = Math.min(fullSamples.length, (int) Math.round(endTime * sampleRate));
        if (end <= start) {
            return new float[0];
        }
        float[] output = new float[end - start];
        System.arraycopy(fullSamples, start, output, 0, end - start);
        return output;
    }

    private double cosineSimilarity(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0) {
            return 0.0;
        }
        int size = Math.min(left.length, right.length);
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < size; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm <= 0.0 || rightNorm <= 0.0) {
            return 0.0;
        }
        double score = dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            return 0.0;
        }
        return Math.max(-1.0, Math.min(1.0, score));
    }
}
