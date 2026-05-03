package com.controller;

import com.model.VoicePrint;
import com.recognition.SherpaOnnxConfigStore;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class VoicePrintLibraryController {

    private static final String LEGACY_LIBRARY_FILE = "voice_print_library.dat";
    private static final String SQLITE_LIBRARY_FILE = "voice_print_library.db";

    private final Map<String, VoicePrint> voicePrintMap;
    private final ObservableList<VoicePrint> voicePrintList;
    private final File databaseFile;
    private final File managedAudioDir;
    private final File legacyLibraryFile;

    private static VoicePrintLibraryController instance;

    private VoicePrintLibraryController() {
        this.voicePrintMap = new HashMap<String, VoicePrint>();
        this.voicePrintList = FXCollections.observableArrayList();
        this.databaseFile = resolveDatabaseFile();
        this.managedAudioDir = resolveManagedAudioDir();
        this.legacyLibraryFile = new File(LEGACY_LIBRARY_FILE).getAbsoluteFile();

        try {
            Class.forName("org.sqlite.JDBC");
            initializeStorageDirectories();
            initializeDatabase();
            migrateLegacyDataIfNeeded();
            reloadFromDatabase();
        } catch (Exception e) {
            throw new IllegalStateException("声纹库初始化失败: " + e.getMessage(), e);
        }
    }

    public static synchronized VoicePrintLibraryController getInstance() {
        if (instance == null) {
            instance = new VoicePrintLibraryController();
        }
        return instance;
    }

    public ObservableList<VoicePrint> getVoicePrintList() {
        return voicePrintList;
    }

    public List<VoicePrint> getVoicePrintsByUser(String userName) {
        return voicePrintList.stream()
                .filter(vp -> vp.getUserName().equals(userName))
                .collect(Collectors.toList());
    }

    public List<String> getAllUserNames() {
        return voicePrintList.stream()
                .map(VoicePrint::getUserName)
                .distinct()
                .collect(Collectors.toList());
    }

    public synchronized VoicePrint addVoicePrint(String userName, String voiceName, File audioFile) {
        File storedAudioFile = importManagedAudio(audioFile, userName, voiceName);
        VoicePrint voicePrint = new VoicePrint(userName, voiceName, storedAudioFile);
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "insert into voice_prints(id, user_name, voice_name, file_path, create_time) values(?, ?, ?, ?, ?)"
             )) {
            statement.setString(1, voicePrint.getId());
            statement.setString(2, voicePrint.getUserName());
            statement.setString(3, voicePrint.getVoiceName());
            statement.setString(4, voicePrint.getFilePath());
            statement.setLong(5, voicePrint.getCreateTime());
            statement.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("新增声纹失败: " + e.getMessage(), e);
        }

        voicePrintMap.put(voicePrint.getId(), voicePrint);
        voicePrintList.add(voicePrint);
        sortInMemoryList();
        return voicePrint;
    }

    public synchronized boolean removeVoicePrint(String id) {
        VoicePrint removed = voicePrintMap.get(id);
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "delete from voice_prints where id = ?"
             )) {
            statement.setString(1, id);
            int affected = statement.executeUpdate();
            if (affected <= 0) {
                return false;
            }
        } catch (Exception e) {
            throw new IllegalStateException("删除声纹失败: " + e.getMessage(), e);
        }

        voicePrintMap.remove(id);
        voicePrintList.removeIf(vp -> vp.getId().equals(id));
        deleteManagedAudioFile(removed);
        return true;
    }

    public synchronized boolean updateVoicePrint(VoicePrint voicePrint) {
        if (voicePrint == null || voicePrint.getId() == null || voicePrint.getId().trim().isEmpty()) {
            return false;
        }

        VoicePrint previous = voicePrintMap.get(voicePrint.getId());
        File incomingFile = voicePrint.getAudioFile();
        if (incomingFile == null && voicePrint.getFilePath() != null && !voicePrint.getFilePath().trim().isEmpty()) {
            incomingFile = new File(voicePrint.getFilePath());
        }
        File storedAudioFile = importManagedAudio(incomingFile, voicePrint.getUserName(), voicePrint.getVoiceName());
        voicePrint.setAudioFile(storedAudioFile);

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "update voice_prints set user_name = ?, voice_name = ?, file_path = ?, create_time = ? where id = ?"
             )) {
            statement.setString(1, voicePrint.getUserName());
            statement.setString(2, voicePrint.getVoiceName());
            statement.setString(3, voicePrint.getFilePath());
            statement.setLong(4, voicePrint.getCreateTime());
            statement.setString(5, voicePrint.getId());
            int affected = statement.executeUpdate();
            if (affected <= 0) {
                return false;
            }
        } catch (Exception e) {
            throw new IllegalStateException("更新声纹失败: " + e.getMessage(), e);
        }

        voicePrintMap.put(voicePrint.getId(), voicePrint);
        int index = -1;
        for (int i = 0; i < voicePrintList.size(); i++) {
            if (voicePrintList.get(i).getId().equals(voicePrint.getId())) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            voicePrintList.set(index, voicePrint);
            sortInMemoryList();
        } else {
            voicePrintList.add(voicePrint);
            sortInMemoryList();
        }

        if (previous != null) {
            deleteManagedAudioFileIfReplaced(previous, voicePrint);
        }
        return true;
    }

    public synchronized void clearLibrary() {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("delete from voice_prints");
        } catch (Exception e) {
            throw new IllegalStateException("清空声纹库失败: " + e.getMessage(), e);
        }

        for (VoicePrint voicePrint : new ArrayList<VoicePrint>(voicePrintList)) {
            deleteManagedAudioFile(voicePrint);
        }
        voicePrintMap.clear();
        voicePrintList.clear();
    }

    public synchronized void purgeLibraryStorage() {
        voicePrintMap.clear();
        voicePrintList.clear();

        deleteRecursively(databaseFile);
        deleteRecursively(managedAudioDir);
        if (legacyLibraryFile.exists()) {
            legacyLibraryFile.delete();
        }

        try {
            initializeStorageDirectories();
            initializeDatabase();
        } catch (Exception e) {
            throw new IllegalStateException("重建声纹库存储失败: " + e.getMessage(), e);
        }
    }

    public File getDatabaseFile() {
        return databaseFile;
    }

    public File getManagedAudioDir() {
        return managedAudioDir;
    }

    private File resolveDatabaseFile() {
        try {
            SherpaOnnxConfigStore configStore = SherpaOnnxConfigStore.loadDefaultStore();
            return new File(configStore.getAudioTempSubDirectory("voiceprint_library"), SQLITE_LIBRARY_FILE);
        } catch (Exception e) {
            return new File(SQLITE_LIBRARY_FILE).getAbsoluteFile();
        }
    }

    private File resolveManagedAudioDir() {
        try {
            SherpaOnnxConfigStore configStore = SherpaOnnxConfigStore.loadDefaultStore();
            return configStore.getAudioTempSubDirectory("voiceprint_library/audio");
        } catch (Exception e) {
            return new File("voiceprint_library_audio").getAbsoluteFile();
        }
    }

    private void initializeStorageDirectories() {
        if (databaseFile.getParentFile() != null && !databaseFile.getParentFile().exists()) {
            databaseFile.getParentFile().mkdirs();
        }
        if (!managedAudioDir.exists()) {
            managedAudioDir.mkdirs();
        }
    }

    private void initializeDatabase() throws Exception {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("pragma journal_mode=WAL");
            statement.execute("pragma synchronous=NORMAL");
            statement.execute("create table if not exists voice_prints (" +
                    "id text primary key, " +
                    "user_name text not null, " +
                    "voice_name text not null, " +
                    "file_path text not null, " +
                    "create_time integer not null)");
            statement.execute("create index if not exists idx_voice_prints_user_name on voice_prints(user_name)");
            statement.execute("create index if not exists idx_voice_prints_create_time on voice_prints(create_time)");
        }
    }

    private void migrateLegacyDataIfNeeded() throws Exception {
        if (countVoicePrints() > 0) {
            return;
        }

        if (!legacyLibraryFile.exists()) {
            return;
        }

        List<VoicePrint> legacyVoicePrints = readLegacyVoicePrints(legacyLibraryFile);
        if (legacyVoicePrints.isEmpty()) {
            return;
        }

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "insert or ignore into voice_prints(id, user_name, voice_name, file_path, create_time) values(?, ?, ?, ?, ?)"
             )) {
            connection.setAutoCommit(false);
            for (VoicePrint voicePrint : legacyVoicePrints) {
                hydrateVoicePrint(voicePrint);
                statement.setString(1, voicePrint.getId());
                statement.setString(2, voicePrint.getUserName());
                statement.setString(3, voicePrint.getVoiceName());
                statement.setString(4, voicePrint.getFilePath() == null ? "" : voicePrint.getFilePath());
                statement.setLong(5, voicePrint.getCreateTime());
                statement.addBatch();
            }
            statement.executeBatch();
            connection.commit();
        }
    }

    private int countVoicePrints() throws Exception {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select count(*) from voice_prints")) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    @SuppressWarnings("unchecked")
    private List<VoicePrint> readLegacyVoicePrints(File legacyFile) {
        List<VoicePrint> result = new ArrayList<VoicePrint>();
        try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(legacyFile))) {
            Object data = inputStream.readObject();
            if (data instanceof List<?>) {
                for (Object item : (List<?>) data) {
                    if (item instanceof VoicePrint) {
                        result.add((VoicePrint) item);
                    }
                }
            }
        } catch (Exception ignored) {
            // 旧文件损坏时不阻断新存储初始化
        }
        return result;
    }

    private void reloadFromDatabase() throws Exception {
        List<VoicePrint> loaded = new ArrayList<VoicePrint>();
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "select id, user_name, voice_name, file_path, create_time " +
                             "from voice_prints order by lower(user_name), lower(voice_name), create_time"
             )) {
            while (resultSet.next()) {
                VoicePrint voicePrint = new VoicePrint();
                voicePrint.setId(resultSet.getString("id"));
                voicePrint.setUserName(resultSet.getString("user_name"));
                voicePrint.setVoiceName(resultSet.getString("voice_name"));
                voicePrint.setFilePath(resultSet.getString("file_path"));
                voicePrint.setCreateTime(resultSet.getLong("create_time"));
                hydrateVoicePrint(voicePrint);
                loaded.add(voicePrint);
            }
        }

        voicePrintMap.clear();
        voicePrintList.clear();
        for (VoicePrint voicePrint : loaded) {
            voicePrintMap.put(voicePrint.getId(), voicePrint);
            voicePrintList.add(voicePrint);
        }
    }

    private void hydrateVoicePrint(VoicePrint voicePrint) {
        if (voicePrint == null) {
            return;
        }
        String filePath = voicePrint.getFilePath();
        if (filePath != null && !filePath.trim().isEmpty()) {
            voicePrint.setAudioFile(new File(filePath));
        } else {
            voicePrint.setAudioFile(null);
        }
    }

    private File importManagedAudio(File sourceFile, String userName, String voiceName) {
        if (sourceFile == null) {
            throw new IllegalArgumentException("声纹音频文件不能为空。");
        }
        if (!sourceFile.exists()) {
            throw new IllegalArgumentException("声纹音频文件不存在: " + sourceFile.getAbsolutePath());
        }
        if (isManagedAudioFile(sourceFile)) {
            return sourceFile.getAbsoluteFile();
        }

        initializeStorageDirectories();
        String extension = getFileExtension(sourceFile);
        if (extension.isEmpty()) {
            extension = "wav";
        }
        String safeUser = sanitizeName(userName);
        String safeVoice = sanitizeName(voiceName);
        File userDir = new File(managedAudioDir, safeUser);
        if (!userDir.exists()) {
            userDir.mkdirs();
        }
        File targetFile = new File(
                userDir,
                safeVoice + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + extension
        );
        try {
            Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new IllegalStateException("复制声纹音频到托管目录失败: " + e.getMessage(), e);
        }
        return targetFile.getAbsoluteFile();
    }

    private boolean isManagedAudioFile(File file) {
        try {
            String managedRoot = managedAudioDir.getCanonicalPath();
            String candidate = file.getCanonicalPath();
            return candidate.startsWith(managedRoot + File.separator) || candidate.equals(managedRoot);
        } catch (Exception e) {
            return false;
        }
    }

    private void deleteManagedAudioFile(VoicePrint voicePrint) {
        if (voicePrint == null) {
            return;
        }
        String filePath = voicePrint.getFilePath();
        if (filePath == null || filePath.trim().isEmpty()) {
            return;
        }
        File file = new File(filePath);
        if (isManagedAudioFile(file) && file.exists()) {
            file.delete();
        }
    }

    private void deleteManagedAudioFileIfReplaced(VoicePrint previous, VoicePrint current) {
        if (previous == null || current == null) {
            return;
        }
        String oldPath = previous.getFilePath() == null ? "" : previous.getFilePath();
        String newPath = current.getFilePath() == null ? "" : current.getFilePath();
        if (!oldPath.isEmpty() && !oldPath.equals(newPath)) {
            File oldFile = new File(oldPath);
            if (isManagedAudioFile(oldFile) && oldFile.exists()) {
                oldFile.delete();
            }
        }
    }

    private void deleteRecursively(File target) {
        if (target == null || !target.exists()) {
            return;
        }
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        target.delete();
    }

    private void sortInMemoryList() {
        FXCollections.sort(voicePrintList, (left, right) -> {
            int userCompare = safeLower(left.getUserName()).compareTo(safeLower(right.getUserName()));
            if (userCompare != 0) {
                return userCompare;
            }
            int voiceCompare = safeLower(left.getVoiceName()).compareTo(safeLower(right.getVoiceName()));
            if (voiceCompare != 0) {
                return voiceCompare;
            }
            return Long.compare(left.getCreateTime(), right.getCreateTime());
        });
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private String sanitizeName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9\\-_\\u4e00-\\u9fa5]", "_");
    }

    private String getFileExtension(File file) {
        String name = file == null ? "" : file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= name.length() - 1) {
            return "";
        }
        return name.substring(dotIndex + 1).toLowerCase();
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
    }
}
