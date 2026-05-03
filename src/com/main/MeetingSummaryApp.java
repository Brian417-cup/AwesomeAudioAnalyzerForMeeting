package com.main;

import com.controller.AudioPlayerController;
import com.controller.RecognitionBackendController;
import com.controller.VoicePrintLibraryController;
import com.gui.SherpaConfigDialog;
import com.gui.VoicePrintSidePanel;
import com.gui.WaveformCanvas;
import com.model.SpeechRecognitionUnit;
import com.recognition.AudioSourceResolver;
import com.recognition.CacheMaintenanceService;
import com.recognition.SherpaOnnxConfigStore;
import com.recognition.VoicePrintSmartMatchService;
import com.resource.FrontProfile;
import com.search.RecognitionSearchStore;
import com.util.AudioCutter;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.scene.text.Text;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MeetingSummaryApp extends Application {

    private static final int CONTENT_PREVIEW_MAX_LINES = 10;
    private static final double CONTENT_LINE_HEIGHT = 18.0;
    private static final int DEFAULT_RECOGNITION_PAGE_SIZE = 20;

    private enum RightPanelKind {
        NONE,
        VOICE_PRINT_LIBRARY,
        RESULT_EDITOR
    }

    private RecognitionBackendController recognitionBackendController;
    private final AudioSourceResolver audioSourceResolver = new AudioSourceResolver();
    private final VoicePrintSmartMatchService voicePrintSmartMatchService = new VoicePrintSmartMatchService();
    private final RecognitionSearchStore recognitionSearchStore = new RecognitionSearchStore();
    private final ObservableList<SpeechRecognitionUnit> recognitionList = FXCollections.observableArrayList();
    private final ObservableList<SpeechRecognitionUnit> displayList = FXCollections.observableArrayList();
    private final List<SpeechRecognitionUnit> filteredRecognitionList = new ArrayList<>();
    private SherpaOnnxConfigStore configStore;

    private Button loadBtn;
    private Button startBtn;
    private Button exportBtn;
    private Label filePathLabel;
    private Label statusLabel;
    private ProgressBar progressBar;
    private TextField searchField;

    private CheckBox hotWordsCheckBox;
    private TextField hotWordsPathField;
    private Button browseHotWordsBtn;
    private Button editHotWordsBtn;
    private CheckBox voicePrintCheckBox;
    private CheckBox fixedSpeakerCountCheckBox;
    private TextField speakerCountField;
    private Button voicePrintLibBtn;
    private Button liveVoicePrintBtn;

    private File currentAudioFile;
    private File hotWordsFile;
    private boolean isSidePanelOpen = false;
    private RightPanelKind currentRightPanelKind = RightPanelKind.NONE;

    private StackPane hostLayer;
    private StackPane sidePanelLayer;
    private BorderPane mainContent;
    private ListView<SpeechRecognitionUnit> listView;
    private Button recognitionPrevPageBtn;
    private Button recognitionNextPageBtn;
    private Label recognitionPageLabel;
    private ComboBox<Integer> recognitionPageSizeBox;
    private int recognitionPageIndex = 0;
    private int recognitionPageSize = DEFAULT_RECOGNITION_PAGE_SIZE;
    private VBox recognitionEditPanel;
    private TextField editSpeakerField;
    private TextArea editContentArea;
    private Label editTargetLabel;
    private Label editTimeLabel;
    private Button smartReplaceDialogBtn;
    private SpeechRecognitionUnit editingUnit;

    // 主界面下方联动波形与播放控制
    private WaveformCanvas previewWaveformCanvas;
    private Slider previewProgressSlider;
    private Label previewTimeLabel;
    private Button previewPlayBtn;
    private Button previewPauseBtn;
    private Button previewBackwardBtn;
    private Button previewForwardBtn;
    private TextField previewJumpField;
    private File previewAudioFile;

    private Clip previewClip;
    private ScheduledExecutorService previewScheduler;
    private boolean previewSchedulerStarted = false;
    private SpeechRecognitionUnit translatingUnit;
    private boolean suppressSelectionSeek = false;

    @Override
    public void start(Stage primaryStage) {
        recognitionBackendController = new RecognitionBackendController();
        reloadConfigStore(false);
        initUI(primaryStage);
        if (configStore == null) {
            Platform.runLater(() -> showError("配置文件加载失败，请检查 sherpa_onnx.properties 是否存在且内容有效。"));
        }
    }

    @Override
    public void stop() {
        cleanupPreviewPlayer();
    }

    private void initUI(Stage stage) {
        hostLayer = new StackPane();
        hostLayer.setStyle("-fx-background-color: #f5f6fa;");
        mainContent = createMainContent();
        sidePanelLayer = new StackPane();
        sidePanelLayer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        sidePanelLayer.setPickOnBounds(false);
        sidePanelLayer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        StackPane.setAlignment(sidePanelLayer, javafx.geometry.Pos.CENTER);
        hostLayer.getChildren().addAll(mainContent, sidePanelLayer);

        Scene scene = new Scene(hostLayer, 1180, 820);
        scene.widthProperty().addListener((obs, oldVal, newVal) -> {
            adjustOpenSidePanelWidth();
            updatePreviewWidthFromScene();
        });
        stage.setTitle("会议总结助手 v1.0");
        stage.setScene(scene);
        stage.show();
        Platform.runLater(this::updatePreviewWidthFromScene);
    }

    private BorderPane createMainContent() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #f5f6fa;");

        root.setTop(createTopArea());
        root.setCenter(new CenterPanel());
        root.setBottom(createStatusBar());

        return root;
    }

    private VBox createTopArea() {
        VBox topArea = new VBox(8);
        topArea.getChildren().addAll(createMenuBar(), createTopToolbar());
        return topArea;
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        menuBar.setStyle(
                "-fx-background-color: #ffffff; " +
                        "-fx-border-color: #e0e0e0; " +
                        "-fx-border-width: 0 0 1 0; " +
                        "-fx-padding: 2 0 2 0;"
        );

        Menu systemMenu = new Menu("系统");
        systemMenu.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        MenuItem configMenuItem = new MenuItem("识别配置");
        configMenuItem.setOnAction(e -> openConfigDialog());

        MenuItem reloadMenuItem = new MenuItem("重新加载配置");
        reloadMenuItem.setOnAction(e -> reloadConfigStore(true));

        MenuItem aboutMenuItem = new MenuItem("关于");
        aboutMenuItem.setOnAction(e -> showInfo("关于系统", FrontProfile.ABOUT_DESCRIPTION));

        systemMenu.getItems().addAll(configMenuItem, reloadMenuItem, new SeparatorMenuItem(), aboutMenuItem);

        Menu cacheMenu = new Menu("缓存管理");
        cacheMenu.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        MenuItem clearIntermediateMenuItem = new MenuItem("清空中转文件");
        clearIntermediateMenuItem.setOnAction(e -> clearCacheByType(
                "清空中转文件",
                "这会删除音频转换、波形预览、匹配过程等中间文件，但不会删除原始音频。是否继续？",
                CacheClearType.INTERMEDIATE
        ));

        MenuItem clearRecognitionDbMenuItem = new MenuItem("清空转译结果数据库");
        clearRecognitionDbMenuItem.setOnAction(e -> clearCacheByType(
                "清空转译结果数据库",
                "这会删除当前用于结果搜索和检索的数据库内容。是否继续？",
                CacheClearType.RECOGNITION_DATABASE
        ));

        MenuItem clearVoicePrintMenuItem = new MenuItem("清空声纹库文件");
        clearVoicePrintMenuItem.setOnAction(e -> clearCacheByType(
                "清空声纹库文件",
                "这会删除声纹库数据库和已注册的声纹音频文件。这个操作不可撤销，是否继续？",
                CacheClearType.VOICE_PRINT_LIBRARY
        ));

        MenuItem resetHotWordsMenuItem = new MenuItem("重置热词词表");
        resetHotWordsMenuItem.setOnAction(e -> clearCacheByType(
                "重置热词词表",
                "这会清空当前热词词表目录，并恢复为系统默认样例内容。是否继续？",
                CacheClearType.HOTWORDS
        ));

        MenuItem clearAllCacheMenuItem = new MenuItem("清空全部缓存");
        clearAllCacheMenuItem.setOnAction(e -> clearCacheByType(
                "清空全部缓存",
                "这会一次性清空中转文件、转译结果数据库、声纹库文件，并把热词词表重置为默认样例。这个操作不可撤销，是否继续？",
                CacheClearType.ALL
        ));

        cacheMenu.getItems().addAll(
                clearIntermediateMenuItem,
                clearRecognitionDbMenuItem,
                clearVoicePrintMenuItem,
                resetHotWordsMenuItem,
                new SeparatorMenuItem(),
                clearAllCacheMenuItem
        );

        menuBar.getMenus().addAll(systemMenu, cacheMenu);
        return menuBar;
    }

    private VBox createTopToolbar() {
        VBox toolbar = new VBox(12);
        toolbar.setPadding(new Insets(0, 0, 15, 0));

        HBox fileBox = new HBox(10);
        fileBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        loadBtn = new Button("加载音频");
        styleButton(loadBtn, "#3498db");
        loadBtn.setTooltip(new Tooltip("选择要识别的会议录音，支持配置中列出的音频格式。"));
        loadBtn.setOnMouseClicked(this::loadAudioFile);

        startBtn = new Button("开始识别");
        styleButton(startBtn, "#27ae60");
        startBtn.setDisable(true);
        startBtn.setTooltip(new Tooltip("使用当前音频、热词和发言人设置开始识别。"));
        startBtn.setOnMouseClicked(e -> startRecognition());

        exportBtn = new Button("导出结果");
        styleButton(exportBtn, "#f39c12");
        exportBtn.setDisable(true);
        exportBtn.setTooltip(new Tooltip("导出当前已修正的识别结果。"));
        exportBtn.setOnMouseClicked(e -> exportResults());

        voicePrintLibBtn = new Button("声纹库");
        styleButton(voicePrintLibBtn, "#8e44ad");
        voicePrintLibBtn.setTooltip(new Tooltip("打开右侧声纹库，管理已注册的用户声纹。"));
        voicePrintLibBtn.setOnAction(e -> toggleSidePanel());

        liveVoicePrintBtn = new Button("现场声纹制作");
        styleButton(liveVoicePrintBtn, "#16a085");
        liveVoicePrintBtn.setDisable(true);
        liveVoicePrintBtn.setTooltip(new Tooltip("从当前音频中可视化剪切一段声音并注册为声纹。"));
        liveVoicePrintBtn.setOnAction(e -> openLiveVoicePrintDialog());

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        filePathLabel = new Label("未选择文件");
        filePathLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 13px;");
        filePathLabel.setPrefWidth(420);
        filePathLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        filePathLabel.setTooltip(new Tooltip("尚未选择音频文件"));

        fileBox.getChildren().addAll(
                loadBtn,
                startBtn,
                exportBtn,
                voicePrintLibBtn,
                liveVoicePrintBtn,
                spacer,
                filePathLabel
        );

        HBox optionBox = new HBox(15);
        optionBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        optionBox.setPadding(new Insets(8, 0, 0, 0));

        hotWordsCheckBox = new CheckBox("关键词增强");
        hotWordsCheckBox.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        hotWordsCheckBox.setTooltip(new Tooltip("适合有专有名词、项目名、人名时使用，可提高这些关键词的识别概率"));
        hotWordsCheckBox.setDisable(true);
        hotWordsCheckBox.setOnAction(e -> handleHotWordsToggle());

        hotWordsPathField = new TextField();
        hotWordsPathField.setPromptText("关键词词表文件路径(.txt)");
        hotWordsPathField.setEditable(false);
        hotWordsPathField.setPrefWidth(320);
        hotWordsPathField.setStyle(getInputStyle());
        hotWordsPathField.setDisable(true);

        browseHotWordsBtn = new Button("导入词表");
        styleButton(browseHotWordsBtn, "#9b59b6");
        browseHotWordsBtn.setDisable(true);
        browseHotWordsBtn.setTooltip(new Tooltip("导入外部热词文本文件，系统会复制到受控临时目录中。"));
        browseHotWordsBtn.setOnMouseClicked(e -> browseHotWordsFile());

        editHotWordsBtn = new Button("编辑词表");
        styleButton(editHotWordsBtn, "#2980b9");
        editHotWordsBtn.setDisable(true);
        editHotWordsBtn.setTooltip(new Tooltip("打开当前热词文件，直接编辑每行一个热词或短语。"));
        editHotWordsBtn.setOnAction(e -> openHotWordsEditor());

        voicePrintCheckBox = new CheckBox("识别已知发言人");
        voicePrintCheckBox.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        voicePrintCheckBox.setTooltip(new Tooltip("将当前讲话内容与发言人库比对，尽量识别出是谁在说话"));
        voicePrintCheckBox.setDisable(true);
        voicePrintCheckBox.setOnAction(e -> {
            if (voicePrintCheckBox.isSelected()) {
                openSidePanel();
            } else {
                closeSidePanel();
            }
        });

        Label speakerCountLabel = new Label("人数:");
        speakerCountLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        fixedSpeakerCountCheckBox = new CheckBox("固定说话人数");
        fixedSpeakerCountCheckBox.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        fixedSpeakerCountCheckBox.setTooltip(new Tooltip("如果你确定现场有几个人发言，可固定人数；不勾选则由系统自动判断"));
        fixedSpeakerCountCheckBox.setDisable(true);
        fixedSpeakerCountCheckBox.setSelected(false);
        fixedSpeakerCountCheckBox.setOnAction(e -> {
            boolean enabled = fixedSpeakerCountCheckBox.isSelected() && !fixedSpeakerCountCheckBox.isDisable();
            speakerCountField.setDisable(!enabled);
        });

        speakerCountField = new TextField("1");
        speakerCountField.setPrefWidth(60);
        speakerCountField.setStyle(getInputStyle());
        speakerCountField.setTooltip(new Tooltip("勾选固定说话人数后，这里填写预计发言人数。"));
        speakerCountField.setDisable(true);

        optionBox.getChildren().addAll(
                hotWordsCheckBox,
                hotWordsPathField,
                browseHotWordsBtn,
                editHotWordsBtn,
                new Separator(),
                voicePrintCheckBox,
                fixedSpeakerCountCheckBox,
                speakerCountLabel,
                speakerCountField
        );

        HBox searchBox = new HBox(10);
        searchBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label searchIcon = new Label("搜索");
        searchIcon.setStyle("-fx-font-size: 15px;");

        searchField = new TextField();
        searchField.setPromptText("输入说话人名称或关键词筛选识别结果...");
        searchField.setTooltip(new Tooltip("可按说话人名称或识别内容筛选结果，分页会自动重算。"));
        searchField.setPrefWidth(650);
        searchField.setStyle(getInputStyle());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> refreshDisplayList());

        Button clearBtn = new Button("清空");
        styleButton(clearBtn, "#95a5a6");
        clearBtn.setTooltip(new Tooltip("清空搜索条件，恢复显示全部识别片段。"));
        clearBtn.setOnMouseClicked(e -> {
            searchField.clear();
            refreshDisplayList();
        });

        searchBox.getChildren().addAll(searchIcon, searchField, clearBtn);
        toolbar.getChildren().addAll(fileBox, optionBox, searchBox);
        return toolbar;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(12, 0, 0, 0));
        statusBar.setStyle("-fx-border-color: #bdc3c7; -fx-border-width: 1 0 0 0; -fx-padding: 10 0 0 0; -fx-background-color: transparent;");

        statusLabel = new Label("就绪");
        statusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 13px;");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(420);
        progressBar.setVisible(false);

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label countLabel = new Label("共 0 条");
        countLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 12px;");

        recognitionList.addListener((javafx.collections.ListChangeListener.Change<? extends SpeechRecognitionUnit> c) -> {
            countLabel.setText("共 " + recognitionList.size() + " 条");
        });

        statusBar.getChildren().addAll(statusLabel, progressBar, spacer, countLabel);
        return statusBar;
    }

    private void loadAudioFile(MouseEvent event) {
        try {
            FileChooser chooser = new FileChooser();
            configureAudioChooser(chooser, "选择音频文件");

            File file = chooser.showOpenDialog(loadBtn.getScene().getWindow());
            if (file != null) {
                currentAudioFile = file;
                filePathLabel.setText(file.getAbsolutePath());
                filePathLabel.setTooltip(new Tooltip(file.getAbsolutePath()));
                startBtn.setDisable(false);
                hotWordsCheckBox.setDisable(false);
                updateHotWordsControlState(hotWordsCheckBox.isSelected());
                voicePrintCheckBox.setDisable(false);
                fixedSpeakerCountCheckBox.setDisable(false);
                speakerCountField.setDisable(!fixedSpeakerCountCheckBox.isSelected());
                liveVoicePrintBtn.setDisable(false);
                exportBtn.setDisable(true);

                recognitionList.clear();
                displayList.clear();
                if (searchField != null) {
                    searchField.clear();
                }

                preparePreviewAudio(file);
                statusLabel.setText("已加载文件: " + file.getName());
            }
        } catch (Exception e) {
            showError("加载音频失败: " + e.getMessage());
        }
    }

    private void handleHotWordsToggle() {
        boolean enabled = hotWordsCheckBox.isSelected();
        updateHotWordsControlState(enabled);

        if (!enabled) {
            return;
        }

        try {
            if (hotWordsFile == null || !hotWordsFile.exists()) {
                applyHotWordsFile(ensureDefaultHotWordsFile());
            } else {
                applyHotWordsFile(hotWordsFile);
            }
        } catch (Exception e) {
            hotWordsCheckBox.setSelected(false);
            updateHotWordsControlState(false);
            showError("初始化热词文件失败: " + e.getMessage());
        }
    }

    private void browseHotWordsFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导入热词文件");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("文本文件", "*.txt")
        );

        File file = chooser.showOpenDialog(browseHotWordsBtn.getScene().getWindow());
        if (file != null) {
            try {
                applyHotWordsFile(importHotWordsFile(file));
                hotWordsCheckBox.setSelected(true);
                updateHotWordsControlState(true);
                showInfo("导入成功", "热词词表已导入到临时目录，后续可以直接在系统里继续编辑。");
            } catch (Exception e) {
                showError("导入热词文件失败: " + e.getMessage());
            }
        }
    }

    private void openHotWordsEditor() {
        try {
            if (hotWordsFile == null || !hotWordsFile.exists()) {
                applyHotWordsFile(ensureDefaultHotWordsFile());
            }
        } catch (Exception e) {
            showError("打开热词编辑器失败: " + e.getMessage());
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (hostLayer != null && hostLayer.getScene() != null) {
            dialog.initOwner(hostLayer.getScene().getWindow());
        }
        dialog.setTitle("编辑热词词表");

        Label introLabel = new Label(
                "这里是当前正在使用的热词词表。建议每行只填写一个热词或短语，尽量使用完整名称，不建议在这里额外写单独分值。"
        );
        introLabel.setWrapText(true);
        introLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #5f6b7a;");

        Label pathLabel = new Label(hotWordsFile.getAbsolutePath());
        pathLabel.setWrapText(true);
        pathLabel.setTooltip(new Tooltip(hotWordsFile.getAbsolutePath()));
        pathLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #7f8c8d;");

        TextArea editorArea = new TextArea();
        editorArea.setWrapText(true);
        editorArea.setStyle("-fx-font-size: 13px; -fx-font-family: 'Microsoft YaHei UI';");
        try {
            editorArea.setText(readTextFile(hotWordsFile));
        } catch (Exception e) {
            showError("读取热词词表失败: " + e.getMessage());
            return;
        }

        Button resetTemplateBtn = new Button("恢复样例内容");
        styleButton(resetTemplateBtn, "#7f8c8d");
        resetTemplateBtn.setOnAction(e -> editorArea.setText(buildDefaultHotWordsTemplate()));

        Button saveBtn = new Button("保存");
        styleButton(saveBtn, "#27ae60");
        saveBtn.setOnAction(e -> {
            try {
                writeTextFile(hotWordsFile, editorArea.getText());
                dialog.close();
                showInfo("保存成功", "热词词表已保存，当前识别会使用更新后的内容。");
            } catch (Exception ex) {
                showError("保存热词词表失败: " + ex.getMessage());
            }
        });

        Button cancelBtn = new Button("取消");
        styleButton(cancelBtn, "#95a5a6");
        cancelBtn.setOnAction(e -> dialog.close());

        HBox buttonBar = new HBox(10, resetTemplateBtn, saveBtn, cancelBtn);
        buttonBar.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        VBox root = new VBox(12, introLabel, pathLabel, editorArea, buttonBar);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: #f7f9fc;");
        VBox.setVgrow(editorArea, Priority.ALWAYS);

        dialog.setScene(new Scene(root, 760, 520));
        dialog.showAndWait();
    }

    private void updateHotWordsControlState(boolean enabled) {
        hotWordsPathField.setDisable(!enabled);
        browseHotWordsBtn.setDisable(!enabled);
        if (editHotWordsBtn != null) {
            editHotWordsBtn.setDisable(!enabled);
        }
    }

    private void applyHotWordsFile(File file) {
        hotWordsFile = file;
        if (hotWordsPathField != null) {
            hotWordsPathField.setText(file == null ? "" : file.getAbsolutePath());
            hotWordsPathField.setTooltip(file == null ? null : new Tooltip(file.getAbsolutePath()));
        }
    }

    private File ensureDefaultHotWordsFile() throws Exception {
        SherpaOnnxConfigStore store = configStore != null ? configStore : SherpaOnnxConfigStore.loadDefaultStore();
        File hotWordsDir = store.getAudioTempSubDirectory("hotwords");
        if (!hotWordsDir.exists() && !hotWordsDir.mkdirs()) {
            throw new IOException("无法创建热词目录: " + hotWordsDir.getAbsolutePath());
        }
        File defaultFile = new File(hotWordsDir, "default_hotwords.txt");
        if (!defaultFile.exists() || defaultFile.length() == 0L) {
            writeTextFile(defaultFile, buildDefaultHotWordsTemplate());
        }
        return defaultFile;
    }

    private File importHotWordsFile(File sourceFile) throws Exception {
        SherpaOnnxConfigStore store = configStore != null ? configStore : SherpaOnnxConfigStore.loadDefaultStore();
        File importDir = store.getAudioTempSubDirectory("hotwords/imported");
        if (!importDir.exists() && !importDir.mkdirs()) {
            throw new IOException("无法创建热词导入目录: " + importDir.getAbsolutePath());
        }
        String importedName = "imported_" + System.currentTimeMillis() + "_" + sanitizeFileName(sourceFile.getName());
        if (!importedName.toLowerCase().endsWith(".txt")) {
            importedName = importedName + ".txt";
        }
        File targetFile = new File(importDir, importedName);
        Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return targetFile;
    }

    private String readTextFile(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void writeTextFile(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建目录: " + parent.getAbsolutePath());
        }
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            writer.write(content == null ? "" : content);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private String buildDefaultHotWordsTemplate() {
        return "武汉大学\n"
                + "武大\n"
                + "小米科技园\n"
                + "国家会议中心\n"
                + "雷军\n"
                + "小米\n";
    }

    private String sanitizeFileName(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return "hotwords.txt";
        }
        return rawName.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private void startRecognition() {
        if (currentAudioFile == null) {
            showError("请先选择音频文件");
            return;
        }

        if (searchField != null) {
            searchField.clear();
        }
        recognitionList.clear();
        displayList.clear();
        translatingUnit = null;

        startBtn.setDisable(true);
        startBtn.setText("识别中...");
        exportBtn.setDisable(true);
        progressBar.setVisible(true);

        boolean useHotWords = hotWordsCheckBox.isSelected();
        if (useHotWords) {
            try {
                if (hotWordsFile == null || !hotWordsFile.exists()) {
                    applyHotWordsFile(ensureDefaultHotWordsFile());
                }
            } catch (Exception e) {
                startBtn.setDisable(false);
                startBtn.setText("开始识别");
                progressBar.setVisible(false);
                showError("热词文件准备失败: " + e.getMessage());
                return;
            }
        }
        boolean useVoicePrint = voicePrintCheckBox.isSelected();
        boolean useFixedSpeakerCount = fixedSpeakerCountCheckBox != null && fixedSpeakerCountCheckBox.isSelected();
        int speakerCount = parseSpeakerCount();
        final int[] detectedCount = new int[]{0};
        final int[] completedCount = new int[]{0};
        final int[] displayedProgress = new int[]{0};

        recognitionBackendController.recognizeWithSettings(
                currentAudioFile,
                useHotWords,
                hotWordsFile,
                useVoicePrint,
                useFixedSpeakerCount,
                speakerCount,
                new RecognitionBackendController.RecognitionCallback() {
                    @Override
                    public void onProgress(int progress) {
                        Platform.runLater(() -> {
                            displayedProgress[0] = Math.max(displayedProgress[0], progress);
                            progressBar.setProgress(displayedProgress[0] / 100.0);
                            if (completedCount[0] > 0 || detectedCount[0] > 0) {
                                statusLabel.setText(buildRecognitionProgressText(displayedProgress[0], detectedCount[0], completedCount[0]));
                            } else {
                                statusLabel.setText("转译进度: " + displayedProgress[0] + "%");
                            }
                        });
                    }

                    @Override
                    public void onSegmentDetected(SpeechRecognitionUnit unit, int index) {
                        Platform.runLater(() -> {
                            detectedCount[0] = Math.max(detectedCount[0], index + 1);
                            recognitionList.add(unit);
                            if (translatingUnit == null) {
                                translatingUnit = unit;
                            }
                            refreshDisplayList();

                            if (translatingUnit != null) {
                                selectRecognitionUnit(translatingUnit, true, false);
                            }
                            displayedProgress[0] = Math.max(displayedProgress[0], Math.min(82, 55 + detectedCount[0] * 2));
                            progressBar.setProgress(displayedProgress[0] / 100.0);
                            listView.refresh();
                            statusLabel.setText(buildRecognitionProgressText(displayedProgress[0], detectedCount[0], completedCount[0]));
                        });
                    }

                    @Override
                    public void onSegmentReady(SpeechRecognitionUnit unit, int index) {
                        Platform.runLater(() -> {
                            completedCount[0] = Math.max(completedCount[0], index + 1);
                            if (!recognitionList.contains(unit)) {
                                recognitionList.add(unit);
                            }
                            refreshDisplayList();

                            SpeechRecognitionUnit nextUnit = null;
                            if (index + 1 < recognitionList.size()) {
                                nextUnit = recognitionList.get(index + 1);
                            }
                            translatingUnit = nextUnit;

                            SpeechRecognitionUnit focusUnit = nextUnit != null ? nextUnit : unit;
                            if (focusUnit != null) {
                                selectRecognitionUnit(focusUnit, true, false);
                            }

                            displayedProgress[0] = Math.max(displayedProgress[0], Math.min(96, 58 + completedCount[0] * 4));
                            progressBar.setProgress(displayedProgress[0] / 100.0);
                            statusLabel.setText(buildRecognitionProgressText(displayedProgress[0], detectedCount[0], completedCount[0]));
                            listView.refresh();
                        });
                    }

                    @Override
                    public void onSuccess(List<SpeechRecognitionUnit> result) {
                        Platform.runLater(() -> {
                            if (recognitionList.isEmpty() && result != null) {
                                recognitionList.addAll(result);
                                refreshDisplayList();
                            }
                            startBtn.setDisable(false);
                            startBtn.setText("重新识别");
                            exportBtn.setDisable(recognitionList.isEmpty());
                            progressBar.setVisible(false);
                            translatingUnit = null;
                            listView.refresh();
                            statusLabel.setText("识别完成，共 " + recognitionList.size() + " 个语音片段");

                            if (!recognitionList.isEmpty()) {
                                SpeechRecognitionUnit selected = listView.getSelectionModel().getSelectedItem();
                                if (selected == null) {
                                    SpeechRecognitionUnit first = recognitionList.get(0);
                                    selectRecognitionUnit(first, true, false);
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        Platform.runLater(() -> {
                            startBtn.setDisable(false);
                            startBtn.setText("开始识别");
                            progressBar.setVisible(false);
                            statusLabel.setText("错误: " + error);
                            showError(error);
                        });
                    }
                }
        );
    }

    private int parseSpeakerCount() {
        try {
            int parsed = Integer.parseInt(speakerCountField.getText().trim());
            return Math.max(parsed, 1);
        } catch (Exception e) {
            return 1;
        }
    }

    private String buildRecognitionProgressText(int progress, int detectedCount, int completedCount) {
        if (detectedCount <= 0) {
            return "转译进度: " + progress + "%";
        }
        if (completedCount <= 0) {
            return "已检测到 " + detectedCount + " 条语音片段，正在开始转写...（" + progress + "%）";
        }
        int currentWorking = Math.min(detectedCount, completedCount + 1);
        if (completedCount < detectedCount) {
            return "已完成 " + completedCount + " / " + detectedCount + " 条，正在转写第 " + currentWorking + " 条（" + progress + "%）";
        }
        return "已完成 " + completedCount + " 条转写，正在整理结果...（" + progress + "%）";
    }

    private void refreshDisplayList() {
        SpeechRecognitionUnit selected = listView == null ? null : listView.getSelectionModel().getSelectedItem();
        String keyword = searchField == null ? "" : searchField.getText();
        filteredRecognitionList.clear();
        filteredRecognitionList.addAll(recognitionSearchStore.filter(recognitionList, keyword, configStore));

        if (listView == null) {
            updateRecognitionPageItems();
            return;
        }

        SpeechRecognitionUnit target = null;
        if (selected != null && filteredRecognitionList.contains(selected)) {
            target = selected;
        } else if (translatingUnit != null && filteredRecognitionList.contains(translatingUnit)) {
            target = translatingUnit;
        }

        if (target != null) {
            recognitionPageIndex = filteredRecognitionList.indexOf(target) / Math.max(1, recognitionPageSize);
        }
        updateRecognitionPageItems();

        if (selected != null && displayList.contains(selected)) {
            listView.getSelectionModel().select(selected);
        } else if (translatingUnit != null && displayList.contains(translatingUnit)) {
            listView.getSelectionModel().select(translatingUnit);
        }
    }

    private void updateRecognitionPageItems() {
        int total = filteredRecognitionList.size();
        int pageSize = Math.max(1, recognitionPageSize);
        int pageCount = Math.max(1, (total + pageSize - 1) / pageSize);
        recognitionPageIndex = Math.max(0, Math.min(recognitionPageIndex, pageCount - 1));

        int from = Math.min(total, recognitionPageIndex * pageSize);
        int to = Math.min(total, from + pageSize);
        displayList.setAll(filteredRecognitionList.subList(from, to));

        if (recognitionPageLabel != null) {
            if (total == 0) {
                recognitionPageLabel.setText("第 0 / 0 页，共 0 条");
            } else {
                recognitionPageLabel.setText("第 " + (recognitionPageIndex + 1) + " / " + pageCount + " 页，共 " + total + " 条");
            }
        }
        if (recognitionPrevPageBtn != null) {
            recognitionPrevPageBtn.setDisable(total == 0 || recognitionPageIndex <= 0);
        }
        if (recognitionNextPageBtn != null) {
            recognitionNextPageBtn.setDisable(total == 0 || recognitionPageIndex >= pageCount - 1);
        }
    }

    private boolean selectRecognitionUnit(SpeechRecognitionUnit target, boolean locateWaveform, boolean suppressSeek) {
        if (target == null || listView == null) {
            return false;
        }
        if (!filteredRecognitionList.contains(target)) {
            refreshDisplayList();
        }
        int filteredIndex = filteredRecognitionList.indexOf(target);
        if (filteredIndex < 0) {
            return false;
        }

        recognitionPageIndex = filteredIndex / Math.max(1, recognitionPageSize);
        updateRecognitionPageItems();
        if (!displayList.contains(target)) {
            return false;
        }

        boolean previousSuppress = suppressSelectionSeek;
        suppressSelectionSeek = suppressSeek || previousSuppress;
        try {
            listView.getSelectionModel().select(target);
            listView.scrollTo(target);
        } finally {
            suppressSelectionSeek = previousSuppress;
        }

        if (locateWaveform) {
            locateWaveformToUnit(target);
        }
        return true;
    }

    private void preparePreviewAudio(File file) throws Exception {
        cleanupPreviewClipOnly();

        AudioSourceResolver.ResolvedAudio resolvedAudio = audioSourceResolver.resolveForProcessing(file, "player/main-preview");
        previewAudioFile = resolvedAudio.getWorkingFile();

        AudioInputStream audioStream = AudioSystem.getAudioInputStream(previewAudioFile);
        AudioFormat format = audioStream.getFormat();

        if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    format.getSampleRate(),
                    16,
                    format.getChannels(),
                    format.getChannels() * 2,
                    format.getSampleRate(),
                    false
            );
            audioStream = AudioSystem.getAudioInputStream(targetFormat, audioStream);
        }

        DataLine.Info info = new DataLine.Info(Clip.class, audioStream.getFormat());
        previewClip = (Clip) AudioSystem.getLine(info);
        previewClip.open(audioStream);
        previewClip.addLineListener(event -> {
            if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP
                    && previewClip != null
                    && previewClip.getMicrosecondPosition() >= previewClip.getMicrosecondLength() - 100000) {
                Platform.runLater(() -> updatePreviewPlaybackButtonState(false));
            }
        });

        if (previewProgressSlider != null) {
            previewProgressSlider.setMax(previewClip.getMicrosecondLength() / 1_000_000.0);
            previewProgressSlider.setValue(0);
        }

        if (previewTimeLabel != null) {
            double totalSeconds = previewClip.getMicrosecondLength() / 1_000_000.0;
            previewTimeLabel.setText("00:00 / " + formatTime(totalSeconds));
        }

        if (previewWaveformCanvas != null) {
            previewWaveformCanvas.loadAudioFile(previewAudioFile);
            previewWaveformCanvas.clearSelection();
            previewWaveformCanvas.setPlayedRatio(0);
        }

        updatePreviewPlaybackButtonState(false);
        startPreviewSchedulerIfNeeded();
    }

    private void startPreviewSchedulerIfNeeded() {
        if (previewSchedulerStarted) {
            return;
        }
        previewSchedulerStarted = true;
        previewScheduler = Executors.newSingleThreadScheduledExecutor();
        previewScheduler.scheduleAtFixedRate(() -> {
            if (previewClip == null || !previewClip.isRunning()) {
                return;
            }
            double currentSec = previewClip.getMicrosecondPosition() / 1_000_000.0;
            double totalSec = Math.max(0.001, previewClip.getMicrosecondLength() / 1_000_000.0);
            double ratio = currentSec / totalSec;

            Platform.runLater(() -> {
                if (previewProgressSlider != null) {
                    previewProgressSlider.setValue(currentSec);
                }
                if (previewTimeLabel != null) {
                    previewTimeLabel.setText(formatTime(currentSec) + " / " + formatTime(totalSec));
                }
                if (previewWaveformCanvas != null) {
                    previewWaveformCanvas.setPlayedRatio(ratio);
                }
                syncListSelectionWithPlaybackTime(currentSec);
            });
        }, 0, 50, TimeUnit.MILLISECONDS);
    }

    private void togglePreviewPlay() {
        if (previewClip == null) {
            return;
        }
        if (previewClip.getMicrosecondPosition() >= previewClip.getMicrosecondLength() - 100000) {
            previewClip.setMicrosecondPosition(0);
            if (previewProgressSlider != null) {
                previewProgressSlider.setValue(0);
            }
            if (previewWaveformCanvas != null) {
                previewWaveformCanvas.setPlayedRatio(0);
            }
        }
        if (!previewClip.isRunning()) {
            previewClip.start();
            updatePreviewPlaybackButtonState(true);
        }
    }

    private void pausePreview() {
        if (previewClip != null && previewClip.isRunning()) {
            previewClip.stop();
        }
        updatePreviewPlaybackButtonState(false);
    }

    private void seekPreviewBy(double secondsDelta) {
        if (previewClip == null) {
            return;
        }
        double currentSec = previewClip.getMicrosecondPosition() / 1_000_000.0;
        double totalSec = previewClip.getMicrosecondLength() / 1_000_000.0;
        double target = Math.max(0, Math.min(totalSec, currentSec + secondsDelta));
        jumpToPreviewTime(target);
    }

    private void seekPreviewToSlider() {
        if (previewClip == null || previewProgressSlider == null) {
            return;
        }
        double target = previewProgressSlider.getValue();
        jumpToPreviewTime(target);
    }

    private void jumpPreviewFromField() {
        if (previewClip == null || previewJumpField == null) {
            return;
        }

        try {
            double seconds = parseManualTime(previewJumpField.getText());
            jumpToPreviewTime(seconds);
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private void jumpToPreviewTime(double seconds) {
        if (previewClip == null) {
            return;
        }
        double totalSec = previewClip.getMicrosecondLength() / 1_000_000.0;
        double target = Math.max(0, Math.min(totalSec, seconds));
        boolean wasPlaying = previewClip.isRunning();
        if (wasPlaying) {
            previewClip.stop();
        }

        previewClip.setMicrosecondPosition((long) (target * 1_000_000));

        if (previewProgressSlider != null) {
            previewProgressSlider.setValue(target);
        }
        if (previewTimeLabel != null) {
            previewTimeLabel.setText(formatTime(target) + " / " + formatTime(totalSec));
        }
        if (previewWaveformCanvas != null) {
            previewWaveformCanvas.setPlayedRatio(target / Math.max(0.001, totalSec));
        }
        syncListSelectionWithPlaybackTime(target);

        if (wasPlaying) {
            previewClip.start();
        }
    }

    private void locateWaveformToUnit(SpeechRecognitionUnit unit) {
        if (unit == null || previewWaveformCanvas == null) {
            return;
        }

        double anchorTime = computeUnitAnchorTime(unit);
        if (previewClip != null) {
            jumpToPreviewTime(anchorTime);
        } else if (previewProgressSlider != null) {
            previewProgressSlider.setValue(Math.max(0, anchorTime));
        }

        highlightWaveformSelectionOnly(unit);
    }

    private void syncListSelectionWithPlaybackTime(double currentSec) {
        if (listView == null || recognitionList.isEmpty()) {
            return;
        }

        SpeechRecognitionUnit target = findUnitByTime(currentSec);
        if (target == null) {
            return;
        }

        SpeechRecognitionUnit selected = listView.getSelectionModel().getSelectedItem();
        if (selected != target) {
            selectRecognitionUnit(target, false, true);
        }

        highlightWaveformSelectionOnly(target);
    }

    private SpeechRecognitionUnit findUnitByTime(double seconds) {
        SpeechRecognitionUnit previous = null;
        for (int i = 0; i < recognitionList.size(); i++) {
            SpeechRecognitionUnit unit = recognitionList.get(i);
            if (seconds < unit.getStartTime()) {
                return previous;
            }
            SpeechRecognitionUnit next = i + 1 < recognitionList.size() ? recognitionList.get(i + 1) : null;
            if (next != null && seconds >= next.getStartTime()) {
                previous = unit;
                continue;
            }
            if (seconds >= unit.getStartTime() && seconds <= unit.getEndTime()) {
                return unit;
            }
            previous = unit;
        }
        return previous;
    }

    private double computeUnitAnchorTime(SpeechRecognitionUnit unit) {
        if (unit == null) {
            return 0.0;
        }

        double start = Math.max(0.0, unit.getStartTime());
        double end = Math.max(start, unit.getEndTime());
        double duration = end - start;
        if (duration <= 0.0) {
            return start;
        }

        double offset = Math.min(0.02, duration / 2.0);
        return Math.min(end, start + offset);
    }

    private void highlightWaveformSelectionOnly(SpeechRecognitionUnit unit) {
        if (unit == null || previewWaveformCanvas == null) {
            return;
        }

        double totalSec = 0;
        if (previewClip != null) {
            totalSec = previewClip.getMicrosecondLength() / 1_000_000.0;
        } else if (previewProgressSlider != null) {
            totalSec = previewProgressSlider.getMax();
        }

        if (totalSec > 0) {
            previewWaveformCanvas.setSelectionRatio(
                    unit.getStartTime() / totalSec,
                    unit.getEndTime() / totalSec
            );
        }
    }

    private void updatePreviewLayoutWidth(double targetWidth) {
        if (previewWaveformCanvas == null || previewProgressSlider == null) {
            return;
        }
        if (Math.abs(previewWaveformCanvas.getWidth() - targetWidth) > 1.0) {
            previewWaveformCanvas.setCanvasSize(targetWidth, 150);
        }
        if (Math.abs(previewProgressSlider.getPrefWidth() - targetWidth) > 1.0
                || Math.abs(previewProgressSlider.getMinWidth() - targetWidth) > 1.0
                || Math.abs(previewProgressSlider.getMaxWidth() - targetWidth) > 1.0) {
            previewProgressSlider.setMinWidth(targetWidth);
            previewProgressSlider.setPrefWidth(targetWidth);
            previewProgressSlider.setMaxWidth(targetWidth);
        }
    }

    private void updatePreviewWidthFromScene() {
        if (mainContent == null || mainContent.getScene() == null) {
            return;
        }
        double targetWidth = computePreviewWidth();
        updatePreviewLayoutWidth(targetWidth);
    }

    private void cleanupPreviewClipOnly() {
        if (previewClip != null) {
            if (previewClip.isRunning()) {
                previewClip.stop();
            }
            previewClip.close();
            previewClip = null;
        }
    }

    private void cleanupPreviewPlayer() {
        cleanupPreviewClipOnly();
        updatePreviewPlaybackButtonState(false);
        if (previewScheduler != null) {
            previewScheduler.shutdownNow();
            previewScheduler = null;
            previewSchedulerStarted = false;
        }
    }

    private String formatTime(double seconds) {
        int totalSeconds = (int) Math.max(0, seconds);
        int minutes = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    private void toggleSidePanel() {
        if (isSidePanelVisible()) {
            closeSidePanel();
        } else {
            openSidePanel();
        }
    }

    private void openSidePanel() {
        if (isSidePanelVisible()) {
            return;
        }

        try {
            if (sidePanelLayer == null) {
                return;
            }
            VoicePrintSidePanel sidePanel = new VoicePrintSidePanel(null, null);
            double panelWidth = computeSidePanelWidth();
            applySidePanelWidth(sidePanel, panelWidth);

            sidePanel.setOnCloseCallback(() -> {
                isSidePanelOpen = false;
                if (sidePanelLayer != null) {
                    sidePanelLayer.getChildren().clear();
                }
                currentRightPanelKind = RightPanelKind.NONE;
                voicePrintCheckBox.setSelected(false);
            });

            showRightPanel(sidePanel, RightPanelKind.VOICE_PRINT_LIBRARY, panelWidth, () -> isSidePanelOpen = true);

            voicePrintCheckBox.setSelected(true);
        } catch (Exception e) {
            showError("打开声纹库失败: " + e.getMessage());
        }
    }

    private void closeSidePanel() {
        if (!isSidePanelVisible()) {
            return;
        }

        try {
            VoicePrintSidePanel sidePanel = getCurrentSidePanel();
            if (sidePanel == null) {
                isSidePanelOpen = false;
                currentRightPanelKind = RightPanelKind.NONE;
                return;
            }

            isSidePanelOpen = false;
            closeRightPanel(() -> isSidePanelOpen = false);
            voicePrintCheckBox.setSelected(false);
        } catch (Exception e) {
            showError("关闭声纹库失败: " + e.getMessage());
        }
    }

    private void adjustOpenSidePanelWidth() {
        Region panel = getCurrentRightPanel();
        if (panel == null) {
            return;
        }
        if (currentRightPanelKind == RightPanelKind.VOICE_PRINT_LIBRARY && panel instanceof VoicePrintSidePanel) {
            applySidePanelWidth((VoicePrintSidePanel) panel, computeSidePanelWidth());
        } else if (currentRightPanelKind == RightPanelKind.RESULT_EDITOR) {
            applyRecognitionEditorWidth(panel, computeRecognitionEditorWidth());
        }
    }

    private double computeSidePanelWidth() {
        if (mainContent == null || mainContent.getScene() == null) {
            return 300;
        }
        double sceneWidth = mainContent.getScene().getWidth();
        double target = sceneWidth * 0.26;
        return Math.max(220, Math.min(360, target));
    }

    private void applySidePanelWidth(VoicePrintSidePanel sidePanel, double panelWidth) {
        sidePanel.setMinWidth(220);
        sidePanel.setPrefWidth(panelWidth);
        sidePanel.setMaxWidth(panelWidth);
    }

    private double computePreviewWidth() {
        double sceneWidth = mainContent.getScene().getWidth();
        return Math.max(320, sceneWidth - 120);
    }

    private void updatePreviewPlaybackButtonState(boolean playing) {
        if (previewPlayBtn != null) {
            previewPlayBtn.setStyle(buildPlaybackToggleStyle("#27ae60", playing));
        }
        if (previewPauseBtn != null) {
            previewPauseBtn.setStyle(buildPlaybackToggleStyle("#e74c3c", !playing));
        }
    }

    private String buildPlaybackToggleStyle(String color, boolean selected) {
        String background = selected ? "derive(" + color + ", -12%)" : color;
        String border = selected ? "#1f2d3d" : "transparent";
        String borderWidth = selected ? "2" : "0";
        return "-fx-background-color: " + background + "; "
                + "-fx-text-fill: white; "
                + "-fx-padding: 10 20; "
                + "-fx-font-size: 14px; "
                + "-fx-font-weight: bold; "
                + "-fx-border-radius: 4; "
                + "-fx-background-radius: 4; "
                + "-fx-border-color: " + border + "; "
                + "-fx-border-width: " + borderWidth + "; "
                + "-fx-cursor: hand;";
    }

    private boolean isSidePanelVisible() {
        return currentRightPanelKind == RightPanelKind.VOICE_PRINT_LIBRARY && getCurrentSidePanel() != null;
    }

    private VoicePrintSidePanel getCurrentSidePanel() {
        if (sidePanelLayer == null || sidePanelLayer.getChildren().isEmpty()) {
            return null;
        }
        if (sidePanelLayer.getChildren().get(0) instanceof VoicePrintSidePanel) {
            return (VoicePrintSidePanel) sidePanelLayer.getChildren().get(0);
        }
        return null;
    }

    private Region getCurrentRightPanel() {
        if (sidePanelLayer == null || sidePanelLayer.getChildren().isEmpty()) {
            return null;
        }
        if (sidePanelLayer.getChildren().get(0) instanceof Region) {
            return (Region) sidePanelLayer.getChildren().get(0);
        }
        return null;
    }

    private void showRightPanel(Region panel, RightPanelKind panelKind, double panelWidth, Runnable onShown) {
        if (sidePanelLayer == null || panel == null) {
            return;
        }

        if (currentRightPanelKind == RightPanelKind.VOICE_PRINT_LIBRARY && panelKind != RightPanelKind.VOICE_PRINT_LIBRARY) {
            voicePrintCheckBox.setSelected(false);
            isSidePanelOpen = false;
        }

        sidePanelLayer.getChildren().clear();
        panel.setTranslateX(panelWidth);
        sidePanelLayer.getChildren().setAll(panel);
        sidePanelLayer.requestLayout();
        currentRightPanelKind = panelKind;

        TranslateTransition transition = new TranslateTransition(Duration.millis(300), panel);
        transition.setFromX(panelWidth);
        transition.setToX(0);
        transition.setOnFinished(e -> {
            if (onShown != null) {
                onShown.run();
            }
        });
        transition.play();
    }

    private void closeRightPanel(Runnable onClosed) {
        Region currentPanel = getCurrentRightPanel();
        if (currentPanel == null) {
            currentRightPanelKind = RightPanelKind.NONE;
            editingUnit = null;
            if (onClosed != null) {
                onClosed.run();
            }
            return;
        }

        TranslateTransition transition = new TranslateTransition(Duration.millis(220), currentPanel);
        double hideOffset = Math.max(currentPanel.getWidth(), currentPanel.getPrefWidth());
        transition.setFromX(0);
        transition.setToX(hideOffset <= 0 ? 320 : hideOffset);
        transition.setOnFinished(e -> {
            if (sidePanelLayer != null) {
                sidePanelLayer.getChildren().clear();
            }
            currentRightPanelKind = RightPanelKind.NONE;
            editingUnit = null;
            if (onClosed != null) {
                onClosed.run();
            }
        });
        transition.play();
    }

    private double computeRecognitionEditorWidth() {
        if (mainContent == null || mainContent.getScene() == null) {
            return 360;
        }
        double sceneWidth = mainContent.getScene().getWidth();
        double target = sceneWidth * 0.30;
        return Math.max(300, Math.min(440, target));
    }

    private void applyRecognitionEditorWidth(Region panel, double panelWidth) {
        panel.setMinWidth(300);
        panel.setPrefWidth(panelWidth);
        panel.setMaxWidth(panelWidth);
    }

    private void openRecognitionEditor(SpeechRecognitionUnit unit) {
        if (unit == null) {
            return;
        }

        if (progressBar != null && progressBar.isVisible() && unit == translatingUnit) {
            showError("当前片段仍在转译中，请稍后再编辑。");
            return;
        }

        editingUnit = unit;
        if (recognitionEditPanel == null) {
            recognitionEditPanel = createRecognitionEditPanel();
        }

        populateRecognitionEditor(unit);
        double panelWidth = computeRecognitionEditorWidth();
        applyRecognitionEditorWidth(recognitionEditPanel, panelWidth);

        if (currentRightPanelKind == RightPanelKind.RESULT_EDITOR
                && sidePanelLayer != null
                && sidePanelLayer.getChildren().contains(recognitionEditPanel)) {
            return;
        }

        showRightPanel(recognitionEditPanel, RightPanelKind.RESULT_EDITOR, panelWidth, null);
    }

    private VBox createRecognitionEditPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(18, 18, 20, 18));
        panel.setStyle(
                "-fx-background-color: #ffffff; " +
                        "-fx-border-color: #dde1e7; " +
                        "-fx-border-width: 0 0 0 1; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.10), 16, 0, -4, 0);"
        );

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label titleLabel = new Label("结果修正");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("关闭");
        styleButton(closeBtn, "#95a5a6");
        closeBtn.setOnAction(e -> closeRecognitionEditor());

        titleRow.getChildren().addAll(titleLabel, spacer, closeBtn);

        Label introLabel = new Label("点击识别片段右侧的“✂ 编辑”后，可在这里修正发言人标签和转写内容。保存后会同步更新列表展示和最终导出结果。");
        introLabel.setWrapText(true);
        introLabel.setStyle("-fx-text-fill: #5d6d7e; -fx-font-size: 12px; -fx-line-spacing: 2;");

        VBox infoCard = new VBox(6);
        infoCard.setPadding(new Insets(10, 12, 10, 12));
        infoCard.setStyle("-fx-background-color: #f8fbff; -fx-border-color: #d8e7fb; -fx-border-radius: 6; -fx-background-radius: 6;");

        editTargetLabel = new Label("当前片段：未选择");
        editTargetLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        editTimeLabel = new Label("时间范围：--");
        editTimeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #5d6d7e;");

        infoCard.getChildren().addAll(editTargetLabel, editTimeLabel);

        Label speakerFieldLabel = new Label("发言人标签");
        speakerFieldLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        smartReplaceDialogBtn = new Button("智能替换...");
        styleButton(smartReplaceDialogBtn, "#2980b9");
        smartReplaceDialogBtn.setTooltip(new Tooltip("打开独立匹配窗口，根据已注册声纹帮你查找更像的发言人候选。"));
        smartReplaceDialogBtn.setOnAction(e -> openSmartReplaceDialog());

        HBox speakerTitleRow = new HBox(10, speakerFieldLabel, smartReplaceDialogBtn);
        speakerTitleRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        editSpeakerField = new TextField();
        editSpeakerField.setPromptText("例如：说话人1、主持人、张三");
        editSpeakerField.setStyle(getInputStyle());
        editSpeakerField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(editSpeakerField, Priority.ALWAYS);

        Label speakerHintLabel = new Label("如果你想参考已注册发言人来修正标签，可以点右侧“智能替换...”打开独立匹配窗口。");
        speakerHintLabel.setWrapText(true);
        speakerHintLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");

        Label contentFieldLabel = new Label("转写内容");
        contentFieldLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        editContentArea = new TextArea();
        editContentArea.setWrapText(true);
        editContentArea.setPrefRowCount(12);
        editContentArea.setStyle(
                "-fx-background-color: white; " +
                        "-fx-border-color: #d0d7de; " +
                        "-fx-border-radius: 6; " +
                        "-fx-background-radius: 6; " +
                        "-fx-font-size: 13px; " +
                        "-fx-text-fill: #2c3e50;"
        );
        VBox.setVgrow(editContentArea, Priority.ALWAYS);

        Button saveBtn = new Button("保存修改");
        styleButton(saveBtn, "#27ae60");
        saveBtn.setOnAction(e -> saveRecognitionEdit());

        Button cancelBtn = new Button("取消");
        styleButton(cancelBtn, "#95a5a6");
        cancelBtn.setOnAction(e -> closeRecognitionEditor());

        HBox actionRow = new HBox(10, saveBtn, cancelBtn);
        actionRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        panel.getChildren().addAll(
                titleRow,
                introLabel,
                infoCard,
                speakerTitleRow,
                editSpeakerField,
                speakerHintLabel,
                contentFieldLabel,
                editContentArea,
                actionRow
        );
        return panel;
    }

    private void populateRecognitionEditor(SpeechRecognitionUnit unit) {
        if (unit == null || editSpeakerField == null || editContentArea == null) {
            return;
        }
        int index = recognitionList.indexOf(unit) + 1;
        editTargetLabel.setText("当前片段：第 " + Math.max(index, 1) + " 条");
        editTimeLabel.setText("时间范围：" + unit.getFormattedTime());
        editSpeakerField.setText(unit.getSpeaker() == null ? "" : unit.getSpeaker());
        editContentArea.setText(unit.getContent() == null ? "" : unit.getContent());
        selectRecognitionUnit(unit, true, false);
    }

    private String getSmartReplaceThresholdText() {
        if (configStore == null) {
            return "0.82";
        }
        String value = configStore.getValue("speaker.matchThreshold");
        if (value == null || value.trim().isEmpty()) {
            return "0.82";
        }
        return value.trim();
    }

    private float parseSmartReplaceThreshold(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            raw = getSmartReplaceThresholdText();
        }

        try {
            float value = Float.parseFloat(raw.trim());
            if (value <= 0 || value >= 1) {
                throw new IllegalArgumentException("智能替换门槛请填写 0 到 1 之间的小数，例如 0.82。");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("智能替换门槛格式不对，请填写类似 0.82 这样的数字。");
        }
    }

    private void openSmartReplaceDialog() {
        if (editingUnit == null) {
            showError("请先选择一条识别结果，再进行智能替换。");
            return;
        }

        Stage dialog = new Stage();
        dialog.initOwner(mainContent.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("智能替换发言人");

        VBox root = new VBox(14);
        root.setPadding(new Insets(18));
        root.setStyle("-fx-background-color: #f7f9fc;");

        Label titleLabel = new Label("智能替换发言人");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label introLabel = new Label("系统会根据当前片段的声音特征，到已注册发言人库里查找更像的候选用户。先设置匹配门槛，再点击右侧开始匹配。");
        introLabel.setWrapText(true);
        introLabel.setStyle("-fx-text-fill: #5d6d7e; -fx-font-size: 12px; -fx-line-spacing: 2;");

        VBox infoCard = new VBox(6);
        infoCard.setPadding(new Insets(10, 12, 10, 12));
        infoCard.setStyle("-fx-background-color: #ffffff; -fx-border-color: #d8e1ea; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label currentLabel = new Label("当前片段：第 " + Math.max(1, recognitionList.indexOf(editingUnit) + 1) + " 条");
        currentLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label currentTime = new Label("时间范围：" + editingUnit.getFormattedTime());
        currentTime.setStyle("-fx-font-size: 12px; -fx-text-fill: #5d6d7e;");

        Label currentSpeaker = new Label("当前标签：" + (editingUnit.getSpeaker() == null ? "未标注发言人" : editingUnit.getSpeaker()));
        currentSpeaker.setStyle("-fx-font-size: 12px; -fx-text-fill: #5d6d7e;");
        infoCard.getChildren().addAll(currentLabel, currentTime, currentSpeaker);

        HBox topBar = new HBox(10);
        topBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label thresholdLabel = new Label("匹配门槛");
        thresholdLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        TextField thresholdField = new TextField(getSmartReplaceThresholdText());
        thresholdField.setPrefWidth(90);
        thresholdField.setStyle(getInputStyle());
        thresholdField.setPromptText("0.82");
        thresholdField.setTooltip(new Tooltip("匹配门槛越高越严格，推荐 0.80 到 0.88。"));

        Label thresholdHint = new Label("建议 0.80 到 0.88，越高越严格");
        thresholdHint.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");

        Pane topSpacer = new Pane();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        Button matchBtn = new Button("开始匹配");
        styleButton(matchBtn, "#2980b9");

        topBar.getChildren().addAll(thresholdLabel, thresholdField, thresholdHint, topSpacer, matchBtn);

        Label listTitle = new Label("候选用户列表");
        listTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        ListView<VoicePrintSmartMatchService.Candidate> candidateListView = new ListView<>();
        candidateListView.setStyle(
                "-fx-background-color: white; " +
                        "-fx-border-color: #d8e1ea; " +
                        "-fx-border-radius: 6; " +
                        "-fx-background-radius: 6; " +
                        "-fx-padding: 6;"
        );
        VBox.setVgrow(candidateListView, Priority.ALWAYS);
        candidateListView.setCellFactory(param -> new ListCell<VoicePrintSmartMatchService.Candidate>() {
            @Override
            protected void updateItem(VoicePrintSmartMatchService.Candidate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                VBox cellBox = new VBox(4);
                cellBox.setPadding(new Insets(10));
                cellBox.setStyle(
                        "-fx-background-color: white; " +
                                "-fx-border-color: #e0e6ed; " +
                                "-fx-border-radius: 6; " +
                                "-fx-background-radius: 6;"
                );

                HBox headRow = new HBox(8);
                headRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                Label userLabel = new Label(item.getUserName());
                userLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

                Pane rowSpacer = new Pane();
                HBox.setHgrow(rowSpacer, Priority.ALWAYS);

                Label recommendBadge = new Label("最推荐用户");
                recommendBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 999; -fx-background-color: #d9f2e3; -fx-text-fill: #1e6b3a;");
                boolean topRecommended = getIndex() == 0;
                recommendBadge.setVisible(topRecommended);
                recommendBadge.setManaged(topRecommended);

                Label scoreLabel = new Label("平均匹配度 " + (int) Math.round(item.getScore() * 100) + "%，该用户已注册 " + item.getVoiceCount() + " 条声纹");
                scoreLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #5d6d7e;");

                headRow.getChildren().addAll(userLabel, rowSpacer, recommendBadge);
                cellBox.getChildren().addAll(headRow, scoreLabel);
                setGraphic(cellBox);
            }
        });

        Label resultHintLabel = new Label("点击“开始匹配”后，这里会列出达到门槛的候选用户。");
        resultHintLabel.setWrapText(true);
        resultHintLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");

        ProgressBar matchProgressBar = new ProgressBar();
        matchProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        matchProgressBar.setVisible(false);
        matchProgressBar.setPrefWidth(Double.MAX_VALUE);

        Button replaceBtn = new Button("用选中用户替换");
        styleButton(replaceBtn, "#27ae60");
        replaceBtn.setDisable(true);

        Button closeBtn = new Button("关闭");
        styleButton(closeBtn, "#95a5a6");
        closeBtn.setOnAction(e -> dialog.close());

        HBox actionRow = new HBox(10, replaceBtn, closeBtn);
        actionRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        matchBtn.setOnAction(e -> {
            File audioFile = previewAudioFile != null ? previewAudioFile : currentAudioFile;
            if (audioFile == null || !audioFile.exists()) {
                showError("当前还没有可用音频，暂时无法进行智能替换。");
                return;
            }

            float threshold;
            try {
                threshold = parseSmartReplaceThreshold(thresholdField.getText());
                thresholdField.setText(String.format("%.2f", threshold));
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
                return;
            }

            final File workingAudioFile = audioFile;
            final SpeechRecognitionUnit targetUnit = editingUnit;
            matchBtn.setDisable(true);
            replaceBtn.setDisable(true);
            thresholdField.setDisable(true);
            candidateListView.setDisable(true);
            matchProgressBar.setVisible(true);
            resultHintLabel.setText("正在后台匹配中，你可以稍等结果出来后再选择要替换成哪位用户。");

            Thread worker = new Thread(() -> {
                try {
                    List<VoicePrintSmartMatchService.Candidate> candidates = voicePrintSmartMatchService.findCandidates(
                            workingAudioFile,
                            targetUnit.getStartTime(),
                            targetUnit.getEndTime(),
                            threshold
                    );
                    Platform.runLater(() -> {
                        if (!dialog.isShowing()) {
                            return;
                        }
                        candidateListView.setItems(FXCollections.observableArrayList(candidates));
                        candidateListView.setDisable(false);
                        matchBtn.setDisable(false);
                        thresholdField.setDisable(false);
                        matchProgressBar.setVisible(false);
                        replaceBtn.setDisable(candidates.isEmpty());
                        if (candidates.isEmpty()) {
                            resultHintLabel.setText(String.format("这次没有找到达到 %.2f 门槛的相似用户。你可以调低一点门槛再试。", threshold));
                            showInfo("匹配完成", "这次没有找到达到当前门槛的相似用户。");
                        } else {
                            resultHintLabel.setText("已经为你找到 " + candidates.size() + " 位相似用户。请在下方选择一位作为新的发言人标签。");
                            showInfo("匹配完成", "已经为你找到 " + candidates.size() + " 位相似用户。");
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        if (!dialog.isShowing()) {
                            return;
                        }
                        matchBtn.setDisable(false);
                        thresholdField.setDisable(false);
                        candidateListView.setDisable(false);
                        matchProgressBar.setVisible(false);
                        resultHintLabel.setText("这次匹配没有完成：" + ex.getMessage());
                        showError("智能替换失败: " + ex.getMessage());
                    });
                }
            }, "voiceprint-smart-match");
            worker.setDaemon(true);
            worker.start();
        });

        thresholdField.setOnAction(e -> matchBtn.fire());

        candidateListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                replaceBtn.setDisable(newVal == null)
        );

        replaceBtn.setOnAction(e -> {
            VoicePrintSmartMatchService.Candidate selectedCandidate = candidateListView.getSelectionModel().getSelectedItem();
            if (selectedCandidate == null) {
                showError("请先在候选列表中选择一个用户。");
                return;
            }
            applySmartCandidate(selectedCandidate);
            dialog.close();
        });

        root.getChildren().addAll(
                titleLabel,
                introLabel,
                infoCard,
                topBar,
                listTitle,
                candidateListView,
                matchProgressBar,
                resultHintLabel,
                actionRow
        );

        Scene scene = new Scene(root, 620, 560);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void applySmartCandidate(VoicePrintSmartMatchService.Candidate candidate) {
        if (candidate == null || editingUnit == null) {
            return;
        }

        String currentLabel = editingUnit.getSpeaker() == null ? "" : editingUnit.getSpeaker().trim();
        String newSpeaker = candidate.getUserName() == null ? "" : candidate.getUserName().trim();
        if (newSpeaker.isEmpty()) {
            showError("这个候选发言人名称为空，暂时无法替换。");
            return;
        }

        if (newSpeaker.equals(currentLabel)) {
            showInfo("已是当前标签", "这条片段当前已经是“" + newSpeaker + "”，不需要再替换。");
            return;
        }

        int sameLabelCount = countSameSpeakerLabels(currentLabel, editingUnit);
        boolean replaceAll = false;
        if (!currentLabel.isEmpty() && sameLabelCount > 0) {
            Optional<javafx.scene.control.ButtonType> result = showSpeakerBatchReplacePrompt(
                    currentLabel,
                    newSpeaker,
                    getSameSpeakerLabelUnits(currentLabel, editingUnit)
            );
            if (!result.isPresent()) {
                return;
            }
            replaceAll = "一起替换".equals(result.get().getText());
        }

        int replacedCount = replaceSpeakerLabel(editingUnit, currentLabel, newSpeaker, replaceAll);
        editSpeakerField.setText(newSpeaker);
        refreshDisplayList();
        if (listView != null) {
            listView.refresh();
            selectRecognitionUnit(editingUnit, false, false);
        }
        highlightWaveformSelectionOnly(editingUnit);

        String message;
        if (replaceAll && replacedCount > 1) {
            message = "已经帮你把“" + currentLabel + "”统一替换为“" + newSpeaker + "”，共更新 " + replacedCount + " 条片段。";
        } else {
            message = "已经把当前片段的发言人替换为“" + newSpeaker + "”。";
        }
        statusLabel.setText("智能替换已完成");
        showInfo("替换成功", message);
    }

    private Optional<javafx.scene.control.ButtonType> showSpeakerBatchReplacePrompt(
            String currentLabel,
            String newSpeaker,
            List<SpeechRecognitionUnit> sameLabelUnits
    ) {
        while (true) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("智能替换");
            alert.setHeaderText("我找到了一位更像的已注册发言人");
            alert.setContentText(
                    "当前片段更像“" + newSpeaker + "”。\n"
                            + "另外我还发现有 " + sameLabelUnits.size() + " 条片段和它一样，都标着“" + currentLabel + "”。\n\n"
                            + "如果你希望我一次性帮你统一掉，可以直接点“一起替换”。如果你想先稳一点，也可以只替换当前这一条。"
            );

            javafx.scene.control.ButtonType detailBtn = new javafx.scene.control.ButtonType("查看详情");
            javafx.scene.control.ButtonType batchBtn = new javafx.scene.control.ButtonType("一起替换");
            javafx.scene.control.ButtonType singleBtn = new javafx.scene.control.ButtonType("只替换当前这条");
            alert.getButtonTypes().setAll(detailBtn, batchBtn, singleBtn);

            Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
            if (!result.isPresent()) {
                return result;
            }
            if ("查看详情".equals(result.get().getText())) {
                showSameLabelReplaceDetailDialog(currentLabel, sameLabelUnits);
                continue;
            }
            return result;
        }
    }

    private void showSameLabelReplaceDetailDialog(String currentLabel, List<SpeechRecognitionUnit> sameLabelUnits) {
        Stage dialog = new Stage();
        dialog.initOwner(mainContent.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("查看相同标签片段");

        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: #f7f9fc;");

        Label title = new Label("相同标签片段详情");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label intro = new Label("下面这些片段目前都标着“" + currentLabel + "”。你可以先看一下内容，再决定要不要一起替换。");
        intro.setWrapText(true);
        intro.setStyle("-fx-text-fill: #5d6d7e; -fx-font-size: 12px;");

        ListView<SpeechRecognitionUnit> detailListView = new ListView<>();
        detailListView.setItems(FXCollections.observableArrayList(sameLabelUnits));
        detailListView.setStyle(
                "-fx-background-color: white; " +
                        "-fx-border-color: #d8e1ea; " +
                        "-fx-border-radius: 6; " +
                        "-fx-background-radius: 6; " +
                        "-fx-padding: 6;"
        );
        detailListView.setCellFactory(param -> new ListCell<SpeechRecognitionUnit>() {
            @Override
            protected void updateItem(SpeechRecognitionUnit item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                VBox box = new VBox(4);
                box.setPadding(new Insets(10));
                box.setStyle("-fx-background-color: white; -fx-border-color: #e0e6ed; -fx-border-radius: 6; -fx-background-radius: 6;");
                Label time = new Label(item.getFormattedTime());
                time.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
                Label content = new Label(item.getContent() == null ? "（暂无内容）" : item.getContent());
                content.setWrapText(true);
                content.setStyle("-fx-font-size: 12px; -fx-text-fill: #5d6d7e;");
                box.getChildren().addAll(time, content);
                setGraphic(box);
            }
        });
        VBox.setVgrow(detailListView, Priority.ALWAYS);

        Button closeBtn = new Button("返回上一步");
        styleButton(closeBtn, "#95a5a6");
        closeBtn.setOnAction(e -> dialog.close());

        root.getChildren().addAll(title, intro, detailListView, closeBtn);
        dialog.setScene(new Scene(root, 620, 480));
        dialog.showAndWait();
    }

    private List<SpeechRecognitionUnit> getSameSpeakerLabelUnits(String speakerLabel, SpeechRecognitionUnit currentUnit) {
        ObservableList<SpeechRecognitionUnit> result = FXCollections.observableArrayList();
        if (speakerLabel == null || speakerLabel.trim().isEmpty()) {
            return result;
        }
        for (SpeechRecognitionUnit unit : recognitionList) {
            if (unit == currentUnit) {
                continue;
            }
            String label = unit.getSpeaker() == null ? "" : unit.getSpeaker().trim();
            if (speakerLabel.equals(label)) {
                result.add(unit);
            }
        }
        return result;
    }

    private int countSameSpeakerLabels(String speakerLabel, SpeechRecognitionUnit currentUnit) {
        if (speakerLabel == null || speakerLabel.trim().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (SpeechRecognitionUnit unit : recognitionList) {
            if (unit == currentUnit) {
                continue;
            }
            String label = unit.getSpeaker() == null ? "" : unit.getSpeaker().trim();
            if (speakerLabel.equals(label)) {
                count++;
            }
        }
        return count;
    }

    private int replaceSpeakerLabel(
            SpeechRecognitionUnit currentUnit,
            String oldSpeaker,
            String newSpeaker,
            boolean replaceAllSameLabel
    ) {
        int count = 0;
        if (replaceAllSameLabel && oldSpeaker != null && !oldSpeaker.trim().isEmpty()) {
            for (SpeechRecognitionUnit unit : recognitionList) {
                String label = unit.getSpeaker() == null ? "" : unit.getSpeaker().trim();
                if (oldSpeaker.equals(label)) {
                    unit.setSpeaker(newSpeaker);
                    count++;
                }
            }
        } else if (currentUnit != null) {
            currentUnit.setSpeaker(newSpeaker);
            count = 1;
        }
        return count;
    }

    private void saveRecognitionEdit() {
        if (editingUnit == null || editSpeakerField == null || editContentArea == null) {
            return;
        }

        String speaker = editSpeakerField.getText() == null ? "" : editSpeakerField.getText().trim();
        String content = editContentArea.getText() == null ? "" : editContentArea.getText().trim();

        if (speaker.isEmpty()) {
            showError("请先填写发言人标签。");
            return;
        }
        if (content.isEmpty()) {
            showError("请先填写转写内容。");
            return;
        }

        SpeechRecognitionUnit target = editingUnit;
        String oldSpeaker = target.getSpeaker() == null ? "" : target.getSpeaker();
        String oldContent = target.getContent() == null ? "" : target.getContent();
        if (speaker.equals(oldSpeaker) && content.equals(oldContent)) {
            showInfo("未修改", "当前内容与原结果一致，无需重复保存。");
            return;
        }
        target.setSpeaker(speaker);
        target.setContent(content);

        refreshDisplayList();
        if (listView != null) {
            listView.refresh();
            selectRecognitionUnit(target, false, false);
        }
        highlightWaveformSelectionOnly(target);

        int index = recognitionList.indexOf(target) + 1;
        String message = "已保存第 " + Math.max(index, 1) + " 条识别结果修改";
        if (filteredRecognitionList.contains(target)) {
            statusLabel.setText(message);
        } else {
            statusLabel.setText(message + "，当前筛选条件下该条可能已被隐藏");
        }
        showInfo("修改成功", message + "，导出结果也会使用修改后的内容。");
    }

    private void closeRecognitionEditor() {
        if (currentRightPanelKind != RightPanelKind.RESULT_EDITOR) {
            return;
        }
        closeRightPanel(null);
    }

    private void openLiveVoicePrintDialog() {
        if (currentAudioFile == null) {
            showError("请先加载音频文件，再进行现场声纹制作");
            return;
        }

        Stage dialog = new Stage();
        dialog.initOwner(mainContent.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("现场声纹制作（可视化剪切）");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f6fa;");

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/view/main.fxml"));
        Parent cutterView;
        AudioPlayerController cutterController;

        try {
            cutterView = loader.load();
            cutterController = loader.getController();
            cutterController.setPrimaryStage(dialog);
            cutterController.setOpenButtonEnabled(false);
            cutterController.openExternalFile(currentAudioFile);
        } catch (Exception e) {
            showError("打开可视化剪切界面失败: " + e.getMessage());
            return;
        }

        VBox registerPane = createLiveRegisterPane(dialog, cutterController);

        root.setCenter(cutterView);
        root.setBottom(registerPane);

        Scene scene = new Scene(root, 1050, 820);
        dialog.setScene(scene);
        dialog.show();
    }

    private VBox createLiveRegisterPane(Stage dialog, AudioPlayerController cutterController) {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(12, 20, 16, 20));
        panel.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-width: 1 0 0 0;");

        Label intro = new Label("可在上方波形中拖拽选择剪切区间，或使用当前识别列表选中项自动定位。");
        intro.setStyle("-fx-text-fill: #495057; -fx-font-size: 13px;");

        HBox nameRow = new HBox(10);
        nameRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        TextField userNameField = new TextField();
        userNameField.setPromptText("用户名");
        userNameField.setStyle(getInputStyle());
        userNameField.setPrefWidth(160);

        TextField voiceNameField = new TextField();
        voiceNameField.setPromptText("声纹名称（如：现场发言）");
        voiceNameField.setStyle(getInputStyle());
        voiceNameField.setPrefWidth(220);

        Button useSelectionBtn = new Button("使用当前识别片段区间");
        styleButton(useSelectionBtn, "#6c757d");

        Label rangeLabel = new Label("当前剪切区间: 未设置");
        rangeLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 12px;");

        Runnable refreshRangeLabel = () -> {
            if (cutterController.getAudioCutterController() == null) {
                rangeLabel.setText("当前剪切区间: 未设置");
                rangeLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 12px;");
                return;
            }

            double[] range = cutterController.getAudioCutterController().getCutRange();
            if (range[0] >= 0 && range[1] > range[0]) {
                rangeLabel.setText(String.format("当前剪切区间: %.2fs - %.2fs（时长 %.2fs）", range[0], range[1], range[1] - range[0]));
                rangeLabel.setStyle("-fx-text-fill: #155724; -fx-font-size: 12px;");
            } else {
                rangeLabel.setText("当前剪切区间: 未设置");
                rangeLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 12px;");
            }
        };

        useSelectionBtn.setOnAction(e -> {
            SpeechRecognitionUnit selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showError("请先在识别结果列表中选中一条记录");
                return;
            }

            cutterController.setCutRange(selected.getStartTime(), selected.getEndTime());
            cutterController.jumpToTime(selected.getStartTime());
            if (voiceNameField.getText().trim().isEmpty()) {
                voiceNameField.setText("片段_" + selected.getSpeaker());
            }
            refreshRangeLabel.run();
        });

        Button registerBtn = new Button("剪切并注册到声纹库");
        styleButton(registerBtn, "#27ae60");
        registerBtn.setOnAction(e -> {
            String userName = userNameField.getText().trim();
            String voiceName = voiceNameField.getText().trim();
            if (userName.isEmpty() || voiceName.isEmpty()) {
                showError("请填写用户名和声纹名称");
                return;
            }

            if (cutterController.getAudioCutterController() == null || !cutterController.getAudioCutterController().canExport()) {
                showError("请先在上方可视化波形中设置有效的剪切区间");
                return;
            }

            double[] range = cutterController.getAudioCutterController().getCutRange();

            try {
                SherpaOnnxConfigStore tempConfigStore = configStore != null ? configStore : SherpaOnnxConfigStore.loadDefaultStore();
                File tempDir = tempConfigStore.getAudioTempSubDirectory("live_voiceprints");
                if (!tempDir.exists()) {
                    tempDir.mkdirs();
                }

                String safeUser = userName.replaceAll("[^a-zA-Z0-9_-]", "_");
                String safeVoice = voiceName.replaceAll("[^a-zA-Z0-9_-]", "_");
                File cutFile = new File(tempDir, safeUser + "_" + safeVoice + "_" + System.currentTimeMillis() + ".wav");

                File cutSourceFile = resolveLiveVoicePrintCutSource();
                AudioCutter.cut(cutSourceFile, cutFile, range[0], range[1]);
                VoicePrintLibraryController.getInstance().addVoicePrint(userName, voiceName, cutFile);

                if (!isSidePanelOpen) {
                    openSidePanel();
                }

                showInfo("注册成功", "已完成可视化剪切并注册到声纹库\n文件: " + cutFile.getAbsolutePath());
                dialog.close();
            } catch (Exception ex) {
                showError("现场声纹制作失败: " + ex.getMessage());
            }
        });

        Button cancelBtn = new Button("取消");
        styleButton(cancelBtn, "#95a5a6");
        cancelBtn.setOnAction(e -> dialog.close());

        Button refreshBtn = new Button("刷新区间显示");
        styleButton(refreshBtn, "#5d6d7e");
        refreshBtn.setOnAction(e -> refreshRangeLabel.run());

        HBox actionRow = new HBox(10, registerBtn, refreshBtn, cancelBtn);

        nameRow.getChildren().addAll(userNameField, voiceNameField, useSelectionBtn);
        panel.getChildren().addAll(intro, nameRow, rangeLabel, actionRow);
        return panel;
    }

    private File resolveLiveVoicePrintCutSource() throws Exception {
        if (currentAudioFile == null) {
            throw new IllegalStateException("当前没有可剪切的音频文件。");
        }
        AudioSourceResolver.ResolvedAudio resolvedAudio =
                audioSourceResolver.resolveForProcessing(currentAudioFile, "live_voiceprints/source");
        File workingFile = resolvedAudio.getWorkingFile();
        if (workingFile == null || !workingFile.exists()) {
            throw new IllegalStateException("无法准备现场声纹剪切所需的标准 WAV 文件。");
        }
        return workingFile;
    }

    private void openConfigDialog() {
        if (hostLayer == null || hostLayer.getScene() == null) {
            return;
        }

        if (configStore == null) {
            reloadConfigStore(false);
            if (configStore == null) {
                showError("当前无法打开配置，请先检查 sherpa_onnx.properties。");
                return;
            }
        }

        new SherpaConfigDialog(hostLayer.getScene().getWindow(), configStore, updatedStore -> {
            configStore = updatedStore;
            if (statusLabel != null) {
                statusLabel.setText(buildConfigSummary(updatedStore));
            }
        }).show();
    }

    private void reloadConfigStore(boolean showMessage) {
        try {
            configStore = SherpaOnnxConfigStore.loadDefaultStore();
            if (showMessage) {
                showInfo("配置已刷新", "已从 sherpa_onnx.properties 重新加载配置。");
            }
        } catch (Exception e) {
            configStore = null;
            if (showMessage) {
                showError("重新加载配置失败: " + e.getMessage());
            }
        }
    }

    private void configureAudioChooser(FileChooser chooser, String title) {
        chooser.setTitle(title);
        chooser.getExtensionFilters().clear();
        if (configStore != null) {
            chooser.getExtensionFilters().add(configStore.buildAudioExtensionFilter());
        } else {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("音频文件", Arrays.asList("*.wav", "*.mp3", "*.m4a", "*.aac"))
            );
        }
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("所有文件", "*.*"));
    }

    private String buildConfigSummary(SherpaOnnxConfigStore store) {
        if (store == null) {
            return "配置已保存并应用";
        }

        double vadThreshold = parseConfigDouble(store.getValue("vad.threshold"), 0.55);
        double speakerThreshold = parseConfigDouble(store.getValue("speaker.clusteringThreshold"), 0.5);
        double hotwordScore = parseConfigDouble(store.getValue("conformer.hotwordsScore"), 30.0);

        String segmentationMode;
        if (vadThreshold >= 0.60) {
            segmentationMode = "静音分段偏保守";
        } else if (vadThreshold <= 0.50) {
            segmentationMode = "静音分段偏灵敏";
        } else {
            segmentationMode = "静音分段平衡";
        }

        String speakerMode;
        if (speakerThreshold <= 0.46) {
            speakerMode = "多人区分偏敏感";
        } else if (speakerThreshold >= 0.55) {
            speakerMode = "多人区分偏合并";
        } else {
            speakerMode = "多人区分平衡";
        }

        String keywordMode;
        if (hotwordScore >= 30) {
            keywordMode = "关键词增强很强";
        } else if (hotwordScore >= 15) {
            keywordMode = "关键词增强较强";
        } else if (hotwordScore <= 5) {
            keywordMode = "关键词增强较弱";
        } else {
            keywordMode = "关键词增强常规";
        }

        return "配置已保存并应用：" + segmentationMode + "，" + speakerMode + "，" + keywordMode;
    }

    private double parseConfigDouble(String raw, double defaultValue) {
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private double parseManualTime(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入要跳转的时间，例如 03:15。");
        }

        String[] parts = raw.trim().split(":");
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException("时间格式应为 mm:ss 或 hh:mm:ss。");
        }

        int totalSeconds = 0;
        for (String part : parts) {
            if (!part.matches("\\d{1,2}")) {
                throw new IllegalArgumentException("时间格式应为数字，例如 03:15。");
            }
            totalSeconds = totalSeconds * 60 + Integer.parseInt(part);
        }
        return totalSeconds;
    }

    private void exportResults() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("导出识别结果");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("文本文件", "*.txt")
        );
        chooser.setInitialFileName("会议识别结果.txt");

        File file = chooser.showSaveDialog(listView.getScene().getWindow());
        if (file != null) {
            try {
                PrintWriter writer = new PrintWriter(file, "UTF-8");
                for (SpeechRecognitionUnit unit : recognitionList) {
                    writer.println(String.format("[%s] %s: %s",
                            unit.getFormattedTime(), unit.getSpeaker(), unit.getContent()));
                }
                writer.close();
                showInfo("导出成功", "结果已保存到: " + file.getAbsolutePath());
            } catch (Exception e) {
                showError("导出失败: " + e.getMessage());
            }
        }
    }

    private void styleButton(Button button, String color) {
        button.setStyle(String.format(
                "-fx-background-color: %s; " +
                        "-fx-text-fill: white; " +
                        "-fx-padding: 10 20; " +
                        "-fx-font-size: 14px; " +
                        "-fx-border-radius: 4; " +
                        "-fx-cursor: hand;",
                color
        ));

        button.addEventFilter(MouseEvent.MOUSE_ENTERED, e ->
                button.setStyle(String.format(
                        "-fx-background-color: derive(%s, -10%%); " +
                                "-fx-text-fill: white; " +
                                "-fx-padding: 10 20; " +
                                "-fx-font-size: 14px; " +
                                "-fx-border-radius: 4; " +
                                "-fx-cursor: hand;",
                        color
                ))
        );

        button.addEventFilter(MouseEvent.MOUSE_EXITED, e ->
                button.setStyle(String.format(
                        "-fx-background-color: %s; " +
                                "-fx-text-fill: white; " +
                                "-fx-padding: 10 20; " +
                                "-fx-font-size: 14px; " +
                                "-fx-border-radius: 4; " +
                                "-fx-cursor: hand;",
                        color
                ))
        );
    }

    private String getInputStyle() {
        return "-fx-background-color: white; -fx-border-color: #dde1e7; -fx-border-radius: 4; -fx-padding: 6; -fx-font-size: 13px;";
    }

    private String getSmallButtonStyle(String color) {
        return "-fx-background-color: " + color + "; "
                + "-fx-text-fill: white; "
                + "-fx-padding: 5 10; "
                + "-fx-font-size: 12px; "
                + "-fx-font-weight: bold; "
                + "-fx-background-radius: 4; "
                + "-fx-border-radius: 4; "
                + "-fx-cursor: hand;";
    }

    private void showError(String message) {
        showAlert("错误", message, Alert.AlertType.ERROR);
    }

    private void showInfo(String title, String message) {
        showAlert(title, message, Alert.AlertType.INFORMATION);
    }

    private enum CacheClearType {
        INTERMEDIATE,
        RECOGNITION_DATABASE,
        VOICE_PRINT_LIBRARY,
        HOTWORDS,
        ALL
    }

    private void clearCacheByType(String title, String message, CacheClearType clearType) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle(title);
        confirmAlert.setHeaderText(null);
        confirmAlert.setContentText(message);

        Optional<javafx.scene.control.ButtonType> result = confirmAlert.showAndWait();
        if (!result.isPresent() || result.get() != javafx.scene.control.ButtonType.OK) {
            return;
        }

        try {
            CacheMaintenanceService service = new CacheMaintenanceService();
            CacheMaintenanceService.CleanupSummary totalSummary = new CacheMaintenanceService.CleanupSummary();
            StringBuilder detail = new StringBuilder();

            if (clearType == CacheClearType.INTERMEDIATE || clearType == CacheClearType.ALL) {
                CacheMaintenanceService.CleanupSummary summary = service.clearIntermediateFiles(configStore);
                totalSummary.add(summary);
                appendCleanupDetail(detail, "中转文件", summary);
            }

            if (clearType == CacheClearType.RECOGNITION_DATABASE || clearType == CacheClearType.ALL) {
                CacheMaintenanceService.CleanupSummary summary = service.clearRecognitionDatabases(configStore);
                totalSummary.add(summary);
                appendCleanupDetail(detail, "转译结果数据库", summary);
            }

            if (clearType == CacheClearType.VOICE_PRINT_LIBRARY || clearType == CacheClearType.ALL) {
                boolean reopenVoicePrintPanel = isSidePanelVisible()
                        && currentRightPanelKind == RightPanelKind.VOICE_PRINT_LIBRARY;
                if (reopenVoicePrintPanel) {
                    closeSidePanel();
                }
                CacheMaintenanceService.CleanupSummary summary = service.clearVoicePrintLibraryFiles(configStore);
                totalSummary.add(summary);
                appendCleanupDetail(detail, "声纹库文件", summary);
                if (reopenVoicePrintPanel) {
                    Platform.runLater(this::openSidePanel);
                }
            }

            if (clearType == CacheClearType.HOTWORDS || clearType == CacheClearType.ALL) {
                CacheMaintenanceService.CleanupSummary summary = service.clearHotWordsFiles(configStore);
                totalSummary.add(summary);
                File defaultHotWordsFile = ensureDefaultHotWordsFile();
                applyHotWordsFile(defaultHotWordsFile);
                appendCleanupDetail(detail, "热词词表", summary);
                appendExtraDetail(detail, "热词词表已恢复为默认样例：" + defaultHotWordsFile.getAbsolutePath());
            }

            showInfo("清理完成",
                    detail.toString().trim()
                            + "\n\n合计删除文件 "
                            + totalSummary.getRemovedFiles()
                            + " 个，删除目录 "
                            + totalSummary.getRemovedDirectories()
                            + " 个。");
        } catch (Exception ex) {
            showError("清理缓存失败: " + ex.getMessage());
        }
    }

    private void appendCleanupDetail(StringBuilder detail,
                                     String label,
                                     CacheMaintenanceService.CleanupSummary summary) {
        if (detail.length() > 0) {
            detail.append("\n");
        }
        detail.append(label)
                .append("已清理：删除文件 ")
                .append(summary.getRemovedFiles())
                .append(" 个，删除目录 ")
                .append(summary.getRemovedDirectories())
                .append(" 个。");
    }

    private void appendExtraDetail(StringBuilder detail, String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        if (detail.length() > 0) {
            detail.append("\n");
        }
        detail.append(message.trim());
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    class CenterPanel extends VBox {
        public CenterPanel() {
            super(10);
            setPadding(new Insets(10, 0, 0, 0));

            Label titleLabel = new Label("识别结果列表（按语音片段分段）");
            titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

            HBox listHeader = new HBox(10);
            listHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            Pane headerSpacer = new Pane();
            HBox.setHgrow(headerSpacer, Priority.ALWAYS);
            listHeader.getChildren().addAll(titleLabel, headerSpacer, createRecognitionPagingBar());

            listView = new ListView<>();
            listView.setItems(displayList);
            listView.setCellFactory(param -> createRecognitionCell());
            listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
            listView.setStyle(
                    "-fx-background-color: white; " +
                            "-fx-border-color: #dde1e7; " +
                            "-fx-border-radius: 4; " +
                            "-fx-background-radius: 4; " +
                            "-fx-padding: 6;"
            );
            listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                final boolean triggeredBySyncSelection = suppressSelectionSeek;
                Platform.runLater(() -> {
                    if (listView == null || listView.getScene() == null) {
                        return;
                    }
                    SpeechRecognitionUnit currentSelected = listView.getSelectionModel().getSelectedItem();
                    if (currentSelected != newVal) {
                        return;
                    }
                    if (triggeredBySyncSelection) {
                        highlightWaveformSelectionOnly(newVal);
                    } else {
                        locateWaveformToUnit(newVal);
                    }
                });
            });
            VBox.setVgrow(listView, Priority.ALWAYS);

            VBox previewPanel = createPreviewPanel();
            getChildren().addAll(listHeader, listView, previewPanel);
        }

        private HBox createRecognitionPagingBar() {
            HBox pageBar = new HBox(8);
            pageBar.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

            Label pageSizeLabel = new Label("每页");
            pageSizeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #5d6d7e;");

            recognitionPageSizeBox = new ComboBox<>();
            recognitionPageSizeBox.setItems(FXCollections.observableArrayList(10, 20, 50, 100));
            recognitionPageSizeBox.setValue(recognitionPageSize);
            recognitionPageSizeBox.setPrefWidth(76);
            recognitionPageSizeBox.setTooltip(new Tooltip("设置识别结果每页展示多少条，减少长列表滚动压力。"));
            recognitionPageSizeBox.setOnAction(e -> {
                Integer value = recognitionPageSizeBox.getValue();
                recognitionPageSize = value == null ? DEFAULT_RECOGNITION_PAGE_SIZE : Math.max(1, value);
                recognitionPageIndex = 0;
                updateRecognitionPageItems();
            });

            recognitionPrevPageBtn = new Button("上一页");
            recognitionPrevPageBtn.setStyle(getSmallButtonStyle("#5d6d7e"));
            recognitionPrevPageBtn.setTooltip(new Tooltip("切换到上一页识别片段。"));
            recognitionPrevPageBtn.setOnAction(e -> {
                recognitionPageIndex--;
                updateRecognitionPageItems();
            });

            recognitionNextPageBtn = new Button("下一页");
            recognitionNextPageBtn.setStyle(getSmallButtonStyle("#5d6d7e"));
            recognitionNextPageBtn.setTooltip(new Tooltip("切换到下一页识别片段。"));
            recognitionNextPageBtn.setOnAction(e -> {
                recognitionPageIndex++;
                updateRecognitionPageItems();
            });

            recognitionPageLabel = new Label("第 0 / 0 页，共 0 条");
            recognitionPageLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #5d6d7e;");

            pageBar.getChildren().addAll(pageSizeLabel, recognitionPageSizeBox, recognitionPrevPageBtn, recognitionPageLabel, recognitionNextPageBtn);
            updateRecognitionPageItems();
            return pageBar;
        }

        private VBox createPreviewPanel() {
            VBox panel = new VBox(8);
            panel.setPadding(new Insets(10));
            panel.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dde1e7; -fx-border-radius: 6; -fx-background-radius: 6;");

            Label previewTitle = new Label("音频定位与预览（选中列表后会自动定位到下方波形）");
            previewTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

            previewWaveformCanvas = new WaveformCanvas(960, 150);
            previewProgressSlider = new Slider();
            previewProgressSlider.setShowTickMarks(false);
            previewProgressSlider.setShowTickLabels(false);
            previewProgressSlider.setTooltip(new Tooltip("拖动后松开鼠标，可跳转到指定播放位置。"));
            previewProgressSlider.setOnMouseReleased(e -> seekPreviewToSlider());

            previewTimeLabel = new Label("00:00 / 00:00");
            previewTimeLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 13px; -fx-text-fill: #495057;");

            HBox controls = new HBox(10);
            controls.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            previewPlayBtn = new Button("播放");
            previewPlayBtn.setTooltip(new Tooltip("播放或继续播放当前音频。"));
            previewPlayBtn.setOnAction(e -> togglePreviewPlay());

            previewPauseBtn = new Button("暂停");
            previewPauseBtn.setTooltip(new Tooltip("暂停当前音频播放。"));
            previewPauseBtn.setOnAction(e -> pausePreview());
            updatePreviewPlaybackButtonState(false);

            previewBackwardBtn = new Button("快退");
            styleButton(previewBackwardBtn, "#f39c12");
            previewBackwardBtn.setTooltip(new Tooltip("向前回退 5 秒。"));
            previewBackwardBtn.setOnAction(e -> seekPreviewBy(-5));

            previewForwardBtn = new Button("快进");
            styleButton(previewForwardBtn, "#f39c12");
            previewForwardBtn.setTooltip(new Tooltip("向后快进 5 秒。"));
            previewForwardBtn.setOnAction(e -> seekPreviewBy(5));

            Label jumpLabel = new Label("跳转到");
            jumpLabel.setStyle("-fx-text-fill: #495057; -fx-font-size: 12px;");

            previewJumpField = new TextField();
            previewJumpField.setPromptText("例如 03:15");
            previewJumpField.setPrefWidth(92);
            previewJumpField.setStyle(getInputStyle());
            previewJumpField.setTooltip(new Tooltip("输入 mm:ss 或 hh:mm:ss 后回车，可跳转到指定时间。"));
            previewJumpField.setOnAction(e -> jumpPreviewFromField());

            Button jumpBtn = new Button("定位");
            styleButton(jumpBtn, "#5d6d7e");
            jumpBtn.setTooltip(new Tooltip("跳转到左侧输入的时间点。"));
            jumpBtn.setOnAction(e -> jumpPreviewFromField());

            controls.getChildren().addAll(
                    previewPlayBtn,
                    previewPauseBtn,
                    previewBackwardBtn,
                    previewForwardBtn,
                    previewTimeLabel,
                    jumpLabel,
                    previewJumpField,
                    jumpBtn
            );
            panel.getChildren().addAll(previewTitle, previewWaveformCanvas, previewProgressSlider, controls);
            Platform.runLater(() -> updatePreviewWidthFromScene());
            return panel;
        }

        private ListCell<SpeechRecognitionUnit> createRecognitionCell() {
            return new ListCell<SpeechRecognitionUnit>() {
                private final VBox container;
                private final HBox headerBox;
                private final Label speakerLabel;
                private final Label timeLabel;
                private final Label contentLabel;
                private final Label contentHintLabel;
                private final VBox contentBox;
                private final Rectangle contentClip;
                private final Button editButton;
                private boolean contentHovered = false;

                {
                    container = new VBox(8);
                    container.setPadding(new Insets(12));
                    container.setStyle(
                            "-fx-background-color: white; " +
                                    "-fx-border-color: #e0e0e0; " +
                                    "-fx-border-width: 1; " +
                                    "-fx-border-radius: 6; " +
                                    "-fx-background-radius: 6; " +
                                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 8, 0, 0, 3);"
                    );

                    headerBox = new HBox(12);
                    headerBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                    speakerLabel = new Label();
                    speakerLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-font-size: 14px;");

                    timeLabel = new Label();
                    timeLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 12px;");

                    Pane spacer = new Pane();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    editButton = new Button("✂ 编辑");
                    editButton.setStyle(getSmallButtonStyle("#2980b9"));
                    editButton.setTooltip(new Tooltip("编辑这一条识别结果"));
                    editButton.setOnAction(e -> {
                        SpeechRecognitionUnit current = getItem();
                        if (current != null && !isEmpty()) {
                            getListView().getSelectionModel().select(current);
                            openRecognitionEditor(current);
                        }
                        e.consume();
                    });
                    headerBox.getChildren().addAll(speakerLabel, timeLabel, spacer, editButton);

                    contentLabel = new Label();
                    contentLabel.setWrapText(true);
                    contentLabel.setStyle("-fx-text-fill: #34495e; -fx-font-size: 13px; -fx-line-spacing: 1.5;");
                    contentClip = new Rectangle();
                    contentClip.widthProperty().bind(contentLabel.widthProperty());

                    contentHintLabel = new Label("内容较长，鼠标悬浮可查看全文");
                    contentHintLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 11px;");
                    contentHintLabel.setVisible(false);
                    contentHintLabel.setManaged(false);

                    contentBox = new VBox(4, contentLabel, contentHintLabel);
                    contentBox.setOnMouseEntered(e -> {
                        contentHovered = true;
                        updateContentPreview(true);
                    });
                    contentBox.setOnMouseExited(e -> {
                        contentHovered = false;
                        updateContentPreview(false);
                    });
                    container.widthProperty().addListener((obs, oldVal, newVal) ->
                            Platform.runLater(() -> updateContentPreview(contentHovered))
                    );

                    container.getChildren().addAll(headerBox, contentBox);
                    container.setCursor(Cursor.HAND);
                }

                @Override
                protected void updateItem(SpeechRecognitionUnit unit, boolean empty) {
                    super.updateItem(unit, empty);

                    if (empty || unit == null) {
                        setGraphic(null);
                        setContextMenu(null);
                        setStyle("-fx-background-color: transparent;");
                        return;
                    }

                    speakerLabel.setText(unit.getSpeaker() == null || unit.getSpeaker().trim().isEmpty()
                            ? "未标注发言人"
                            : unit.getSpeaker());
                    timeLabel.setText("片段时间: " + unit.getFormattedTime());
                    contentLabel.setText(unit.getContent() == null ? "" : unit.getContent());

                    setGraphic(container);
                    setContextMenu(null);
                    applySelectionStyle(isSelected(), unit == translatingUnit);
                    Platform.runLater(() -> updateContentPreview(contentHovered));
                }

                @Override
                public void updateSelected(boolean selected) {
                    super.updateSelected(selected);
                    if (getItem() != null && !isEmpty()) {
                        applySelectionStyle(selected, getItem() == translatingUnit);
                    }
                }

                private void applySelectionStyle(boolean selected, boolean translating) {
                    setStyle("-fx-background-color: transparent; -fx-padding: 6;");
                    if (selected) {
                        container.setStyle(
                                "-fx-background-color: #f3f8ff; " +
                                        "-fx-border-color: #1f7ae0; " +
                                        "-fx-border-width: 3; " +
                                        "-fx-border-radius: 6; " +
                                        "-fx-background-radius: 6; " +
                                        "-fx-effect: dropshadow(gaussian, rgba(31,122,224,0.18), 10, 0, 0, 2);"
                        );
                    } else if (translating) {
                        container.setStyle(
                                "-fx-background-color: #fff8f0; " +
                                        "-fx-border-color: #f39c12; " +
                                        "-fx-border-width: 3; " +
                                        "-fx-border-radius: 6; " +
                                        "-fx-background-radius: 6; " +
                                        "-fx-effect: dropshadow(gaussian, rgba(243,156,18,0.2), 10, 0, 0, 2);"
                        );
                    } else {
                        container.setStyle(
                                "-fx-background-color: white; " +
                                        "-fx-border-color: #e0e0e0; " +
                                        "-fx-border-width: 1; " +
                                        "-fx-border-radius: 6; " +
                                        "-fx-background-radius: 6; " +
                                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 8, 0, 0, 3);"
                        );
                    }
                }

                private void updateContentPreview(boolean expand) {
                    if (getItem() == null || isEmpty()) {
                        return;
                    }

                    String text = contentLabel.getText() == null ? "" : contentLabel.getText();
                    double availableWidth = contentLabel.getWidth();
                    if (availableWidth <= 0) {
                        availableWidth = container.getWidth() - 28;
                    }
                    if (availableWidth <= 0 && listView != null) {
                        availableWidth = listView.getWidth() - 70;
                    }
                    if (availableWidth <= 0) {
                        availableWidth = 520;
                    }

                    Text helper = new Text(text);
                    helper.setFont(contentLabel.getFont());
                    helper.setWrappingWidth(Math.max(220, availableWidth));
                    double fullHeight = helper.getLayoutBounds().getHeight();
                    double collapsedHeight = CONTENT_PREVIEW_MAX_LINES * CONTENT_LINE_HEIGHT;
                    boolean shouldCollapse = fullHeight > collapsedHeight + 4;

                    if (shouldCollapse && !expand) {
                        contentClip.setHeight(collapsedHeight);
                        contentLabel.setClip(contentClip);
                        contentLabel.setMaxHeight(collapsedHeight);
                        contentLabel.setPrefHeight(collapsedHeight);
                        contentHintLabel.setVisible(true);
                        contentHintLabel.setManaged(true);
                    } else {
                        contentLabel.setClip(null);
                        contentLabel.setMaxHeight(Region.USE_COMPUTED_SIZE);
                        contentLabel.setPrefHeight(Region.USE_COMPUTED_SIZE);
                        contentHintLabel.setVisible(false);
                        contentHintLabel.setManaged(false);
                    }
                }
            };
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
