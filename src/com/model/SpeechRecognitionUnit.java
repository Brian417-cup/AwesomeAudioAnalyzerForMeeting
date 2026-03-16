package com.model;

public class SpeechRecognitionUnit {
    private String speaker;
    private double startTime;
    private double endTime;
    private String content;

    public SpeechRecognitionUnit() {
    }

    public SpeechRecognitionUnit(String speaker, double startTime, double endTime, String content) {
        this.speaker = speaker;
        this.startTime = startTime;
        this.endTime = endTime;
        this.content = content;
    }

    public String getSpeaker() {
        return speaker;
    }

    public void setSpeaker(String speaker) {
        this.speaker = speaker;
    }

    public double getStartTime() {
        return startTime;
    }

    public void setStartTime(double startTime) {
        this.startTime = startTime;
    }

    public double getEndTime() {
        return endTime;
    }

    public void setEndTime(double endTime) {
        this.endTime = endTime;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFormattedTime() {
        int startMin = (int) startTime / 60;
        int startSec = (int) startTime % 60;
        int endMin = (int) endTime / 60;
        int endSec = (int) endTime % 60;
        return String.format("%02d:%02d - %02d:%02d", startMin, startSec, endMin, endSec);
    }
}
