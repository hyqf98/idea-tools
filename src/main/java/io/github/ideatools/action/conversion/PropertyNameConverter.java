package io.github.ideatools.action.conversion;

import java.util.ArrayList;
import java.util.List;

/**
 * 属性名称转换工具类
 * 提供多种格式的属性名称转换功能
 */
public class PropertyNameConverter {

    /**
     * 转换为小写驼峰格式 (lower camel case)
     * 例如: USER_NAME -> userName, user_name -> userName
     *
     * @param name 原始名称
     * @return 小写驼峰格式
     */
    public static String toLowerCamelCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        List<String> words = splitToWords(name);
        if (words.isEmpty()) {
            return name;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            if (i == 0) {
                result.append(word.toLowerCase());
            } else {
                result.append(capitalize(word));
            }
        }

        return result.toString();
    }

    /**
     * 转换为大写驼峰格式 (upper camel case)
     * 例如: user_name -> UserName, USER_NAME -> UserName
     *
     * @param name 原始名称
     * @return 大写驼峰格式
     */
    public static String toUpperCamelCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        List<String> words = splitToWords(name);
        if (words.isEmpty()) {
            return name;
        }

        StringBuilder result = new StringBuilder();
        for (String word : words) {
            result.append(capitalize(word));
        }

        return result.toString();
    }

    /**
     * 转换为小写下划线格式 (lower snake case)
     * 例如: userName -> user_name, UserName -> user_name
     *
     * @param name 原始名称
     * @return 小写下划线格式
     */
    public static String toLowerUnderline(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        return String.join("_", splitToWords(name)).toLowerCase();
    }

    /**
     * 转换为大写下划线格式 (upper snake case)
     * 例如: userName -> USER_NAME, user_name -> USER_NAME
     *
     * @param name 原始名称
     * @return 大写下划线格式
     */
    public static String toUpperUnderline(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }

        return String.join("_", splitToWords(name)).toUpperCase();
    }

    /**
     * 将属性名分割为单词列表
     * <p>
     * 智能处理各种命名风格：
     * - 驼峰命名：userName -> ["user", "name"]
     * - 全大写常量：IDLE -> ["idle"]，RUNNING -> ["running"]
     * - 连续大写：XMLParser -> ["xml", "parser"]
     * - 混合模式：HTTPResponse -> ["http", "response"]
     * </p>
     *
     * @param name 原始名称
     * @return 单词列表
     */
    private static List<String> splitToWords(String name) {
        List<String> words = new ArrayList<>();

        if (name == null || name.isEmpty()) {
            return words;
        }

        // 处理包含下划线的情况
        if (name.contains("_")) {
            String[] parts = name.split("_");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    words.add(part.toLowerCase());
                }
            }
            return words;
        }

        // 检查是否是全大写的常量名（如 IDLE, RUNNING, FINISHED）
        if (isAllUpperCase(name)) {
            words.add(name.toLowerCase());
            return words;
        }

        // 智能处理驼峰命名和混合大小写
        StringBuilder currentWord = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            char nextChar = (i + 1 < name.length()) ? name.charAt(i + 1) : '\0';

            // 当前字符是大写字母
            if (Character.isUpperCase(c)) {
                // 判断是否需要开始新单词：
                // 1. currentWord 不为空 且
                // 2. (下一个字符是小写 或 (下一个字符是大写且当前是单个大写字母结尾))
                // 例如：XMLParser -> "XML" + "Parser"
                // 例如：HTTPResponse -> "HTTP" + "Response"
                // 例如：getHTTPResponse -> "get" + "HTTP" + "Response"
                if (currentWord.length() > 0) {
                    boolean shouldSplit = false;

                    // 如果下一个字符是小写，说明当前大写字母是新单词的开始
                    // 例如 "HTMLParser" 中的 'P' 之前应该分割
                    if (Character.isLowerCase(nextChar)) {
                        shouldSplit = true;
                    }
                    // 如果当前单词只有1个字符且下一个是大写，可能需要继续累积
                    // 例如 "HTTP" 不应该分成 "H", "T", "T", "P"
                    else if (currentWord.length() == 1 && Character.isUpperCase(nextChar)) {
                        // 继续累积连续大写字母
                        shouldSplit = false;
                    }
                    // 如果当前单词有多个字符且全是小写，遇到大写需要分割
                    else if (!isAllUpperCase(currentWord.toString())) {
                        shouldSplit = true;
                    }

                    if (shouldSplit) {
                        words.add(currentWord.toString().toLowerCase());
                        currentWord = new StringBuilder();
                    }
                }
            }

            currentWord.append(c);
        }

        // 添加最后一个单词
        if (currentWord.length() > 0) {
            words.add(currentWord.toString().toLowerCase());
        }

        return words;
    }

    /**
     * 检查字符串是否全部为大写字母
     *
     * @param str 字符串
     * @return 是否全部为大写字母
     */
    private static boolean isAllUpperCase(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isUpperCase(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 首字母大写
     *
     * @param str 字符串
     * @return 首字母大写的字符串
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        return Character.toUpperCase(str.charAt(0)) + str.substring(1).toLowerCase();
    }
}
