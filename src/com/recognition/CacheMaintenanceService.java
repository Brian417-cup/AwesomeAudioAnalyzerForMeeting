package com.recognition;

import com.controller.VoicePrintLibraryController;

import java.io.File;

public class CacheMaintenanceService {

    public static class CleanupSummary {
        private int removedFiles;
        private int removedDirectories;

        public void add(CleanupSummary other) {
            if (other == null) {
                return;
            }
            this.removedFiles += other.removedFiles;
            this.removedDirectories += other.removedDirectories;
        }

        public void fileRemoved() {
            removedFiles++;
        }

        public void directoryRemoved() {
            removedDirectories++;
        }

        public int getRemovedFiles() {
            return removedFiles;
        }

        public int getRemovedDirectories() {
            return removedDirectories;
        }
    }

    public CleanupSummary clearIntermediateFiles(SherpaOnnxConfigStore configStore) {
        File convertedDir = configStore.getAudioTempSubDirectory("converted");
        CleanupSummary summary = deleteDirectoryContents(convertedDir);
        if (!convertedDir.exists()) {
            convertedDir.mkdirs();
        }
        return summary;
    }

    public CleanupSummary clearRecognitionDatabases(SherpaOnnxConfigStore configStore) {
        File searchDir = configStore.getAudioTempSubDirectory("search_index");
        CleanupSummary summary = deleteDirectoryContents(searchDir);
        if (!searchDir.exists()) {
            searchDir.mkdirs();
        }
        return summary;
    }

    public CleanupSummary clearVoicePrintLibraryFiles(SherpaOnnxConfigStore configStore) {
        VoicePrintLibraryController.getInstance().purgeLibraryStorage();
        File voicePrintDir = configStore.getAudioTempSubDirectory("voiceprint_library");
        if (!voicePrintDir.exists()) {
            voicePrintDir.mkdirs();
        }
        CleanupSummary summary = new CleanupSummary();
        summary.directoryRemoved();
        return summary;
    }

    public CleanupSummary clearHotWordsFiles(SherpaOnnxConfigStore configStore) {
        File hotWordsDir = configStore.getAudioTempSubDirectory("hotwords");
        CleanupSummary summary = deleteDirectoryContents(hotWordsDir);
        if (!hotWordsDir.exists()) {
            hotWordsDir.mkdirs();
        }
        return summary;
    }

    private CleanupSummary deleteDirectoryContents(File dir) {
        CleanupSummary summary = new CleanupSummary();
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return summary;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return summary;
        }
        for (File child : children) {
            deleteRecursively(child, summary);
        }
        return summary;
    }

    private void deleteRecursively(File target, CleanupSummary summary) {
        if (target == null || !target.exists()) {
            return;
        }
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child, summary);
                }
            }
            if (target.delete()) {
                summary.directoryRemoved();
            }
        } else {
            if (target.delete()) {
                summary.fileRemoved();
            }
        }
    }
}
