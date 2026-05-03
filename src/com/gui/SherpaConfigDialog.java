package com.gui;

import com.recognition.ConfigFieldDefinition;
import com.recognition.SherpaOnnxConfigStore;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SherpaConfigDialog {

    private static final String PRESET_DEFAULT = "default";
    private static final String PRESET_QUIET = "quiet";
    private static final String PRESET_DISCUSSION = "discussion";
    private static final String PRESET_FAR_FIELD = "far_field";

    public interface SaveListener {
        void onSaved(SherpaOnnxConfigStore updatedStore);
    }

    private final Window owner;
    private final SherpaOnnxConfigStore configStore;
    private final SaveListener saveListener;

    public SherpaConfigDialog(Window owner, SherpaOnnxConfigStore configStore, SaveListener saveListener) {
        this.owner = owner;
        this.configStore = configStore;
        this.saveListener = saveListener;
    }

    public void show() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle("识别配置");

        Map<String, Control> controls = new LinkedHashMap<String, Control>();
        VBox paneContainer = new VBox(10);
        paneContainer.setStyle("-fx-background-color: transparent;");
        List<TitledPane> panes = new ArrayList<TitledPane>();

        Map<String, VBox> sectionContents = new LinkedHashMap<String, VBox>();
        for (ConfigFieldDefinition definition : configStore.getFieldDefinitions()) {
            VBox sectionBox = sectionContents.get(definition.getSection());
            if (sectionBox == null) {
                sectionBox = new VBox(10);
                sectionBox.setPadding(new Insets(6));
                sectionBox.getChildren().add(createSectionIntro(definition.getSection(), controls));

                TitledPane pane = new TitledPane(definition.getSection(), sectionBox);
                pane.setAnimated(false);
                pane.setCollapsible(true);
                pane.setExpanded(false);
                pane.setStyle("-fx-text-fill: #1f3c5b; -fx-font-size: 14px; -fx-font-weight: bold;");
                paneContainer.getChildren().add(pane);
                panes.add(pane);

                sectionContents.put(definition.getSection(), sectionBox);
            }

            sectionBox.getChildren().add(buildFieldRow(dialog, definition, controls));
        }

        if (!panes.isEmpty()) {
            panes.get(0).setExpanded(true);
        }

        VBox content = new VBox(14);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color: #f7f9fc;");

        Label introTitle = new Label("识别配置");
        introTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1f3c5b;");

        Label introDesc = new Label(
                "每一组都可以折叠。建议先按场景套用推荐值，再根据实际识别效果微调少量参数。"
        );
        introDesc.setWrapText(true);
        introDesc.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7785;");

        VBox presetBox = createPresetBox(controls);
        content.getChildren().addAll(introTitle, introDesc, presetBox, paneContainer);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #f7f9fc; -fx-border-color: #d7dde5;");

        Button expandAllBtn = new Button("全部展开");
        expandAllBtn.setStyle(getButtonStyle("#3498db"));
        expandAllBtn.setOnAction(e -> {
            for (TitledPane pane : panes) {
                pane.setExpanded(true);
            }
        });

        Button collapseAllBtn = new Button("全部收起");
        collapseAllBtn.setStyle(getButtonStyle("#5d6d7e"));
        collapseAllBtn.setOnAction(e -> {
            for (TitledPane pane : panes) {
                pane.setExpanded(false);
            }
        });

        Button reloadBtn = new Button("重新读取文件");
        reloadBtn.setStyle(getButtonStyle("#16a085"));
        reloadBtn.setOnAction(e -> reloadValues(controls));

        Button saveBtn = new Button("保存并应用");
        saveBtn.setStyle(getButtonStyle("#27ae60"));
        saveBtn.setOnAction(e -> {
            try {
                validateControls(controls);
                boolean changed = hasControlChanges(controls);
                persistControls(controls);
                configStore.save();
                configStore.load();
                if (saveListener != null) {
                    saveListener.onSaved(configStore);
                }
                dialog.close();
                if (changed) {
                    showSimpleInfo(owner, "配置修改成功，新的识别参数已经保存并应用。");
                }
            } catch (Exception ex) {
                showSimpleError(dialog, "保存失败: " + ex.getMessage());
            }
        });

        Button closeBtn = new Button("关闭");
        closeBtn.setStyle(getButtonStyle("#95a5a6"));
        closeBtn.setOnAction(e -> dialog.close());

        HBox buttonBar = new HBox(10, expandAllBtn, collapseAllBtn, reloadBtn, saveBtn, closeBtn);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(12, 16, 16, 16));

        VBox root = new VBox(scrollPane, buttonBar);
        root.setStyle("-fx-background-color: #f7f9fc;");

        Scene scene = new Scene(root, 940, 780);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private VBox createSectionIntro(String section, Map<String, Control> controls) {
        VBox introBox = new VBox(8);
        introBox.setPadding(new Insets(2, 0, 6, 0));

        Label sectionDesc = new Label(getSectionDescription(section));
        sectionDesc.setWrapText(true);
        sectionDesc.setStyle("-fx-text-fill: #5f6b7a; -fx-font-size: 12px;");

        HBox actionRow = new HBox(10);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        Label actionHint = new Label("如果这一组调乱了，可以只恢复本组的推荐默认值。");
        actionHint.setStyle("-fx-text-fill: #8a97a6; -fx-font-size: 11px;");

        Button resetSectionBtn = new Button("恢复本组默认值");
        resetSectionBtn.setStyle(getButtonStyle("#6c7a89"));
        resetSectionBtn.setOnAction(e -> applySectionDefaults(section, controls));

        actionRow.getChildren().addAll(resetSectionBtn, actionHint);
        introBox.getChildren().addAll(sectionDesc, actionRow);
        return introBox;
    }

    private VBox createPresetBox(Map<String, Control> controls) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dfe6ee; -fx-border-radius: 8; -fx-background-radius: 8;");

        Label title = new Label("常用场景推荐");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label desc = new Label(
                "先点一个最接近的场景，系统会帮你填入推荐值。你仍然可以在下方继续手动修改。"
        );
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7785;");

        HBox presetRow = new HBox(10);
        presetRow.setAlignment(Pos.TOP_LEFT);

        presetRow.getChildren().addAll(
                createPresetItem("恢复推荐默认值", "回到系统建议的通用配置，适合大多数正常录音。", "#7f8c8d", PRESET_DEFAULT, controls),
                createPresetItem("安静会议室", "适合轮流发言、背景声少、麦克风距离较近的场景。", "#3498db", PRESET_QUIET, controls),
                createPresetItem("多人讨论", "适合多人频繁接话、短句较多、发言切换快的场景。", "#16a085", PRESET_DISCUSSION, controls),
                createPresetItem("远场录音/有噪声", "适合会议室拾音较远、空调噪声或环境声较明显的场景。", "#f39c12", PRESET_FAR_FIELD, controls)
        );

        box.getChildren().addAll(title, desc, presetRow);
        return box;
    }

    private VBox createPresetItem(
            String buttonText,
            String description,
            String color,
            String presetId,
            Map<String, Control> controls
    ) {
        VBox item = new VBox(6);
        item.setPrefWidth(205);
        item.setMaxWidth(205);
        item.setStyle("-fx-background-color: #f8fafc; -fx-background-radius: 8; -fx-border-color: #e3e9ef; -fx-border-radius: 8; -fx-padding: 10;");

        Button button = new Button(buttonText);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setStyle(getButtonStyle(color));
        button.setOnAction(e -> applyPreset(presetId, controls));

        Label descLabel = new Label(description);
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #66717f;");

        item.getChildren().addAll(button, descLabel);
        return item;
    }

    private VBox buildFieldRow(Stage ownerStage, ConfigFieldDefinition definition, Map<String, Control> controls) {
        VBox row = new VBox(6);
        row.setPadding(new Insets(12));
        row.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e0e6ed; -fx-border-radius: 8;");

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(definition.getLabel());
        label.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        Label helpBadge = createHelpBadge(definition.getKey());

        Label desc = new Label(definition.getDescription());
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7785;");

        String rangeHint = getRangeHint(definition.getKey());
        Label hintLabel = new Label(
                rangeHint.isEmpty()
                        ? "内部配置项: " + definition.getKey()
                        : "建议范围: " + rangeHint + "    内部配置项: " + definition.getKey()
        );
        hintLabel.setWrapText(true);
        hintLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #9aa5b1;");

        Control control;
        if (definition.getInputType() == ConfigFieldDefinition.InputType.SELECT) {
            ComboBox<String> comboBox = new ComboBox<String>(FXCollections.observableArrayList(definition.getOptions()));
            comboBox.setMaxWidth(Double.MAX_VALUE);
            comboBox.setValue(configStore.getValue(definition.getKey()));
            Label riskBadge = createRiskBadge(definition.getKey(), comboBox.getValue());
            comboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateRiskBadge(riskBadge, definition.getKey(), newVal));
            titleRow.getChildren().addAll(label, helpBadge, riskBadge);
            control = comboBox;
        } else if (definition.getInputType() == ConfigFieldDefinition.InputType.FILE) {
            TextField pathField = new TextField(configStore.getValue(definition.getKey()));
            pathField.setPromptText("请选择文件路径");
            pathField.setTooltip(new Tooltip(pathField.getText()));
            pathField.textProperty().addListener((obs, oldVal, newVal) -> pathField.setTooltip(new Tooltip(newVal)));
            pathField.setMaxWidth(Double.MAX_VALUE);
            titleRow.getChildren().addAll(label, helpBadge);

            Button browseBtn = new Button("选择文件");
            browseBtn.setStyle(getButtonStyle("#3498db"));
            browseBtn.setOnAction(e -> {
                FileChooser chooser = new FileChooser();
                chooser.setTitle(definition.getLabel());
                List<String> patterns = definition.getFilePatterns();
                if (patterns != null && !patterns.isEmpty()) {
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("可选文件", patterns));
                } else {
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("所有文件", "*.*"));
                }
                File selected = chooser.showOpenDialog(ownerStage);
                if (selected != null) {
                    pathField.setText(selected.getAbsolutePath().replace("\\", "/"));
                }
            });

            HBox fileBox = new HBox(10, pathField, browseBtn);
            HBox.setHgrow(pathField, Priority.ALWAYS);
            row.getChildren().addAll(titleRow, desc, hintLabel, fileBox);
            controls.put(definition.getKey(), pathField);
            return row;
        } else if (definition.getInputType() == ConfigFieldDefinition.InputType.DIRECTORY) {
            TextField pathField = new TextField(configStore.getValue(definition.getKey()));
            pathField.setPromptText("请选择目录路径");
            pathField.setTooltip(new Tooltip(pathField.getText()));
            pathField.textProperty().addListener((obs, oldVal, newVal) -> pathField.setTooltip(new Tooltip(newVal)));
            pathField.setMaxWidth(Double.MAX_VALUE);
            titleRow.getChildren().addAll(label, helpBadge);

            Button browseBtn = new Button("选择目录");
            browseBtn.setStyle(getButtonStyle("#3498db"));
            browseBtn.setOnAction(e -> {
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle(definition.getLabel());
                String currentPath = pathField.getText() == null ? "" : pathField.getText().trim();
                if (!currentPath.isEmpty()) {
                    File currentDir = new File(currentPath);
                    if (currentDir.exists()) {
                        chooser.setInitialDirectory(currentDir.isDirectory() ? currentDir : currentDir.getParentFile());
                    }
                }
                File selected = chooser.showDialog(ownerStage);
                if (selected != null) {
                    pathField.setText(selected.getAbsolutePath().replace("\\", "/"));
                }
            });

            HBox directoryBox = new HBox(10, pathField, browseBtn);
            HBox.setHgrow(pathField, Priority.ALWAYS);
            row.getChildren().addAll(titleRow, desc, hintLabel, directoryBox);
            controls.put(definition.getKey(), pathField);
            return row;
        } else {
            TextField textField = new TextField(configStore.getValue(definition.getKey()));
            textField.setMaxWidth(Double.MAX_VALUE);
            String example = getExampleValue(definition.getKey());
            if (!example.isEmpty()) {
                textField.setPromptText(example);
            }
            Label riskBadge = createRiskBadge(definition.getKey(), textField.getText());
            textField.textProperty().addListener((obs, oldVal, newVal) -> updateRiskBadge(riskBadge, definition.getKey(), newVal));
            titleRow.getChildren().addAll(label, helpBadge, riskBadge);
            control = textField;
        }

        control.setMaxWidth(Double.MAX_VALUE);
        row.getChildren().addAll(titleRow, desc, hintLabel, control);
        controls.put(definition.getKey(), control);
        return row;
    }

    private Label createHelpBadge(String key) {
        Label helpBadge = new Label("?");
        helpBadge.setStyle(
                "-fx-font-size: 10px; "
                        + "-fx-font-weight: bold; "
                        + "-fx-text-fill: #2f6b99; "
                        + "-fx-background-color: #e8f2fb; "
                        + "-fx-background-radius: 999; "
                        + "-fx-padding: 2 6; "
                        + "-fx-cursor: hand;"
        );
        String helpText = getFieldHelpText(key);
        if (helpText != null && !helpText.trim().isEmpty()) {
            helpBadge.setTooltip(new Tooltip(helpText));
        }
        return helpBadge;
    }

    private Label createRiskBadge(String key, String value) {
        Label badge = new Label();
        badge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 999;");
        updateRiskBadge(badge, key, value);
        return badge;
    }

    private void updateRiskBadge(Label badge, String key, String value) {
        String[] state = evaluateFieldState(key, value);
        badge.setText(state[0]);
        badge.setStyle(
                "-fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 3 8; "
                        + "-fx-background-radius: 999; "
                        + "-fx-background-color: " + state[2] + "; "
                        + "-fx-text-fill: " + state[1] + ";"
        );
    }

    private String[] evaluateFieldState(String key, String value) {
        if ("speaker.matchThreshold".equals(key)) {
            double number = parseDouble(value, Double.NaN);
            if (Double.isNaN(number)) {
                return new String[]{"待填写", "#7f8c8d", "#eef2f6"};
            }
            if (number < 0.70) {
                return new String[]{"偏宽松", "#9a6700", "#fff3cd"};
            }
            if (number > 0.92) {
                return new String[]{"偏严格", "#8a4b08", "#fdebd0"};
            }
            return new String[]{"推荐", "#1e6b3a", "#d9f2e3"};
        }

        if ("vad.threshold".equals(key) || "speaker.clusteringThreshold".equals(key)) {
            double number = parseDouble(value, Double.NaN);
            if (Double.isNaN(number)) {
                return new String[]{"待填写", "#7f8c8d", "#eef2f6"};
            }
            if (number < 0.40 || number > 0.75) {
                return new String[]{"高敏感", "#8a4b08", "#fdebd0"};
            }
            if (number < 0.45 || number > 0.68) {
                return new String[]{"需留意", "#9a6700", "#fff3cd"};
            }
            return new String[]{"推荐", "#1e6b3a", "#d9f2e3"};
        }

        if ("vad.minSilenceDuration".equals(key) || "vad.minSpeechDuration".equals(key)
                || "speaker.minDurationOn".equals(key) || "speaker.minDurationOff".equals(key)) {
            double number = parseDouble(value, Double.NaN);
            if (Double.isNaN(number)) {
                return new String[]{"待填写", "#7f8c8d", "#eef2f6"};
            }
            if (number < 0.10 || number > 1.20) {
                return new String[]{"需留意", "#9a6700", "#fff3cd"};
            }
            return new String[]{"平衡", "#1e6b3a", "#d9f2e3"};
        }

        if ("conformer.hotwordsScore".equals(key)) {
            double number = parseDouble(value, Double.NaN);
            if (Double.isNaN(number)) {
                return new String[]{"待填写", "#7f8c8d", "#eef2f6"};
            }
            if (number >= 30) {
                return new String[]{"强化很高", "#7c2d12", "#fed7aa"};
            }
            if (number >= 15) {
                return new String[]{"强化较高", "#8a4b08", "#fdebd0"};
            }
            if (number < 5.0) {
                return new String[]{"强化偏弱", "#9a6700", "#fff3cd"};
            }
            return new String[]{"推荐", "#1e6b3a", "#d9f2e3"};
        }

        return new String[]{"常规", "#5b6570", "#eef2f6"};
    }

    private void reloadValues(Map<String, Control> controls) {
        try {
            configStore.load();
            for (ConfigFieldDefinition definition : configStore.getFieldDefinitions()) {
                setControlValue(controls.get(definition.getKey()), configStore.getValue(definition.getKey()));
            }
        } catch (Exception e) {
            showSimpleError(owner, "重新读取失败: " + e.getMessage());
        }
    }

    private void applyPreset(String presetId, Map<String, Control> controls) {
        Map<String, String> values = buildPresetValues(presetId);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            Control control = controls.get(entry.getKey());
            if (control != null) {
                setControlValue(control, entry.getValue());
            }
        }
    }

    private void applySectionDefaults(String section, Map<String, Control> controls) {
        for (ConfigFieldDefinition definition : configStore.getFieldDefinitions()) {
            if (!section.equals(definition.getSection())) {
                continue;
            }
            Control control = controls.get(definition.getKey());
            if (control != null) {
                setControlValue(control, configStore.getDefaultValue(definition.getKey()));
            }
        }
    }

    private Map<String, String> buildPresetValues(String presetId) {
        Map<String, String> values = new LinkedHashMap<String, String>();

        if (PRESET_DEFAULT.equals(presetId)) {
            values.put("vad.threshold", "0.55");
            values.put("vad.minSilenceDuration", "0.35");
            values.put("vad.minSpeechDuration", "0.6");
            values.put("vad.maxSpeechDuration", "5.0");
            values.put("speaker.clusteringThreshold", "0.5");
            values.put("speaker.minDurationOn", "0.3");
            values.put("speaker.minDurationOff", "0.6");
            values.put("speaker.matchThreshold", "0.82");
            values.put("conformer.hotwordsScore", "30.0");
            values.put("common.numThreads", "2");
            return values;
        }

        if (PRESET_QUIET.equals(presetId)) {
            values.put("vad.threshold", "0.58");
            values.put("vad.minSilenceDuration", "0.30");
            values.put("vad.minSpeechDuration", "0.45");
            values.put("vad.maxSpeechDuration", "6.0");
            values.put("speaker.clusteringThreshold", "0.52");
            values.put("speaker.minDurationOn", "0.25");
            values.put("speaker.minDurationOff", "0.55");
            values.put("speaker.matchThreshold", "0.84");
            values.put("conformer.hotwordsScore", "30.0");
            return values;
        }

        if (PRESET_DISCUSSION.equals(presetId)) {
            values.put("vad.threshold", "0.50");
            values.put("vad.minSilenceDuration", "0.22");
            values.put("vad.minSpeechDuration", "0.35");
            values.put("vad.maxSpeechDuration", "4.5");
            values.put("speaker.clusteringThreshold", "0.45");
            values.put("speaker.minDurationOn", "0.22");
            values.put("speaker.minDurationOff", "0.45");
            values.put("speaker.matchThreshold", "0.81");
            values.put("conformer.hotwordsScore", "30.0");
            return values;
        }

        if (PRESET_FAR_FIELD.equals(presetId)) {
            values.put("vad.threshold", "0.62");
            values.put("vad.minSilenceDuration", "0.45");
            values.put("vad.minSpeechDuration", "0.70");
            values.put("vad.maxSpeechDuration", "5.5");
            values.put("speaker.clusteringThreshold", "0.55");
            values.put("speaker.minDurationOn", "0.35");
            values.put("speaker.minDurationOff", "0.70");
            values.put("speaker.matchThreshold", "0.86");
            values.put("conformer.hotwordsScore", "30.0");
            return values;
        }

        return values;
    }

    private void validateControls(Map<String, Control> controls) {
        for (ConfigFieldDefinition definition : configStore.getFieldDefinitions()) {
            String value = readControlValue(controls.get(definition.getKey()));
            validateValue(definition, value);
        }
    }

    private void validateValue(ConfigFieldDefinition definition, String rawValue) {
        String key = definition.getKey();
        String value = rawValue == null ? "" : rawValue.trim();

        if (definition.getInputType() == ConfigFieldDefinition.InputType.FILE
                || definition.getInputType() == ConfigFieldDefinition.InputType.DIRECTORY) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException(definition.getLabel() + "不能为空。");
            }
            return;
        }

        if ("audio.inputFormats".equals(key)) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("允许导入的音频格式不能为空。");
            }
            String[] parts = value.split(",");
            for (String part : parts) {
                String item = part.trim();
                if (item.isEmpty() || !item.matches("[a-zA-Z0-9]+")) {
                    throw new IllegalArgumentException("音频格式请用英文逗号分隔，例如 wav,mp3,m4a。");
                }
            }
            return;
        }

        if (definition.getInputType() == ConfigFieldDefinition.InputType.SELECT) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException(definition.getLabel() + "不能为空。");
            }
            return;
        }

        if (isIntegerField(key)) {
            int intValue;
            try {
                intValue = Integer.parseInt(value);
            } catch (Exception e) {
                throw new IllegalArgumentException(definition.getLabel() + "必须填写整数。");
            }
            checkIntRange(key, definition.getLabel(), intValue);
            return;
        }

        if (isFloatField(key)) {
            double floatValue;
            try {
                floatValue = Double.parseDouble(value);
            } catch (Exception e) {
                throw new IllegalArgumentException(definition.getLabel() + "必须填写数字。");
            }
            checkDoubleRange(key, definition.getLabel(), floatValue);
        }
    }

    private boolean isIntegerField(String key) {
        return "common.numThreads".equals(key)
                || "common.sampleRate".equals(key)
                || "vad.windowSize".equals(key)
                || "conformer.maxActivePaths".equals(key);
    }

    private boolean isFloatField(String key) {
        return "conformer.hotwordsScore".equals(key)
                || "vad.threshold".equals(key)
                || "vad.minSilenceDuration".equals(key)
                || "vad.minSpeechDuration".equals(key)
                || "vad.maxSpeechDuration".equals(key)
                || "speaker.clusteringThreshold".equals(key)
                || "speaker.minDurationOn".equals(key)
                || "speaker.minDurationOff".equals(key)
                || "speaker.matchThreshold".equals(key);
    }

    private void checkIntRange(String key, String label, int value) {
        if ("common.numThreads".equals(key) && (value < 1 || value > 16)) {
            throw new IllegalArgumentException(label + "建议填写 1 到 16。");
        }
        if ("common.sampleRate".equals(key) && (value < 8000 || value > 48000)) {
            throw new IllegalArgumentException(label + "建议填写 8000 到 48000。");
        }
        if ("vad.windowSize".equals(key) && (value < 128 || value > 4096)) {
            throw new IllegalArgumentException(label + "建议填写 128 到 4096。");
        }
        if ("conformer.maxActivePaths".equals(key) && (value < 1 || value > 32)) {
            throw new IllegalArgumentException(label + "建议填写 1 到 32。");
        }
    }

    private void checkDoubleRange(String key, String label, double value) {
        if ("vad.threshold".equals(key) && (value <= 0 || value >= 1)) {
            throw new IllegalArgumentException(label + "建议填写 0 到 1 之间的小数。");
        }
        if ("speaker.clusteringThreshold".equals(key) && (value <= 0 || value >= 1)) {
            throw new IllegalArgumentException(label + "建议填写 0 到 1 之间的小数。");
        }
        if ("speaker.matchThreshold".equals(key) && (value <= 0 || value >= 1)) {
            throw new IllegalArgumentException(label + "建议填写 0 到 1 之间的小数。");
        }
        if ("vad.minSilenceDuration".equals(key) && (value < 0.05 || value > 3)) {
            throw new IllegalArgumentException(label + "建议填写 0.05 到 3 秒。");
        }
        if ("vad.minSpeechDuration".equals(key) && (value < 0.05 || value > 5)) {
            throw new IllegalArgumentException(label + "建议填写 0.05 到 5 秒。");
        }
        if ("vad.maxSpeechDuration".equals(key) && (value < 1 || value > 60)) {
            throw new IllegalArgumentException(label + "建议填写 1 到 60 秒。");
        }
        if ("speaker.minDurationOn".equals(key) && (value < 0.05 || value > 5)) {
            throw new IllegalArgumentException(label + "建议填写 0.05 到 5 秒。");
        }
        if ("speaker.minDurationOff".equals(key) && (value < 0.05 || value > 5)) {
            throw new IllegalArgumentException(label + "建议填写 0.05 到 5 秒。");
        }
        if ("conformer.hotwordsScore".equals(key) && (value < 0 || value > 50)) {
            throw new IllegalArgumentException(label + "建议填写 0 到 50。");
        }
    }

    private String getRangeHint(String key) {
        if ("common.numThreads".equals(key)) {
            return "1 到 16，常用 2 到 4";
        }
        if ("common.sampleRate".equals(key)) {
            return "8000 到 48000，常用 16000";
        }
        if ("vad.windowSize".equals(key)) {
            return "128 到 4096，常用 512";
        }
        if ("conformer.maxActivePaths".equals(key)) {
            return "1 到 32，常用 20";
        }
        if ("speaker.matchThreshold".equals(key)) {
            return "0 到 1，常用 0.80 到 0.88";
        }
        if ("vad.threshold".equals(key) || "speaker.clusteringThreshold".equals(key)) {
            return "0 到 1，常用 0.45 到 0.68";
        }
        if ("vad.minSilenceDuration".equals(key) || "vad.minSpeechDuration".equals(key)
                || "speaker.minDurationOn".equals(key) || "speaker.minDurationOff".equals(key)) {
            return "0.05 到 5 秒";
        }
        if ("vad.maxSpeechDuration".equals(key)) {
            return "1 到 60 秒，常用 4.5 到 6";
        }
        if ("conformer.hotwordsScore".equals(key)) {
            return "0 到 50，常用 20 到 30";
        }
        if ("audio.inputFormats".equals(key)) {
            return "用英文逗号分隔，例如 wav,mp3,m4a";
        }
        return "";
    }

    private String getFieldHelpText(String key) {
        if ("vad.threshold".equals(key)) {
            return "如果系统经常把空调声、纸张声当成讲话，可以把这个值调高一点；如果经常漏掉很轻的发言，可以调低一点。";
        }
        if ("vad.minSilenceDuration".equals(key)) {
            return "如果一句话中间稍微停顿就被切成很多段，可以把这个值调大一点。";
        }
        if ("vad.minSpeechDuration".equals(key)) {
            return "如果很多很短的杂音也进入了识别，可以把这个值调大一点。";
        }
        if ("speaker.clusteringThreshold".equals(key)) {
            return "如果不同人总被合并成同一个人，可以调低一点；如果同一个人总被拆成多个人，可以调高一点。";
        }
        if ("speaker.matchThreshold".equals(key)) {
            return "这个值会影响右侧“智能替换”和识别阶段的声纹库匹配。想少匹配错人就调高一点，想更容易给出候选人就调低一点。";
        }
        if ("conformer.hotwordsScore".equals(key)) {
            return "如果你希望项目名、人名、术语更容易被识别出来，可以适当调高；默认 30 偏强，过高可能会让普通词被误带偏。";
        }
        if ("audio.autoConvertWithFfmpeg".equals(key)) {
            return "手机录音常见的 aac、m4a、mp3 建议开启这个选项，系统会先转成更稳定的标准 wav 再处理。";
        }
        if ("audio.inputFormats".equals(key)) {
            return "这里填的是允许用户导入的文件后缀，例如 wav,mp3,m4a,aac。";
        }
        if ("common.numThreads".equals(key)) {
            return "电脑性能一般时保持 2 到 4 就够了，设太高不一定更快。";
        }
        return "这个参数会影响识别效果，建议先用场景推荐值，再根据实际结果少量微调。";
    }

    private String getExampleValue(String key) {
        if ("audio.inputFormats".equals(key)) {
            return "wav,mp3,m4a,aac";
        }
        if ("common.numThreads".equals(key)) {
            return "例如 2";
        }
        if ("vad.threshold".equals(key)) {
            return "例如 0.55";
        }
        if ("speaker.clusteringThreshold".equals(key)) {
            return "例如 0.50";
        }
        if ("speaker.matchThreshold".equals(key)) {
            return "例如 0.82";
        }
        if ("conformer.hotwordsScore".equals(key)) {
            return "例如 30.0";
        }
        return "";
    }

    private double parseDouble(String raw, double defaultValue) {
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void setControlValue(Control control, String value) {
        if (control instanceof TextField) {
            ((TextField) control).setText(value);
        } else if (control instanceof ComboBox<?>) {
            @SuppressWarnings("unchecked")
            ComboBox<String> comboBox = (ComboBox<String>) control;
            comboBox.setValue(value);
        }
    }

    private String readControlValue(Control control) {
        if (control instanceof TextField) {
            return ((TextField) control).getText();
        }
        if (control instanceof ComboBox<?>) {
            Object value = ((ComboBox<?>) control).getValue();
            return value == null ? "" : value.toString();
        }
        return "";
    }

    private void persistControls(Map<String, Control> controls) {
        for (ConfigFieldDefinition definition : configStore.getFieldDefinitions()) {
            configStore.setValue(definition.getKey(), readControlValue(controls.get(definition.getKey())));
        }
    }

    private boolean hasControlChanges(Map<String, Control> controls) {
        for (ConfigFieldDefinition definition : configStore.getFieldDefinitions()) {
            String currentValue = configStore.getValue(definition.getKey());
            String newValue = readControlValue(controls.get(definition.getKey()));
            if (currentValue == null) {
                currentValue = "";
            }
            if (newValue == null) {
                newValue = "";
            }
            if (!currentValue.equals(newValue)) {
                return true;
            }
        }
        return false;
    }

    private String getSectionDescription(String section) {
        if ("基础运行设置".equals(section)) {
            return "这里是整体运行方式，例如处理速度、采样率和推理设备。多数情况下保持默认即可。";
        }
        if ("音频导入设置".equals(section)) {
            return "这里决定系统允许导入哪些音频，以及导入时是否自动转成标准音频。";
        }
        if ("普通识别设置".equals(section)) {
            return "不启用关键词增强时，系统会走这一组识别模型。适合常规会议转写。";
        }
        if ("关键词增强识别".equals(section)) {
            return "当主界面勾选“关键词增强”时，会使用这里的模型和参数。适合项目名、人名、专业词较多的场景。";
        }
        if ("静音分段设置".equals(section)) {
            return "这里控制系统如何区分讲话和停顿，以及怎样把长音频切成一段一段。";
        }
        if ("多人发言区分与库匹配".equals(section)) {
            return "这里控制不同发言人的区分效果，以及和发言人库进行匹配的严格程度。";
        }
        return "这一组参数会影响识别流程，可根据业务场景逐步调整。";
    }

    private void showSimpleError(Window window, String message) {
        showSimpleMessage(window, "提示", message, "#e74c3c");
    }

    private void showSimpleInfo(Window window, String message) {
        showSimpleMessage(window, "提示", message, "#27ae60");
    }

    private void showSimpleMessage(Window window, String title, String message, String buttonColor) {
        Stage errorStage = new Stage();
        errorStage.initModality(Modality.APPLICATION_MODAL);
        if (window != null) {
            errorStage.initOwner(window);
        }
        errorStage.setTitle(title);

        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #2c3e50;");

        Button closeBtn = new Button("关闭");
        closeBtn.setStyle(getButtonStyle(buttonColor));
        closeBtn.setOnAction(e -> errorStage.close());

        VBox root = new VBox(12, messageLabel, closeBtn);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: white;");

        errorStage.setScene(new Scene(root, 440, 180));
        errorStage.showAndWait();
    }

    private String getButtonStyle(String color) {
        return "-fx-background-color: " + color + "; "
                + "-fx-text-fill: white; "
                + "-fx-padding: 10 20; "
                + "-fx-font-size: 14px; "
                + "-fx-font-weight: bold; "
                + "-fx-background-radius: 4; "
                + "-fx-border-radius: 4; "
                + "-fx-cursor: hand;";
    }
}
