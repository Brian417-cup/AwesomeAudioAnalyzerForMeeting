package com.search;

import com.model.SpeechRecognitionUnit;
import com.recognition.SherpaOnnxConfigStore;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecognitionSearchStore {

    private final Map<Integer, SpeechRecognitionUnit> unitByRowId = new LinkedHashMap<Integer, SpeechRecognitionUnit>();
    private String lastSignature = "";
    private File sqliteFile;
    private boolean initialized = false;

    public synchronized List<SpeechRecognitionUnit> filter(
            List<SpeechRecognitionUnit> source,
            String keyword,
            SherpaOnnxConfigStore configStore
    ) {
        ensureSqliteReady(configStore);
        ensureIndexCurrent(source, configStore);

        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<SpeechRecognitionUnit>(source);
        }

        String normalized = keyword.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return new ArrayList<SpeechRecognitionUnit>(source);
        }

        try {
            return searchWithSqlite(normalized);
        } catch (Exception e) {
            throw new IllegalStateException("SQLite 搜索执行失败: " + e.getMessage(), e);
        }
    }

    private void ensureSqliteReady(SherpaOnnxConfigStore configStore) {
        if (initialized) {
            return;
        }

        try {
            Class.forName("org.sqlite.JDBC");
            sqliteFile = resolveSqliteFile(configStore);
            if (sqliteFile == null) {
                throw new IllegalStateException("无法确定 SQLite 临时索引文件路径。");
            }
            initializeSchema();
            initialized = true;
        } catch (Exception e) {
            throw new IllegalStateException("SQLite 搜索初始化失败，请确认 sqlite-jdbc 已加入工程依赖。详细信息: " + e.getMessage(), e);
        }
    }

    private void ensureIndexCurrent(List<SpeechRecognitionUnit> source, SherpaOnnxConfigStore configStore) {
        String signature = buildSignature(source);
        File targetDbFile = resolveSqliteFile(configStore);
        String dbSignature = targetDbFile == null ? "" : targetDbFile.getAbsolutePath();
        String combinedSignature = signature + "::" + dbSignature;
        if (combinedSignature.equals(lastSignature)) {
            return;
        }

        unitByRowId.clear();
        for (int i = 0; i < source.size(); i++) {
            unitByRowId.put(i + 1, source.get(i));
        }

        sqliteFile = targetDbFile;
        try {
            rebuildSqliteIndex();
        } catch (Exception e) {
            throw new IllegalStateException("SQLite 搜索索引重建失败: " + e.getMessage(), e);
        }

        lastSignature = combinedSignature;
    }

    private void initializeSchema() throws Exception {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("pragma journal_mode=WAL");
            statement.execute("pragma synchronous=NORMAL");
            statement.execute("create table if not exists recognition_segments (" +
                    "id integer primary key, " +
                    "speaker text not null, " +
                    "content text not null, " +
                    "speaker_lower text not null, " +
                    "content_lower text not null, " +
                    "start_time real not null, " +
                    "end_time real not null)");
            statement.execute("create index if not exists idx_recognition_segments_speaker_lower on recognition_segments(speaker_lower)");
            statement.execute("create index if not exists idx_recognition_segments_content_lower on recognition_segments(content_lower)");
            statement.execute("create index if not exists idx_recognition_segments_time on recognition_segments(start_time, end_time)");
        }
    }

    private String buildSignature(List<SpeechRecognitionUnit> source) {
        StringBuilder builder = new StringBuilder();
        builder.append(source.size()).append('|');
        for (SpeechRecognitionUnit unit : source) {
            builder.append(unit.getStartTime()).append('|')
                    .append(unit.getEndTime()).append('|')
                    .append(unit.getSpeaker() == null ? "" : unit.getSpeaker()).append('|')
                    .append(unit.getContent() == null ? "" : unit.getContent()).append('|');
        }
        return builder.toString();
    }

    private File resolveSqliteFile(SherpaOnnxConfigStore configStore) {
        try {
            SherpaOnnxConfigStore store = configStore != null ? configStore : SherpaOnnxConfigStore.loadDefaultStore();
            File dir = store.getAudioTempSubDirectory("search_index/sqlite");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            return new File(dir, "recognition_search.db");
        } catch (Exception e) {
            return null;
        }
    }

    private void rebuildSqliteIndex() throws Exception {
        if (sqliteFile == null) {
            throw new IllegalStateException("SQLite 索引文件未初始化。");
        }

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("delete from recognition_segments");
        }

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "insert into recognition_segments(" +
                             "id, speaker, content, speaker_lower, content_lower, start_time, end_time" +
                             ") values(?, ?, ?, ?, ?, ?, ?)"
             )) {
            connection.setAutoCommit(false);
            for (Map.Entry<Integer, SpeechRecognitionUnit> entry : unitByRowId.entrySet()) {
                SpeechRecognitionUnit unit = entry.getValue();
                String speaker = unit.getSpeaker() == null ? "" : unit.getSpeaker();
                String content = unit.getContent() == null ? "" : unit.getContent();

                statement.setInt(1, entry.getKey());
                statement.setString(2, speaker);
                statement.setString(3, content);
                statement.setString(4, speaker.toLowerCase());
                statement.setString(5, content.toLowerCase());
                statement.setDouble(6, unit.getStartTime());
                statement.setDouble(7, unit.getEndTime());
                statement.addBatch();
            }
            statement.executeBatch();
            connection.commit();
        }
    }

    private List<SpeechRecognitionUnit> searchWithSqlite(String normalizedKeyword) throws Exception {
        List<SpeechRecognitionUnit> result = new ArrayList<SpeechRecognitionUnit>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select id from recognition_segments " +
                             "where speaker_lower like ? or content_lower like ? " +
                             "order by start_time, end_time, id"
             )) {
            String likeValue = "%" + normalizedKeyword + "%";
            statement.setString(1, likeValue);
            statement.setString(2, likeValue);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int id = resultSet.getInt(1);
                    SpeechRecognitionUnit unit = unitByRowId.get(id);
                    if (unit != null) {
                        result.add(unit);
                    }
                }
            }
        }
        return result;
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
    }
}
