package com.recognition;

public class RecognitionSegment {
    private final double startTime;
    private final double endTime;
    private final String text;
    private final String speakerLabel;

    public RecognitionSegment(double startTime, double endTime, String text) {
        this(startTime, endTime, text, "");
    }

    public RecognitionSegment(double startTime, double endTime, String text, String speakerLabel) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.text = text == null ? "" : text;
        this.speakerLabel = speakerLabel == null ? "" : speakerLabel;
    }

    public double getStartTime() {
        return startTime;
    }

    public double getEndTime() {
        return endTime;
    }

    public String getText() {
        return text;
    }

    public String getSpeakerLabel() {
        return speakerLabel;
    }
}
