package com.recognition;

import com.controller.VoicePrintLibraryController;
import com.k2fsa.sherpa.onnx.FastClusteringConfig;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig;
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment;
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig;
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig;
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingManager;
import com.model.VoicePrint;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SpeakerDiarizationService {

    private static final double MIN_CHUNK_DURATION_FOR_SECONDARY_SPLIT = 0.75;
    private static final double MIN_SPLIT_DURATION_SECONDS = 0.35;
    private static final double MIN_SIGNIFICANT_OVERLAP_SECONDS = 0.32;
    private static final double MIN_SIGNIFICANT_OVERLAP_RATIO = 0.28;
    private static final double MIN_KEEP_RMS_ENERGY = 0.012;
    private static final double MIN_KEEP_MEAN_ABS = 0.009;

    public static class TimedChunk {
        private final double startTime;
        private final double endTime;
        private final float[] samples;
        private final int speakerId;
        private final String speakerLabel;

        public TimedChunk(double startTime, double endTime, float[] samples) {
            this(startTime, endTime, samples, -1, "");
        }

        public TimedChunk(double startTime, double endTime, float[] samples, int speakerId, String speakerLabel) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.samples = samples;
            this.speakerId = speakerId;
            this.speakerLabel = speakerLabel == null ? "" : speakerLabel;
        }

        public double getStartTime() {
            return startTime;
        }

        public double getEndTime() {
            return endTime;
        }

        public float[] getSamples() {
            return samples;
        }

        public double getDuration() {
            return Math.max(0.0, endTime - startTime);
        }

        public int getSpeakerId() {
            return speakerId;
        }

        public String getSpeakerLabel() {
            return speakerLabel;
        }
    }

    private final AudioSampleLoader audioSampleLoader = new AudioSampleLoader();

    public List<String> labelChunks(
            SherpaOnnxConfig config,
            float[] fullSamples,
            int sampleRate,
            List<TimedChunk> chunks,
            boolean useVoicePrint,
            boolean useFixedSpeakerCount,
            int speakerCount
    ) {
        List<String> fallback = buildFallbackLabels(chunks.size(), useFixedSpeakerCount ? speakerCount : 1);
        if (chunks.isEmpty()) {
            return fallback;
        }

        try {
            config.validateForSpeakerDiarization();
            OfflineSpeakerDiarizationSegment[] diarizationSegments = diarize(
                    config,
                    fullSamples,
                    sampleRate,
                    useFixedSpeakerCount,
                    speakerCount
            );
            if (diarizationSegments == null || diarizationSegments.length == 0) {
                return fallback;
            }

            List<Integer> chunkSpeakerIds = new ArrayList<Integer>();
            for (TimedChunk chunk : chunks) {
                chunkSpeakerIds.add(findSpeakerId(chunk, diarizationSegments));
            }

            Map<Integer, String> labelMap = buildDefaultLabelMap(chunkSpeakerIds);
            if (useVoicePrint) {
                mergeVoicePrintLabels(config, sampleRate, chunks, chunkSpeakerIds, labelMap);
            }

            List<String> labels = new ArrayList<String>();
            for (Integer speakerId : chunkSpeakerIds) {
                String label = labelMap.get(speakerId);
                labels.add(label == null || label.trim().isEmpty() ? "说话人1" : label);
            }
            return labels;
        } catch (Exception e) {
            return fallback;
        }
    }

    public List<TimedChunk> refineChunks(
            SherpaOnnxConfig config,
            float[] fullSamples,
            int sampleRate,
            List<TimedChunk> chunks,
            boolean useVoicePrint,
            boolean useFixedSpeakerCount,
            int speakerCount
    ) {
        if (chunks.isEmpty()) {
            return chunks;
        }

        try {
            config.validateForSpeakerDiarization();
            OfflineSpeakerDiarizationSegment[] diarizationSegments = diarize(
                    config,
                    fullSamples,
                    sampleRate,
                    useFixedSpeakerCount,
                    speakerCount
            );
            if (diarizationSegments == null || diarizationSegments.length == 0) {
                return applyFallbackLabels(chunks, useFixedSpeakerCount ? speakerCount : 1);
            }

            List<TimedChunk> refined = new ArrayList<TimedChunk>();
            for (TimedChunk chunk : chunks) {
                refined.addAll(splitChunkIfNeeded(chunk, diarizationSegments, fullSamples, sampleRate));
            }

            if (refined.isEmpty()) {
                return applyFallbackLabels(chunks, useFixedSpeakerCount ? speakerCount : 1);
            }

            Map<Integer, String> labelMap = buildDefaultLabelMapFromChunks(refined);
            if (useVoicePrint) {
                mergeVoicePrintLabelsForChunks(config, sampleRate, refined, labelMap);
            }

            List<TimedChunk> labeled = new ArrayList<TimedChunk>();
            for (TimedChunk chunk : refined) {
                String label = labelMap.get(chunk.getSpeakerId());
                labeled.add(new TimedChunk(
                        chunk.getStartTime(),
                        chunk.getEndTime(),
                        chunk.getSamples(),
                        chunk.getSpeakerId(),
                        label == null || label.trim().isEmpty() ? "说话人1" : label
                ));
            }
            return labeled;
        } catch (Exception e) {
            return applyFallbackLabels(chunks, useFixedSpeakerCount ? speakerCount : 1);
        }
    }

    private OfflineSpeakerDiarizationSegment[] diarize(
            SherpaOnnxConfig config,
            float[] fullSamples,
            int sampleRate,
            boolean useFixedSpeakerCount,
            int speakerCount
    ) {
        OfflineSpeakerDiarization diarization = null;
        try {
            OfflineSpeakerSegmentationPyannoteModelConfig pyannote =
                    OfflineSpeakerSegmentationPyannoteModelConfig.builder()
                            .setModel(config.getSpeakerSegmentationModelFile().getAbsolutePath())
                            .build();

            OfflineSpeakerSegmentationModelConfig segmentation =
                    OfflineSpeakerSegmentationModelConfig.builder()
                            .setPyannote(pyannote)
                            .setDebug(config.isDebug())
                            .build();

            SpeakerEmbeddingExtractorConfig embedding =
                    SpeakerEmbeddingExtractorConfig.builder()
                            .setModel(config.getSpeakerEmbeddingModelFile().getAbsolutePath())
                            .setNumThreads(config.getNumThreads())
                            .setDebug(config.isDebug())
                            .build();

            int numClusters = useFixedSpeakerCount ? Math.max(1, speakerCount) : -1;
            FastClusteringConfig clustering =
                    FastClusteringConfig.builder()
                            .setNumClusters(numClusters)
                            .setThreshold(config.getSpeakerClusteringThreshold())
                            .build();

            OfflineSpeakerDiarizationConfig diarizationConfig =
                    OfflineSpeakerDiarizationConfig.builder()
                            .setSegmentation(segmentation)
                            .setEmbedding(embedding)
                            .setClustering(clustering)
                            .setMinDurationOn(config.getSpeakerMinDurationOn())
                            .setMinDurationOff(config.getSpeakerMinDurationOff())
                            .build();

            diarization = new OfflineSpeakerDiarization(diarizationConfig);
            if (diarization.getSampleRate() != sampleRate) {
                return new OfflineSpeakerDiarizationSegment[0];
            }
            return diarization.process(fullSamples);
        } finally {
            if (diarization != null) {
                diarization.release();
            }
        }
    }

    private int findSpeakerId(TimedChunk chunk, OfflineSpeakerDiarizationSegment[] diarizationSegments) {
        double maxOverlap = -1.0;
        int bestSpeaker = diarizationSegments[0].getSpeaker();
        double chunkStart = chunk.getStartTime();
        double chunkEnd = chunk.getEndTime();

        for (OfflineSpeakerDiarizationSegment segment : diarizationSegments) {
            double overlap = overlap(chunkStart, chunkEnd, segment.getStart(), segment.getEnd());
            if (overlap > maxOverlap) {
                maxOverlap = overlap;
                bestSpeaker = segment.getSpeaker();
            }
        }
        return bestSpeaker;
    }

    private Map<Integer, String> buildDefaultLabelMap(List<Integer> chunkSpeakerIds) {
        Map<Integer, String> map = new LinkedHashMap<Integer, String>();
        int index = 1;
        for (Integer speakerId : chunkSpeakerIds) {
            if (!map.containsKey(speakerId)) {
                map.put(speakerId, "说话人" + index++);
            }
        }
        return map;
    }

    private Map<Integer, String> buildDefaultLabelMapFromChunks(List<TimedChunk> chunks) {
        Map<Integer, String> map = new LinkedHashMap<Integer, String>();
        int index = 1;
        for (TimedChunk chunk : chunks) {
            if (!map.containsKey(chunk.getSpeakerId())) {
                map.put(chunk.getSpeakerId(), "说话人" + index++);
            }
        }
        return map;
    }

    private void mergeVoicePrintLabels(
            SherpaOnnxConfig config,
            int sampleRate,
            List<TimedChunk> chunks,
            List<Integer> chunkSpeakerIds,
            Map<Integer, String> labelMap
    ) {
        VoicePrintLibraryController libraryController = VoicePrintLibraryController.getInstance();
        List<VoicePrint> voicePrints = new ArrayList<VoicePrint>(libraryController.getVoicePrintList());
        if (voicePrints.isEmpty()) {
            return;
        }

        SpeakerEmbeddingExtractor extractor = null;
        SpeakerEmbeddingManager manager = null;
        try {
            SpeakerEmbeddingExtractorConfig embeddingConfig =
                    SpeakerEmbeddingExtractorConfig.builder()
                            .setModel(config.getSpeakerEmbeddingModelFile().getAbsolutePath())
                            .setNumThreads(config.getNumThreads())
                            .setDebug(config.isDebug())
                            .build();

            extractor = new SpeakerEmbeddingExtractor(embeddingConfig);
            manager = new SpeakerEmbeddingManager(extractor.getDim());

            Map<String, List<float[]>> userEmbeddings = new LinkedHashMap<String, List<float[]>>();
            for (VoicePrint voicePrint : voicePrints) {
                File audioFile = resolveAudioFile(voicePrint);
                if (audioFile == null || !audioFile.exists()) {
                    continue;
                }
                float[] embedding = computeEmbedding(extractor, audioFile);
                if (embedding == null || embedding.length == 0) {
                    continue;
                }
                if (!userEmbeddings.containsKey(voicePrint.getUserName())) {
                    userEmbeddings.put(voicePrint.getUserName(), new ArrayList<float[]>());
                }
                userEmbeddings.get(voicePrint.getUserName()).add(embedding);
            }

            for (Map.Entry<String, List<float[]>> entry : userEmbeddings.entrySet()) {
                List<float[]> embeddings = entry.getValue();
                if (!embeddings.isEmpty()) {
                    manager.add(entry.getKey(), embeddings.toArray(new float[embeddings.size()][]));
                }
            }

            if (manager.getNumSpeakers() == 0) {
                return;
            }

            Map<Integer, float[]> speakerEmbeddings = computeClusterEmbeddings(extractor, sampleRate, chunks, chunkSpeakerIds);
            for (Map.Entry<Integer, float[]> entry : speakerEmbeddings.entrySet()) {
                String matchedName = manager.search(entry.getValue(), config.getSpeakerMatchThreshold());
                if (matchedName != null && !matchedName.trim().isEmpty()) {
                    labelMap.put(entry.getKey(), matchedName);
                }
            }
        } catch (Exception ignored) {
            // 声纹库匹配失败时仍保留 diarization 的说话人编号，不影响主识别流程。
        } finally {
            if (manager != null) {
                manager.release();
            }
            if (extractor != null) {
                extractor.release();
            }
        }
    }

    private void mergeVoicePrintLabelsForChunks(
            SherpaOnnxConfig config,
            int sampleRate,
            List<TimedChunk> chunks,
            Map<Integer, String> labelMap
    ) {
        List<Integer> chunkSpeakerIds = new ArrayList<Integer>();
        for (TimedChunk chunk : chunks) {
            chunkSpeakerIds.add(chunk.getSpeakerId());
        }
        mergeVoicePrintLabels(config, sampleRate, chunks, chunkSpeakerIds, labelMap);
    }

    private Map<Integer, float[]> computeClusterEmbeddings(
            SpeakerEmbeddingExtractor extractor,
            int sampleRate,
            List<TimedChunk> chunks,
            List<Integer> chunkSpeakerIds
    ) {
        Map<Integer, List<TimedChunk>> grouped = new HashMap<Integer, List<TimedChunk>>();
        for (int i = 0; i < chunks.size(); i++) {
            Integer speakerId = chunkSpeakerIds.get(i);
            if (!grouped.containsKey(speakerId)) {
                grouped.put(speakerId, new ArrayList<TimedChunk>());
            }
            grouped.get(speakerId).add(chunks.get(i));
        }

        Map<Integer, float[]> result = new HashMap<Integer, float[]>();
        for (Map.Entry<Integer, List<TimedChunk>> entry : grouped.entrySet()) {
            List<TimedChunk> speakerChunks = entry.getValue();
            speakerChunks.sort(Comparator.comparingDouble(TimedChunk::getDuration).reversed());
            List<float[]> embeddings = new ArrayList<float[]>();
            for (int i = 0; i < speakerChunks.size() && i < 3; i++) {
                TimedChunk chunk = speakerChunks.get(i);
                if (chunk.getDuration() < 0.3) {
                    continue;
                }
                float[] embedding = computeEmbedding(extractor, chunk.getSamples(), sampleRate);
                if (embedding != null && embedding.length > 0) {
                    embeddings.add(embedding);
                }
            }

            if (!embeddings.isEmpty()) {
                result.put(entry.getKey(), averageEmbeddings(embeddings));
            }
        }
        return result;
    }

    private float[] computeEmbedding(SpeakerEmbeddingExtractor extractor, File audioFile) throws Exception {
        AudioSampleLoader.AudioData audioData = audioSampleLoader.loadAsMonoFloat(audioFile, 16000, "voiceprint/diarization/library");
        return computeEmbedding(extractor, audioData.getSamples(), audioData.getSampleRate());
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

    private float[] averageEmbeddings(List<float[]> embeddings) {
        int dim = embeddings.get(0).length;
        float[] avg = new float[dim];
        for (float[] embedding : embeddings) {
            for (int i = 0; i < dim; i++) {
                avg[i] += embedding[i];
            }
        }
        for (int i = 0; i < dim; i++) {
            avg[i] /= embeddings.size();
        }
        return avg;
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

    private List<String> buildFallbackLabels(int size, int speakerCount) {
        List<String> labels = new ArrayList<String>();
        for (int i = 0; i < size; i++) {
            if (speakerCount <= 1) {
                labels.add("说话人1");
            } else {
                labels.add("说话人" + ((i % speakerCount) + 1));
            }
        }
        return labels;
    }

    private List<TimedChunk> applyFallbackLabels(List<TimedChunk> chunks, int speakerCount) {
        List<TimedChunk> labeled = new ArrayList<TimedChunk>();
        for (int i = 0; i < chunks.size(); i++) {
            TimedChunk chunk = chunks.get(i);
            String label = speakerCount <= 1 ? "说话人1" : "说话人" + ((i % speakerCount) + 1);
            labeled.add(new TimedChunk(
                    chunk.getStartTime(),
                    chunk.getEndTime(),
                    chunk.getSamples(),
                    i,
                    label
            ));
        }
        return labeled;
    }

    private List<TimedChunk> splitChunkIfNeeded(
            TimedChunk chunk,
            OfflineSpeakerDiarizationSegment[] diarizationSegments,
            float[] fullSamples,
            int sampleRate
    ) {
        if (chunk.getDuration() < MIN_CHUNK_DURATION_FOR_SECONDARY_SPLIT) {
            return buildSingleSpeakerChunk(chunk, diarizationSegments);
        }

        List<OfflineSpeakerDiarizationSegment> relevant = new ArrayList<OfflineSpeakerDiarizationSegment>();
        List<Integer> relevantSpeakerIds = new ArrayList<Integer>();
        double chunkDuration = Math.max(0.001, chunk.getDuration());
        for (OfflineSpeakerDiarizationSegment segment : diarizationSegments) {
            double overlap = overlap(chunk.getStartTime(), chunk.getEndTime(), segment.getStart(), segment.getEnd());
            if (overlap >= MIN_SIGNIFICANT_OVERLAP_SECONDS
                    || overlap / chunkDuration >= MIN_SIGNIFICANT_OVERLAP_RATIO) {
                relevant.add(segment);
                if (!relevantSpeakerIds.contains(segment.getSpeaker())) {
                    relevantSpeakerIds.add(segment.getSpeaker());
                }
            }
        }

        if (relevantSpeakerIds.size() <= 1) {
            return buildSingleSpeakerChunk(chunk, diarizationSegments);
        }

        List<Double> boundaries = new ArrayList<Double>();
        boundaries.add(chunk.getStartTime());
        boundaries.add(chunk.getEndTime());
        for (OfflineSpeakerDiarizationSegment segment : relevant) {
            double start = Math.max(chunk.getStartTime(), segment.getStart());
            double end = Math.min(chunk.getEndTime(), segment.getEnd());
            boundaries.add(start);
            boundaries.add(end);
        }

        boundaries.sort(Double::compareTo);
        List<TimedChunk> pieces = new ArrayList<TimedChunk>();
        double currentStart = boundaries.get(0);
        for (int i = 1; i < boundaries.size(); i++) {
            double currentEnd = boundaries.get(i);
            if (currentEnd - currentStart < MIN_SPLIT_DURATION_SECONDS) {
                continue;
            }
            int speakerId = findSpeakerId(new TimedChunk(currentStart, currentEnd, new float[0]), diarizationSegments);
            float[] pieceSamples = sliceSamples(fullSamples, sampleRate, currentStart, currentEnd);
            if (pieceSamples.length > 0 && shouldKeepPiece(pieceSamples, currentEnd - currentStart)) {
                pieces.add(new TimedChunk(currentStart, currentEnd, pieceSamples, speakerId, ""));
            }
            currentStart = currentEnd;
        }

        if (pieces.isEmpty()) {
            return buildSingleSpeakerChunk(chunk, diarizationSegments);
        }

        return mergeAdjacentSameSpeaker(pieces);
    }

    private List<TimedChunk> buildSingleSpeakerChunk(
            TimedChunk chunk,
            OfflineSpeakerDiarizationSegment[] diarizationSegments
    ) {
        int speakerId = findSpeakerId(chunk, diarizationSegments);
        List<TimedChunk> single = new ArrayList<TimedChunk>();
        single.add(new TimedChunk(
                chunk.getStartTime(),
                chunk.getEndTime(),
                chunk.getSamples(),
                speakerId,
                ""
        ));
        return single;
    }

    private List<TimedChunk> mergeAdjacentSameSpeaker(List<TimedChunk> chunks) {
        if (chunks.isEmpty()) {
            return chunks;
        }

        List<TimedChunk> merged = new ArrayList<TimedChunk>();
        TimedChunk current = chunks.get(0);
        for (int i = 1; i < chunks.size(); i++) {
            TimedChunk next = chunks.get(i);
            if (current.getSpeakerId() == next.getSpeakerId()
                    && Math.abs(current.getEndTime() - next.getStartTime()) < 0.03) {
                current = new TimedChunk(
                        current.getStartTime(),
                        next.getEndTime(),
                        concatSamples(current.getSamples(), next.getSamples()),
                        current.getSpeakerId(),
                        current.getSpeakerLabel()
                );
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
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

    private float[] concatSamples(float[] first, float[] second) {
        float[] merged = new float[first.length + second.length];
        System.arraycopy(first, 0, merged, 0, first.length);
        System.arraycopy(second, 0, merged, first.length, second.length);
        return merged;
    }

    private boolean shouldKeepPiece(float[] samples, double durationSeconds) {
        if (samples == null || samples.length == 0 || durationSeconds < MIN_SPLIT_DURATION_SECONDS) {
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
        return rms >= MIN_KEEP_RMS_ENERGY || meanAbs >= MIN_KEEP_MEAN_ABS;
    }

    private double overlap(double startA, double endA, double startB, double endB) {
        double start = Math.max(startA, startB);
        double end = Math.min(endA, endB);
        return Math.max(0.0, end - start);
    }
}
