package io.github.easy.tools.utils;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * TemplateUtils
 * Utility methods exposed to Velocity templates for common string/date operations.
 *
 * @author haijun
 * @version 1.0.0
 * @since 1.0.0
 */
public class TemplateUtils {

    /**
     * Check if a string is blank
     *
     * @param str input string
     * @return true if blank
     */
    public boolean isBlank(String str) {
        return StrUtil.isBlank(str);
    }

    /**
     * Convert underscore style to spaced words (user_name -> user name)
     *
     * @param text input
     * @return words
     */
    public String underscoreToWords(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        return Arrays.stream(text.split("_"))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining(" "));
    }

    /**
     * Convert camelCase to spaced words (userName -> user name)
     *
     * @param text input
     * @return words
     */
    public String camelToWords(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        return Arrays.stream(StrUtil.toUnderlineCase(text).split("_"))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining(" "));
    }

    /**
     * Title-case words from camelCase input (userName -> User Name)
     *
     * @param text input
     * @return title words
     */
    public String camelToTitleWords(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        return Arrays.stream(StrUtil.toUnderlineCase(text).split("_"))
                .filter(StrUtil::isNotBlank)
                .map(StrUtil::upperFirst)
                .collect(Collectors.joining(" "));
    }

    /**
     * Lower-case words from camelCase input (UserName -> user name)
     *
     * @param text input
     * @return lower words
     */
    public String camelToLowerWords(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        return Arrays.stream(StrUtil.toUnderlineCase(text).split("_"))
                .filter(StrUtil::isNotBlank)
                .map(String::toLowerCase)
                .collect(Collectors.joining(" "));
    }

    /**
     * Current datetime string (yyyy-MM-dd HH:mm:ss)
     *
     * @return now string
     */
    public String now() {
        return DateUtil.now();
    }

    /**
     * Format current datetime by pattern
     *
     * @param pattern date pattern
     * @return formatted date
     */
    public String formatNow(String pattern) {
        return DateUtil.format(DateUtil.date(), pattern);
    }
}
