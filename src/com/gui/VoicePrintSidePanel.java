package com.gui;

import com.controller.VoicePrintLibraryController;
import com.model.VoicePrint;
import com.recognition.SherpaOnnxConfigStore;
import javafx.animation.TranslateTransition;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VoicePrintSidePanel extends VBox {

    private static final SimpleDateFormat LIST_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static final SimpleDateFormat DETAIL_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final VoicePrintLibraryController voicePrintLibraryController;
    private final ObservableList<VoicePrintListItem> displayVoicePrintList = FXCollections.observableArrayList();
    private final List<VoicePrintListItem> filteredVoicePrintList = new ArrayList<>();
    private final Set<String> collapsedUserGroups = new LinkedHashSet<>();
    private final Set<String> knownUserGroups = new LinkedHashSet<>();

    private ListView<VoicePrintListItem> voicePrintListView;
    private TextField userNameField;
    private TextField voiceNameField;
    private Label filePathLabel;
    private Button registerBtn;
    private Button editBtn;
    private Button deleteBtn;
    private Button openLocationBtn;
    private Button clearAllBtn;
    private CheckBox groupCheckBox;
    private TextField userSearchField;
    private Button prevPageBtn;
    private Button nextPageBtn;
    private Label pageInfoLabel;
    private ComboBox<Integer> pageSizeBox;
    private int pageIndex = 0;
    private int pageSize = DEFAULT_PAGE_SIZE;

    private Label selectedIdValue;
    private Label selectedUserValue;
    private Label selectedVoiceValue;
    private Label selectedPathValue;
    private Label selectedTimeValue;

    private File currentAudioFile;
    private final BorderPane parentContainer;
    private Runnable onCloseCallback;

    public VoicePrintSidePanel(BorderPane container, Pane slidePane) {
        this.parentContainer = container;
        this.voicePrintLibraryController = VoicePrintLibraryController.getInstance();
        initializeComponents();
    }

    public void setOnCloseCallback(Runnable callback) {
        this.onCloseCallback = callback;
    }

    private void initializeComponents() {
        setPadding(new Insets(15));
        setSpacing(15);
        setFillWidth(true);
        setStyle("-fx-background-color: white;");
        setMinWidth(220);
        setPrefWidth(420);
        setMaxWidth(Double.MAX_VALUE);

        HBox titleBox = createTitleBar();
        VBox formBox = createRegistrationForm();
        VBox listBox = createVoicePrintList();

        Separator separator = new Separator();
        getChildren().addAll(titleBox, formBox, separator, listBox);

        voicePrintLibraryController.getVoicePrintList().addListener(
                (ListChangeListener<VoicePrint>) change -> refreshVoicePrintList()
        );
    }

    private HBox createTitleBar() {
        HBox titleBox = new HBox(10);
        titleBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label titleLabel = new Label("声纹库管理");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("×");
        closeBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 16px; -fx-padding: 5 10; -fx-border-radius: 4; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> closeWithAnimation());

        titleBox.getChildren().addAll(titleLabel, spacer, closeBtn);
        return titleBox;
    }

    private VBox createRegistrationForm() {
        VBox formBox = new VBox(12);
        formBox.setPadding(new Insets(10));
        formBox.setStyle("-fx-background-color: #f8f9fa; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label formTitle = new Label("注册新声纹");
        formTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #495057;");

        HBox userBox = new HBox(10);
        userBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label userLabel = new Label("用户名:");
        userLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        userNameField = new TextField();
        userNameField.setPromptText("请输入用户名");
        userNameField.setMaxWidth(Double.MAX_VALUE);
        userNameField.setStyle(getInputStyle());
        userNameField.setTooltip(new Tooltip("同一用户可以注册多条声纹样本，后续匹配时会按用户汇总。"));
        userNameField.textProperty().addListener((obs, oldVal, newVal) -> checkCanRegister());
        HBox.setHgrow(userNameField, Priority.ALWAYS);
        userBox.getChildren().addAll(userLabel, userNameField);

        HBox voiceBox = new HBox(10);
        voiceBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label voiceLabel = new Label("声纹名:");
        voiceLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        voiceNameField = new TextField();
        voiceNameField.setPromptText("例如：正式发言、电话语音");
        voiceNameField.setMaxWidth(Double.MAX_VALUE);
        voiceNameField.setStyle(getInputStyle());
        voiceNameField.setTooltip(new Tooltip("给这条声纹样本起一个便于识别的名称。"));
        voiceNameField.textProperty().addListener((obs, oldVal, newVal) -> checkCanRegister());
        HBox.setHgrow(voiceNameField, Priority.ALWAYS);
        voiceBox.getChildren().addAll(voiceLabel, voiceNameField);

        VBox fileBox = new VBox(8);
        Label fileLabel = new Label("音频样本:");
        fileLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        HBox filePathBox = new HBox(10);
        filePathBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        filePathLabel = new Label("未选择文件");
        filePathLabel.setWrapText(true);
        filePathLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");
        filePathLabel.setMaxWidth(Double.MAX_VALUE);
        filePathLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        filePathLabel.setTooltip(new Tooltip("尚未选择声纹样本文件"));
        HBox.setHgrow(filePathLabel, Priority.ALWAYS);

        Button browseBtn = new Button("选择音频");
        browseBtn.setStyle(getButtonStyle("#9b59b6"));
        browseBtn.setTooltip(new Tooltip("选择用于注册声纹的音频样本。"));
        browseBtn.setOnMouseClicked(e -> selectAudioFile());

        filePathBox.getChildren().addAll(filePathLabel, browseBtn);
        fileBox.getChildren().addAll(fileLabel, filePathBox);

        registerBtn = new Button("注册声纹");
        registerBtn.setStyle(getButtonStyle("#27ae60"));
        registerBtn.setPrefWidth(Double.MAX_VALUE);
        registerBtn.setDisable(true);
        registerBtn.setTooltip(new Tooltip("将当前用户、声纹名和音频样本注册到声纹库。"));
        registerBtn.setOnMouseClicked(e -> registerVoicePrint());

        formBox.getChildren().addAll(formTitle, userBox, voiceBox, fileBox, registerBtn);
        return formBox;
    }

    private VBox createVoicePrintList() {
        VBox listBox = new VBox(10);
        VBox.setVgrow(listBox, Priority.ALWAYS);

        HBox listHeader = new HBox(10);
        listHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label listTitle = new Label("已注册声纹");
        listTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #495057;");

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        groupCheckBox = new CheckBox("按用户分组");
        groupCheckBox.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-cursor: hand;");
        groupCheckBox.setTooltip(new Tooltip("按用户名折叠展示声纹，适合用户较多时浏览。"));
        groupCheckBox.setSelected(true);
        groupCheckBox.setOnAction(e -> {
            if (groupCheckBox.isSelected()) {
                knownUserGroups.clear();
                collapsedUserGroups.clear();
            }
            refreshVoicePrintList();
        });

        clearAllBtn = new Button("清空全部");
        clearAllBtn.setStyle(getButtonStyle("#e74c3c"));
        clearAllBtn.setDisable(true);
        clearAllBtn.setTooltip(new Tooltip("清空声纹库中的全部注册声纹。"));
        clearAllBtn.setOnMouseClicked(e -> clearAllVoicePrints());

        listHeader.getChildren().addAll(listTitle, groupCheckBox, spacer, clearAllBtn);

        HBox searchBar = new HBox(8);
        searchBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label searchLabel = new Label("按用户搜索");
        searchLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #5d6d7e;");
        userSearchField = new TextField();
        userSearchField.setPromptText("输入用户名");
        userSearchField.setStyle(getInputStyle());
        userSearchField.setMaxWidth(Double.MAX_VALUE);
        userSearchField.setTooltip(new Tooltip("按用户名搜索声纹库，支持输入部分名称。"));
        userSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            pageIndex = 0;
            refreshVoicePrintList();
        });
        Button clearSearchBtn = new Button("清空");
        clearSearchBtn.setStyle(getSmallButtonStyle("#95a5a6"));
        clearSearchBtn.setTooltip(new Tooltip("清空用户搜索条件。"));
        clearSearchBtn.setOnAction(e -> userSearchField.clear());
        HBox.setHgrow(userSearchField, Priority.ALWAYS);
        searchBar.getChildren().addAll(searchLabel, userSearchField, clearSearchBtn);

        voicePrintListView = new ListView<>();
        voicePrintListView.setItems(displayVoicePrintList);
        voicePrintListView.setCellFactory(param -> createVoicePrintCell());
        voicePrintListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        voicePrintListView.setStyle(
                "-fx-background: #f8f9fa; " +
                        "-fx-border-color: #dee2e6; " +
                        "-fx-selection-bar: #3498db; " +
                        "-fx-selection-bar-non-focused: #bdc3c7;"
        );

        VBox.setVgrow(voicePrintListView, Priority.ALWAYS);

        HBox pageBar = createPageBar();

        editBtn = new Button("编辑选中");
        editBtn.setStyle(getButtonStyle("#2980b9"));
        editBtn.setDisable(true);
        editBtn.setTooltip(new Tooltip("编辑当前选中的声纹信息。"));
        editBtn.setOnMouseClicked(e -> editSelectedVoicePrint());

        deleteBtn = new Button("删除选中");
        deleteBtn.setStyle(getButtonStyle("#e74c3c"));
        deleteBtn.setDisable(true);
        deleteBtn.setTooltip(new Tooltip("删除当前选中的声纹。"));
        deleteBtn.setOnMouseClicked(e -> deleteSelectedVoicePrint());

        openLocationBtn = new Button("打开位置");
        openLocationBtn.setStyle(getButtonStyle("#5d6d7e"));
        openLocationBtn.setDisable(true);
        openLocationBtn.setTooltip(new Tooltip("打开选中声纹音频文件所在位置。"));
        openLocationBtn.setOnMouseClicked(e -> openSelectedVoicePrintLocation());

        HBox actionBar = new HBox(10, editBtn, deleteBtn, openLocationBtn);
        actionBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox detailBox = createDetailPanel();
        listBox.getChildren().addAll(listHeader, searchBar, voicePrintListView, pageBar, detailBox, actionBar);

        voicePrintListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                updateSelectionDetail(newVal)
        );

        refreshVoicePrintList();
        return listBox;
    }

    private HBox createPageBar() {
        HBox pageBar = new HBox(8);
        pageBar.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        Label pageSizeLabel = new Label("每页");
        pageSizeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #5d6d7e;");

        pageSizeBox = new ComboBox<>();
        pageSizeBox.setItems(FXCollections.observableArrayList(10, 20, 50, 100));
        pageSizeBox.setValue(pageSize);
        pageSizeBox.setPrefWidth(74);
        pageSizeBox.setTooltip(new Tooltip("设置声纹库每页展示多少条。"));
        pageSizeBox.setOnAction(e -> {
            Integer value = pageSizeBox.getValue();
            pageSize = value == null ? DEFAULT_PAGE_SIZE : Math.max(1, value);
            pageIndex = 0;
            updatePagedVoicePrintList();
        });

        prevPageBtn = new Button("上一页");
        prevPageBtn.setStyle(getSmallButtonStyle("#5d6d7e"));
        prevPageBtn.setTooltip(new Tooltip("切换到上一页声纹列表。"));
        prevPageBtn.setOnAction(e -> {
            pageIndex--;
            updatePagedVoicePrintList();
        });

        nextPageBtn = new Button("下一页");
        nextPageBtn.setStyle(getSmallButtonStyle("#5d6d7e"));
        nextPageBtn.setTooltip(new Tooltip("切换到下一页声纹列表。"));
        nextPageBtn.setOnAction(e -> {
            pageIndex++;
            updatePagedVoicePrintList();
        });

        pageInfoLabel = new Label("第 0 / 0 页，共 0 条");
        pageInfoLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #5d6d7e;");

        pageBar.getChildren().addAll(pageSizeLabel, pageSizeBox, prevPageBtn, pageInfoLabel, nextPageBtn);
        updatePagedVoicePrintList();
        return pageBar;
    }

    private VBox createDetailPanel() {
        VBox detailBox = new VBox(6);
        detailBox.setPadding(new Insets(10));
        detailBox.setStyle("-fx-background-color: #f8f9fa; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label title = new Label("选中声纹信息");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(8);
        infoGrid.setVgap(6);

        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(68);
        ColumnConstraints valueCol = new ColumnConstraints();
        valueCol.setHgrow(Priority.ALWAYS);
        infoGrid.getColumnConstraints().addAll(labelCol, valueCol);

        selectedIdValue = createDetailValueLabel();
        selectedUserValue = createDetailValueLabel();
        selectedVoiceValue = createDetailValueLabel();
        selectedPathValue = createDetailValueLabel();
        selectedTimeValue = createDetailValueLabel();

        addDetailRow(infoGrid, 0, "ID:", selectedIdValue);
        addDetailRow(infoGrid, 1, "用户:", selectedUserValue);
        addDetailRow(infoGrid, 2, "声纹:", selectedVoiceValue);
        addDetailRow(infoGrid, 3, "文件:", selectedPathValue);
        addDetailRow(infoGrid, 4, "时间:", selectedTimeValue);

        detailBox.getChildren().addAll(title, infoGrid);
        clearDetailPanel();
        return detailBox;
    }

    private Label createDetailValueLabel() {
        Label label = new Label("-");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        label.setStyle("-fx-text-fill: #495057; -fx-font-size: 12px;");
        return label;
    }

    private void addDetailRow(GridPane grid, int rowIndex, String title, Label valueLabel) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #6c757d;");
        grid.add(titleLabel, 0, rowIndex);
        GridPane.setHgrow(valueLabel, Priority.ALWAYS);
        GridPane.setFillWidth(valueLabel, true);
        grid.add(valueLabel, 1, rowIndex);
    }

    private void refreshVoicePrintList() {
        ObservableList<VoicePrint> allPrints = voicePrintLibraryController.getVoicePrintList();
        VoicePrintListItem previousSelection = voicePrintListView.getSelectionModel().getSelectedItem();
        String keyword = userSearchField == null ? "" : userSearchField.getText();

        List<VoicePrintListItem> newItems = new ArrayList<>();
        List<VoicePrint> sortedPrints = new ArrayList<>(allPrints);
        sortedPrints.sort(
                Comparator.comparing(VoicePrint::getUserName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(VoicePrint::getVoiceName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(VoicePrint::getCreateTime)
        );
        sortedPrints = filterVoicePrintsByUser(sortedPrints, keyword);

        if (groupCheckBox.isSelected()) {
            Map<String, List<VoicePrint>> groupedPrints = new LinkedHashMap<>();
            for (VoicePrint voicePrint : sortedPrints) {
                groupedPrints.computeIfAbsent(voicePrint.getUserName(), key -> new ArrayList<>()).add(voicePrint);
            }

            knownUserGroups.retainAll(groupedPrints.keySet());
            collapsedUserGroups.retainAll(groupedPrints.keySet());
            for (String userName : groupedPrints.keySet()) {
                if (!knownUserGroups.contains(userName)) {
                    knownUserGroups.add(userName);
                    collapsedUserGroups.add(userName);
                }
            }

            for (Map.Entry<String, List<VoicePrint>> entry : groupedPrints.entrySet()) {
                boolean collapsed = collapsedUserGroups.contains(entry.getKey());
                newItems.add(VoicePrintListItem.userGroup(entry.getKey(), entry.getValue().size(), collapsed));
                if (!collapsed) {
                    for (VoicePrint voicePrint : entry.getValue()) {
                        newItems.add(VoicePrintListItem.voicePrint(voicePrint));
                    }
                }
            }
        } else {
            knownUserGroups.clear();
            collapsedUserGroups.clear();
            for (VoicePrint voicePrint : sortedPrints) {
                newItems.add(VoicePrintListItem.voicePrint(voicePrint));
            }
        }

        filteredVoicePrintList.clear();
        filteredVoicePrintList.addAll(newItems);
        clearAllBtn.setDisable(allPrints.isEmpty());

        if (previousSelection != null && previousSelection.getType() == VoicePrintListItem.ItemType.VOICE_PRINT) {
            selectByVoicePrintId(previousSelection.getVoicePrint().getId());
        } else if (previousSelection != null && previousSelection.getType() == VoicePrintListItem.ItemType.USER_GROUP) {
            selectByUserGroup(previousSelection.getUserName());
        } else {
            updatePagedVoicePrintList();
            voicePrintListView.getSelectionModel().clearSelection();
            clearDetailPanel();
        }
    }

    private List<VoicePrint> filterVoicePrintsByUser(List<VoicePrint> source, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return source;
        }
        String normalizedKeyword = keyword.trim().toLowerCase();
        List<VoicePrint> result = new ArrayList<>();
        for (VoicePrint voicePrint : source) {
            String userName = voicePrint.getUserName() == null ? "" : voicePrint.getUserName().toLowerCase();
            if (userName.contains(normalizedKeyword)) {
                result.add(voicePrint);
            }
        }
        return result;
    }

    private void updatePagedVoicePrintList() {
        int total = filteredVoicePrintList.size();
        int safePageSize = Math.max(1, pageSize);
        int pageCount = Math.max(1, (total + safePageSize - 1) / safePageSize);
        pageIndex = Math.max(0, Math.min(pageIndex, pageCount - 1));

        int from = Math.min(total, pageIndex * safePageSize);
        int to = Math.min(total, from + safePageSize);
        displayVoicePrintList.setAll(filteredVoicePrintList.subList(from, to));

        if (pageInfoLabel != null) {
            if (total == 0) {
                pageInfoLabel.setText("第 0 / 0 页，共 0 条");
            } else {
                pageInfoLabel.setText("第 " + (pageIndex + 1) + " / " + pageCount + " 页，共 " + total + " 条");
            }
        }
        if (prevPageBtn != null) {
            prevPageBtn.setDisable(total == 0 || pageIndex <= 0);
        }
        if (nextPageBtn != null) {
            nextPageBtn.setDisable(total == 0 || pageIndex >= pageCount - 1);
        }
    }

    private void selectByVoicePrintId(String voicePrintId) {
        for (int i = 0; i < filteredVoicePrintList.size(); i++) {
            VoicePrintListItem item = filteredVoicePrintList.get(i);
            if (item.getType() == VoicePrintListItem.ItemType.VOICE_PRINT
                    && item.getVoicePrint().getId().equals(voicePrintId)) {
                pageIndex = i / Math.max(1, pageSize);
                updatePagedVoicePrintList();
                voicePrintListView.getSelectionModel().select(item);
                voicePrintListView.scrollTo(item);
                return;
            }
        }

        updatePagedVoicePrintList();
        voicePrintListView.getSelectionModel().clearSelection();
        clearDetailPanel();
    }

    private void selectByUserGroup(String userName) {
        if (!groupCheckBox.isSelected()) {
            voicePrintListView.getSelectionModel().clearSelection();
            clearDetailPanel();
            return;
        }

        for (int i = 0; i < filteredVoicePrintList.size(); i++) {
            VoicePrintListItem item = filteredVoicePrintList.get(i);
            if (item.getType() == VoicePrintListItem.ItemType.USER_GROUP && item.getUserName().equals(userName)) {
                pageIndex = i / Math.max(1, pageSize);
                updatePagedVoicePrintList();
                voicePrintListView.getSelectionModel().select(item);
                voicePrintListView.scrollTo(item);
                return;
            }
        }

        updatePagedVoicePrintList();
        voicePrintListView.getSelectionModel().clearSelection();
        clearDetailPanel();
    }

    private ListCell<VoicePrintListItem> createVoicePrintCell() {
        return new ListCell<VoicePrintListItem>() {
            private final VBox container = new VBox(6);
            private final Label titleLabel = new Label();
            private final Label subLabel = new Label();
            private final Label timeLabel = new Label();

            {
                container.setPadding(new Insets(10));
                titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50; -fx-font-size: 14px;");
                subLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12px;");
                timeLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 11px;");
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(VoicePrintListItem item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                    setOnMouseClicked(null);
                    return;
                }

                if (item.getType() == VoicePrintListItem.ItemType.USER_GROUP) {
                    titleLabel.setText((item.isCollapsed() ? "▶ " : "▼ ") + "用户 " + item.getUserName() + "（" + item.getVoiceCount() + " 条声纹）");
                    subLabel.setText(item.isCollapsed() ? "点击展开查看该用户下的具体声纹" : "点击收起该用户下的具体声纹");
                    container.getChildren().setAll(titleLabel, subLabel);
                    setOnMouseClicked(e -> {
                        toggleUserGroup(item.getUserName());
                        e.consume();
                    });
                } else {
                    VoicePrint voicePrint = item.getVoicePrint();
                    titleLabel.setText("声纹 " + voicePrint.getVoiceName());
                    subLabel.setText("用户: " + voicePrint.getUserName());
                    timeLabel.setText("时间: " + LIST_TIME_FORMAT.format(new java.util.Date(voicePrint.getCreateTime())));
                    container.getChildren().setAll(titleLabel, subLabel, timeLabel);
                    setOnMouseClicked(null);
                }

                applySelectionStyle(item, isSelected());
                setGraphic(container);
            }

            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(selected);
                VoicePrintListItem current = getItem();
                if (current != null && !isEmpty()) {
                    applySelectionStyle(current, selected);
                }
            }

            private void applySelectionStyle(VoicePrintListItem item, boolean selected) {
                String baseCellStyle;
                String baseContainerStyle;

                if (item.getType() == VoicePrintListItem.ItemType.USER_GROUP) {
                    baseCellStyle = "-fx-background-color: transparent; -fx-padding: 6 4 2 4;";
                    baseContainerStyle = "-fx-background-color: #eaf3fb; -fx-background-radius: 8; -fx-border-radius: 8;";
                } else {
                    baseCellStyle = "-fx-background-color: transparent; -fx-padding: 4;";
                    baseContainerStyle = "-fx-background-color: #ffffff; -fx-background-radius: 8; -fx-border-radius: 8;";
                }

                if (selected) {
                    container.setStyle(baseContainerStyle + " -fx-border-color: #1f7ae0; -fx-border-width: 3; -fx-background-insets: 0; -fx-border-insets: 0;");
                } else {
                    container.setStyle(baseContainerStyle + " -fx-border-color: #d6dde5; -fx-border-width: 1;");
                }

                setStyle(baseCellStyle);
            }
        };
    }

    private void toggleUserGroup(String userName) {
        if (userName == null || userName.trim().isEmpty()) {
            return;
        }
        if (collapsedUserGroups.contains(userName)) {
            collapsedUserGroups.remove(userName);
        } else {
            collapsedUserGroups.add(userName);
        }
        refreshVoicePrintList();
        selectByUserGroup(userName);
    }

    private void updateSelectionDetail(VoicePrintListItem selected) {
        if (selected == null) {
            editBtn.setDisable(true);
            deleteBtn.setDisable(true);
            openLocationBtn.setDisable(true);
            clearDetailPanel();
            return;
        }

        if (selected.getType() == VoicePrintListItem.ItemType.USER_GROUP) {
            editBtn.setDisable(true);
            deleteBtn.setDisable(true);
            openLocationBtn.setDisable(true);
            selectedIdValue.setText("-");
            selectedUserValue.setText(selected.getUserName());
            selectedVoiceValue.setText("共 " + selected.getVoiceCount() + " 条声纹");
            selectedPathValue.setText("请选择具体声纹条目查看文件信息");
            selectedPathValue.setTooltip(new Tooltip(selectedPathValue.getText()));
            selectedTimeValue.setText("-");
            return;
        }

        VoicePrint voicePrint = selected.getVoicePrint();
        editBtn.setDisable(false);
        deleteBtn.setDisable(false);
        openLocationBtn.setDisable(voicePrint.getFilePath() == null || voicePrint.getFilePath().trim().isEmpty());
        selectedIdValue.setText(voicePrint.getId());
        selectedUserValue.setText(voicePrint.getUserName());
        selectedVoiceValue.setText(voicePrint.getVoiceName());
        selectedPathValue.setText(
                voicePrint.getFilePath() == null || voicePrint.getFilePath().trim().isEmpty()
                        ? "-"
                        : voicePrint.getFilePath()
        );
        selectedPathValue.setTooltip(new Tooltip(selectedPathValue.getText()));
        selectedTimeValue.setText(DETAIL_TIME_FORMAT.format(new java.util.Date(voicePrint.getCreateTime())));
    }

    private void clearDetailPanel() {
        if (openLocationBtn != null) {
            openLocationBtn.setDisable(true);
        }
        selectedIdValue.setText("-");
        selectedUserValue.setText("-");
        selectedVoiceValue.setText("-");
        selectedPathValue.setText("-");
        selectedPathValue.setTooltip(new Tooltip("-"));
        selectedTimeValue.setText("-");
    }

    private void openSelectedVoicePrintLocation() {
        VoicePrintListItem selectedItem = voicePrintListView.getSelectionModel().getSelectedItem();
        if (selectedItem == null || selectedItem.getType() != VoicePrintListItem.ItemType.VOICE_PRINT) {
            showError("请先选中一条具体声纹。");
            return;
        }

        VoicePrint voicePrint = selectedItem.getVoicePrint();
        File audioFile = voicePrint.getAudioFile();
        if (audioFile == null || voicePrint.getFilePath() == null || voicePrint.getFilePath().trim().isEmpty()) {
            showError("这条声纹没有可打开的音频文件路径。");
            return;
        }

        File target = audioFile.exists() ? audioFile : audioFile.getParentFile();
        if (target == null || !target.exists()) {
            showError("文件位置不存在: " + audioFile.getAbsolutePath());
            return;
        }

        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("win") && audioFile.exists()) {
                Runtime.getRuntime().exec(new String[]{"explorer.exe", "/select,", audioFile.getAbsolutePath()});
            } else if (target.isDirectory()) {
                java.awt.Desktop.getDesktop().open(target);
            } else if (target.getParentFile() != null) {
                java.awt.Desktop.getDesktop().open(target.getParentFile());
            }
        } catch (IOException ex) {
            showError("打开文件位置失败: " + ex.getMessage());
        }
    }

    private void selectAudioFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择声纹注册音频");
        chooser.getExtensionFilters().clear();
        try {
            SherpaOnnxConfigStore configStore = SherpaOnnxConfigStore.loadDefaultStore();
            chooser.getExtensionFilters().add(configStore.buildAudioExtensionFilter());
        } catch (Exception e) {
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("音频文件", "*.wav", "*.mp3", "*.m4a", "*.aac")
            );
        }
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("所有文件", "*.*"));

        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file != null) {
            currentAudioFile = file;
            filePathLabel.setText(file.getName());
            filePathLabel.setTooltip(new Tooltip(file.getAbsolutePath()));
            checkCanRegister();
        }
    }

    private void checkCanRegister() {
        boolean canRegister = !userNameField.getText().trim().isEmpty()
                && !voiceNameField.getText().trim().isEmpty()
                && currentAudioFile != null;
        registerBtn.setDisable(!canRegister);
    }

    private void registerVoicePrint() {
        if (currentAudioFile == null) {
            showError("请选择音频文件");
            return;
        }

        String userName = userNameField.getText().trim();
        String voiceName = voiceNameField.getText().trim();

        if (userName.isEmpty() || voiceName.isEmpty()) {
            showError("请填写用户名和声纹名称");
            return;
        }

        voicePrintLibraryController.addVoicePrint(userName, voiceName, currentAudioFile);
        refreshVoicePrintList();

        userNameField.clear();
        voiceNameField.clear();
        filePathLabel.setText("未选择文件");
        currentAudioFile = null;
        registerBtn.setDisable(true);

        showInfo("注册成功", "声纹已成功添加到库中");
    }

    private void editSelectedVoicePrint() {
        VoicePrintListItem selectedItem = voicePrintListView.getSelectionModel().getSelectedItem();
        if (selectedItem == null || selectedItem.getType() != VoicePrintListItem.ItemType.VOICE_PRINT) {
            showError("请先在列表中选中一条具体声纹，再进行编辑。");
            return;
        }

        VoicePrint voicePrint = selectedItem.getVoicePrint();
        Stage dialog = new Stage();
        dialog.initModality(Modality.WINDOW_MODAL);
        if (getScene() != null && getScene().getWindow() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.setTitle("编辑声纹信息");

        VBox root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: #f7f9fc;");

        Label titleLabel = new Label("编辑声纹信息");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label introLabel = new Label("你可以修改用户名、声纹名称，必要时也可以重新指定对应的音频文件。");
        introLabel.setWrapText(true);
        introLabel.setStyle("-fx-text-fill: #5d6d7e; -fx-font-size: 12px;");

        TextField editUserField = new TextField(voicePrint.getUserName());
        editUserField.setPromptText("用户名");
        editUserField.setStyle(getInputStyle());

        TextField editVoiceField = new TextField(voicePrint.getVoiceName());
        editVoiceField.setPromptText("声纹名称");
        editVoiceField.setStyle(getInputStyle());

        Label fileValueLabel = new Label(
                voicePrint.getFilePath() == null || voicePrint.getFilePath().trim().isEmpty()
                        ? "未设置文件"
                        : voicePrint.getFilePath()
        );
        fileValueLabel.setWrapText(true);
        fileValueLabel.setMaxWidth(Double.MAX_VALUE);
        fileValueLabel.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        fileValueLabel.setTooltip(new Tooltip(fileValueLabel.getText()));
        fileValueLabel.setStyle("-fx-text-fill: #495057; -fx-font-size: 12px;");

        final File[] selectedFileHolder = new File[]{
                voicePrint.getAudioFile() != null ? voicePrint.getAudioFile()
                        : (voicePrint.getFilePath() == null || voicePrint.getFilePath().trim().isEmpty()
                        ? null : new File(voicePrint.getFilePath()))
        };

        Button chooseFileBtn = new Button("重新选择文件");
        chooseFileBtn.setStyle(getButtonStyle("#9b59b6"));
        chooseFileBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("选择声纹音频文件");
            chooser.getExtensionFilters().clear();
            try {
                SherpaOnnxConfigStore configStore = SherpaOnnxConfigStore.loadDefaultStore();
                chooser.getExtensionFilters().add(configStore.buildAudioExtensionFilter());
            } catch (Exception ex) {
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("音频文件", "*.wav", "*.mp3", "*.m4a", "*.aac")
                );
            }
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("所有文件", "*.*"));
            File selectedFile = chooser.showOpenDialog(dialog);
            if (selectedFile != null) {
                selectedFileHolder[0] = selectedFile;
                fileValueLabel.setText(selectedFile.getAbsolutePath());
                fileValueLabel.setTooltip(new Tooltip(selectedFile.getAbsolutePath()));
            }
        });

        VBox fileBox = new VBox(8);
        Label fileLabel = new Label("关联音频文件");
        fileLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        fileBox.getChildren().addAll(fileLabel, fileValueLabel, chooseFileBtn);

        Button saveBtn = new Button("保存修改");
        saveBtn.setStyle(getButtonStyle("#27ae60"));
        saveBtn.setOnAction(e -> {
            String newUser = editUserField.getText() == null ? "" : editUserField.getText().trim();
            String newVoice = editVoiceField.getText() == null ? "" : editVoiceField.getText().trim();
            if (newUser.isEmpty() || newVoice.isEmpty()) {
                showError("用户名和声纹名称都不能为空。");
                return;
            }

            File newFile = selectedFileHolder[0];
            if (newFile == null) {
                showError("请为这条声纹指定一个有效的音频文件。");
                return;
            }

            boolean changed = !newUser.equals(voicePrint.getUserName())
                    || !newVoice.equals(voicePrint.getVoiceName())
                    || !newFile.getAbsolutePath().equals(
                    voicePrint.getFilePath() == null ? "" : voicePrint.getFilePath()
            );
            if (!changed) {
                showInfo("未修改", "当前信息和原来一致，无需重复保存。");
                return;
            }

            voicePrint.setUserName(newUser);
            voicePrint.setVoiceName(newVoice);
            voicePrint.setAudioFile(newFile);
            voicePrintLibraryController.updateVoicePrint(voicePrint);
            refreshVoicePrintList();
            selectByVoicePrintId(voicePrint.getId());
            dialog.close();
            showInfo("修改成功", "声纹信息已经更新完成。");
        });

        Button cancelBtn = new Button("取消");
        cancelBtn.setStyle(getButtonStyle("#95a5a6"));
        cancelBtn.setOnAction(e -> dialog.close());

        HBox actionRow = new HBox(10, saveBtn, cancelBtn);

        VBox userBox = new VBox(6, new Label("用户名"), editUserField);
        VBox voiceBox = new VBox(6, new Label("声纹名称"), editVoiceField);
        userBox.getChildren().get(0).setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        voiceBox.getChildren().get(0).setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        root.getChildren().addAll(titleLabel, introLabel, userBox, voiceBox, fileBox, actionRow);
        dialog.setScene(new javafx.scene.Scene(root, 520, 380));
        dialog.showAndWait();
    }

    private void deleteSelectedVoicePrint() {
        VoicePrintListItem selectedItem = voicePrintListView.getSelectionModel().getSelectedItem();
        if (selectedItem == null || selectedItem.getType() != VoicePrintListItem.ItemType.VOICE_PRINT) {
            return;
        }

        VoicePrint selected = selectedItem.getVoicePrint();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认删除");
        alert.setHeaderText(null);
        alert.setContentText("确定要删除 \"" + selected.getUserName() + " - " + selected.getVoiceName() + "\" 吗？");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            voicePrintLibraryController.removeVoicePrint(selected.getId());
        }
    }

    private void clearAllVoicePrints() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认清空");
        alert.setHeaderText(null);
        alert.setContentText("确定要清空所有声纹吗？此操作不可恢复。");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            voicePrintLibraryController.clearLibrary();
            refreshVoicePrintList();
        }
    }

    private void closeWithAnimation() {
        TranslateTransition transition = new TranslateTransition(Duration.millis(300), this);
        double hideOffset = Math.max(getWidth(), getPrefWidth());
        transition.setFromX(0);
        transition.setToX(hideOffset);
        transition.setOnFinished(e -> {
            if (parentContainer != null) {
                parentContainer.setRight(null);
            }
            if (onCloseCallback != null) {
                onCloseCallback.run();
            }
        });
        transition.play();
    }

    private String getButtonStyle(String color) {
        return String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-padding: 8 16; -fx-font-size: 13px; -fx-border-radius: 4; -fx-cursor: hand;",
                color
        );
    }

    private String getSmallButtonStyle(String color) {
        return String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-padding: 5 10; -fx-font-size: 12px; -fx-border-radius: 4; -fx-cursor: hand;",
                color
        );
    }

    private String getInputStyle() {
        return "-fx-background-color: white; -fx-border-color: #dde1e7; -fx-border-radius: 4; -fx-padding: 6; -fx-font-size: 13px;";
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static class VoicePrintListItem {
        private final ItemType type;
        private final VoicePrint voicePrint;
        private final String userName;
        private final int voiceCount;
        private final boolean collapsed;

        private VoicePrintListItem(ItemType type, VoicePrint voicePrint, String userName, int voiceCount, boolean collapsed) {
            this.type = type;
            this.voicePrint = voicePrint;
            this.userName = userName;
            this.voiceCount = voiceCount;
            this.collapsed = collapsed;
        }

        static VoicePrintListItem userGroup(String userName, int voiceCount, boolean collapsed) {
            return new VoicePrintListItem(ItemType.USER_GROUP, null, userName, voiceCount, collapsed);
        }

        static VoicePrintListItem voicePrint(VoicePrint voicePrint) {
            return new VoicePrintListItem(ItemType.VOICE_PRINT, voicePrint, voicePrint.getUserName(), 1, false);
        }

        ItemType getType() {
            return type;
        }

        VoicePrint getVoicePrint() {
            return voicePrint;
        }

        String getUserName() {
            return userName;
        }

        int getVoiceCount() {
            return voiceCount;
        }

        boolean isCollapsed() {
            return collapsed;
        }

        private enum ItemType {
            USER_GROUP,
            VOICE_PRINT
        }
    }
}
