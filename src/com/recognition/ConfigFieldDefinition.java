package com.recognition;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ConfigFieldDefinition {

    public enum InputType {
        TEXT,
        FILE,
        DIRECTORY,
        SELECT
    }

    private final String key;
    private final String section;
    private final String label;
    private final String description;
    private final InputType inputType;
    private final List<String> options;
    private final List<String> filePatterns;

    private ConfigFieldDefinition(
            String key,
            String section,
            String label,
            String description,
            InputType inputType,
            List<String> options,
            List<String> filePatterns
    ) {
        this.key = key;
        this.section = section;
        this.label = label;
        this.description = description;
        this.inputType = inputType;
        this.options = options == null ? Collections.<String>emptyList() : options;
        this.filePatterns = filePatterns == null ? Collections.<String>emptyList() : filePatterns;
    }

    public static ConfigFieldDefinition text(String key, String section, String label, String description) {
        return new ConfigFieldDefinition(key, section, label, description, InputType.TEXT, null, null);
    }

    public static ConfigFieldDefinition file(
            String key,
            String section,
            String label,
            String description,
            String... filePatterns
    ) {
        return new ConfigFieldDefinition(
                key,
                section,
                label,
                description,
                InputType.FILE,
                null,
                filePatterns == null ? null : Arrays.asList(filePatterns)
        );
    }

    public static ConfigFieldDefinition directory(
            String key,
            String section,
            String label,
            String description
    ) {
        return new ConfigFieldDefinition(
                key,
                section,
                label,
                description,
                InputType.DIRECTORY,
                null,
                null
        );
    }

    public static ConfigFieldDefinition select(
            String key,
            String section,
            String label,
            String description,
            String... options
    ) {
        return new ConfigFieldDefinition(
                key,
                section,
                label,
                description,
                InputType.SELECT,
                options == null ? null : Arrays.asList(options),
                null
        );
    }

    public String getKey() {
        return key;
    }

    public String getSection() {
        return section;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public InputType getInputType() {
        return inputType;
    }

    public List<String> getOptions() {
        return options;
    }

    public List<String> getFilePatterns() {
        return filePatterns;
    }
}
