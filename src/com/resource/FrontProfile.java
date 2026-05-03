package com.resource;

public class FrontProfile {
    public static final String APP_NAME = "音频处理工具";
    public static final int WINDOW_WIDTH = 1050;
    public static final int WINDOW_HEIGHT = 600;
    public static final String VERSION = "V1.0";
    public static final String AUTHOR = "Brian";
    public static final String OUT_YEAR = "2026";
    public static final String MAIN_VIEW_PATH = "/com/view/main.fxml";
    public static final String FONT_PATH = "/com/resource/font/wqy-microhei.ttf";
    public static final String ABOUT_DESCRIPTION =
            APP_NAME + " " + VERSION + "\n"
                    + "作者: " + AUTHOR + "\n"
                    + "年份: " + OUT_YEAR + "\n\n"
                    + "本系统用于会议音频导入、语音分段、转写识别、发言人区分、"
                    + "声纹库管理与结果导出。\n"
                    + "你可以通过“系统”菜单查看配置说明，也可以通过“缓存管理”菜单"
                    + "清理中转文件、转译结果数据库和声纹库文件。";
}
