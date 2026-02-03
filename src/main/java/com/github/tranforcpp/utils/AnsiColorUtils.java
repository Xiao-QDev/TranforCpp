package com.github.tranforcpp.utils;

/**
 * ANSI颜色代码工具类
 * 提供统一的颜色管理，减少代码重复
 */
public class AnsiColorUtils {

    public static final String RESET = "\033[0m";
    public static final String BLACK = "\033[30m";
    public static final String RED = "\033[31m";
    public static final String GREEN = "\033[32m";
    public static final String YELLOW = "\033[33m";
    public static final String BLUE = "\033[34m";
    public static final String MAGENTA = "\033[35m";
    public static final String CYAN = "\033[36m";
    public static final String WHITE = "\033[37m";

    public static final String COLOR_45 = "\033[38;5;45m";  // 青蓝色
    public static final String COLOR_46 = "\033[38;5;46m";  // 亮绿色
    public static final String COLOR_51 = "\033[38;5;51m";  // 浅青色
    public static final String COLOR_87 = "\033[38;5;87m";  // 青色
    public static final String COLOR_123 = "\033[38;5;123m"; // 浅蓝色
    public static final String COLOR_159 = "\033[38;5;159m"; // 淡蓝色
    public static final String COLOR_195 = "\033[38;5;195m"; // 极淡蓝色
    public static final String COLOR_208 = "\033[38;5;208m"; // 橙色

    public static final String BG_BLACK = "\033[40m";
    public static final String BG_RED = "\033[41m";
    public static final String BG_GREEN = "\033[42m";
    public static final String BG_YELLOW = "\033[43m";
    public static final String BG_BLUE = "\033[44m";
    public static final String BG_MAGENTA = "\033[45m";
    public static final String BG_CYAN = "\033[46m";
    public static final String BG_WHITE = "\033[47m";

    public static final String BOLD = "\033[1m";
    public static final String UNDERLINE = "\033[4m";
    public static final String REVERSE = "\033[7m";

    public static final String[] LOGO_GRADIENT = {
        COLOR_45, COLOR_51, COLOR_87, COLOR_123, COLOR_159, COLOR_195
    };
    
    /**
     * 为文本应用颜色
     * @param text 要着色的文本
     * @param color 颜色代码
     * @return 着色后的文本
     */
    public static String colorize(String text, String color) {
        return color + text + RESET;
    }
    
    /**
     * 创建渐变效果的文本行
     * @param lines 文本行数组
     * @param colors 颜色数组
     * @return 渐变着色的文本行
     */
    public static String[] createGradientText(String[] lines, String[] colors) {
        String[] result = new String[Math.min(lines.length, colors.length)];
        for (int i = 0; i < result.length; i++) {
            result[i] = colorize(lines[i], colors[i]);
        }
        return result;
    }
    
    /**
     * 获取随机颜色（用于调试）
     * @return 随机颜色代码
     */
    public static String getRandomColor() {
        String[] colors = {RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN};
        int index = (int) (Math.random() * colors.length);
        return colors[index];
    }
}