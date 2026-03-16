package com.model;

import java.io.File;
import java.io.Serializable;

public class VoicePrint implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String userName;
    private String voiceName;
    private transient File audioFile;
    private String filePath;
    private long createTime;

    public VoicePrint() {
    }

    public VoicePrint(String userName, String voiceName, File audioFile) {
        this.id = java.util.UUID.randomUUID().toString();
        this.userName = userName;
        this.voiceName = voiceName;
        this.audioFile = audioFile;
        this.filePath = audioFile != null ? audioFile.getAbsolutePath() : "";
        this.createTime = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getVoiceName() {
        return voiceName;
    }

    public void setVoiceName(String voiceName) {
        this.voiceName = voiceName;
    }

    public File getAudioFile() {
        return audioFile;
    }

    public void setAudioFile(File audioFile) {
        this.audioFile = audioFile;
        this.filePath = audioFile != null ? audioFile.getAbsolutePath() : "";
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
}
