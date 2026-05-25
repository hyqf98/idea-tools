package io.github.ideatools.utils;

import com.intellij.openapi.util.text.StringUtil;
import io.github.ideatools.action.conversion.PropertyNameConverter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
        return StringUtil.isEmpty(str);
    }

    /**
     * Convert underscore style to spaced words (user_name -> user name)
     *
     * @param text input
     * @return words
     */
    public String underscoreToWords(String text) {
        if (StringUtil.isEmpty(text)) {
            return "";
        }
        return Arrays.stream(text.split("_"))
                .filter(s -> !StringUtil.isEmpty(s))
                .collect(Collectors.joining(" "));
    }

    /**
     * Convert camelCase to spaced words (userName -> user name)
     *
     * @param text input
     * @return words
     */
    public String camelToWords(String text) {
        if (StringUtil.isEmpty(text)) {
            return "";
        }
        return Arrays.stream(PropertyNameConverter.toLowerUnderline(text).split("_"))
                .filter(s -> !StringUtil.isEmpty(s))
                .collect(Collectors.joining(" "));
    }

    /**
     * Title-case words from camelCase input (userName -> User Name)
     *
     * @param text input
     * @return title words
     */
    public String camelToTitleWords(String text) {
        if (StringUtil.isEmpty(text)) {
            return "";
        }
        return Arrays.stream(PropertyNameConverter.toLowerUnderline(text).split("_"))
                .filter(s -> !StringUtil.isEmpty(s))
                .map(this::upperFirst)
                .collect(Collectors.joining(" "));
    }

    /**
     * Lower-case words from camelCase input (UserName -> user name)
     *
     * @param text input
     * @return lower words
     */
    public String camelToLowerWords(String text) {
        if (StringUtil.isEmpty(text)) {
            return "";
        }
        return Arrays.stream(PropertyNameConverter.toLowerUnderline(text).split("_"))
                .filter(s -> !StringUtil.isEmpty(s))
                .map(s -> s.toLowerCase())
                .collect(Collectors.joining(" "));
    }

    /**
     * Current datetime string (yyyy-MM-dd HH:mm:ss)
     *
     * @return now string
     */
    public String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Format current datetime by pattern
     *
     * @param pattern date pattern
     * @return formatted date
     */
    public String formatNow(String pattern) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * 将字符串首字母转为大写
     *
     * @param str 字符串
     * @return 首字母大写的字符串
     */
    private String upperFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
