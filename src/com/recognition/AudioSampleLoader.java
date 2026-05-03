package com.recognition;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayOutputStream;
import java.io.File;

public class AudioSampleLoader {

    private final AudioSourceResolver audioSourceResolver = new AudioSourceResolver();

    public static class AudioData {
        private final float[] samples;
        private final int sampleRate;

        public AudioData(float[] samples, int sampleRate) {
            this.samples = samples;
            this.sampleRate = sampleRate;
        }

        public float[] getSamples() {
            return samples;
        }

        public int getSampleRate() {
            return sampleRate;
        }
    }

    public AudioData loadAsMonoFloat(File file, int targetSampleRate) throws Exception {
        return loadAsMonoFloat(file, targetSampleRate, "audio-sample-loader");
    }

    public AudioData loadAsMonoFloat(File file, int targetSampleRate, String cacheCategory) throws Exception {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("音频文件不存在: " + (file == null ? "null" : file.getAbsolutePath()));
        }

        AudioSourceResolver.ResolvedAudio resolvedAudio = audioSourceResolver.resolveForProcessing(file, cacheCategory);
        try (AudioInputStream input = AudioSystem.getAudioInputStream(resolvedAudio.getWorkingFile())) {
            AudioFormat sourceFormat = input.getFormat();

            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.getSampleRate(),
                    16,
                    sourceFormat.getChannels(),
                    sourceFormat.getChannels() * 2,
                    sourceFormat.getSampleRate(),
                    false
            );

            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcmFormat, input)) {
                byte[] pcmBytes = readAllBytes(pcmStream);
                float[] mono = toMonoFloat(pcmBytes, pcmFormat.getChannels());

                int srcRate = Math.round(pcmFormat.getSampleRate());
                if (srcRate == targetSampleRate) {
                    return new AudioData(mono, srcRate);
                }

                float[] resampled = resampleLinear(mono, srcRate, targetSampleRate);
                return new AudioData(resampled, targetSampleRate);
            }
        }
    }

    private byte[] readAllBytes(AudioInputStream stream) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

    private float[] toMonoFloat(byte[] bytes, int channels) {
        if (channels <= 0) {
            channels = 1;
        }
        int bytesPerSample = 2;
        int frameSize = channels * bytesPerSample;
        int frames = bytes.length / frameSize;
        float[] out = new float[frames];

        for (int i = 0; i < frames; i++) {
            int frameOffset = i * frameSize;
            float sum = 0.0f;
            for (int c = 0; c < channels; c++) {
                int offset = frameOffset + c * 2;
                int low = bytes[offset] & 0xff;
                int high = bytes[offset + 1];
                short sample = (short) ((high << 8) | low);
                sum += sample / 32768.0f;
            }
            out[i] = sum / channels;
        }

        return out;
    }

    private float[] resampleLinear(float[] input, int srcRate, int dstRate) {
        if (input.length == 0 || srcRate <= 0 || dstRate <= 0) {
            return input;
        }
        if (srcRate == dstRate) {
            return input;
        }

        int outLength = (int) Math.max(1, Math.round(input.length * (double) dstRate / srcRate));
        float[] output = new float[outLength];

        double scale = (double) srcRate / dstRate;
        for (int i = 0; i < outLength; i++) {
            double srcPos = i * scale;
            int left = (int) Math.floor(srcPos);
            int right = Math.min(left + 1, input.length - 1);
            double frac = srcPos - left;
            float lv = input[Math.min(left, input.length - 1)];
            float rv = input[right];
            output[i] = (float) (lv * (1.0 - frac) + rv * frac);
        }
        return output;
    }
}
