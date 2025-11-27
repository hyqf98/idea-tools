package io.github.easy.tools.utils;

import cn.hutool.core.util.StrUtil;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * StrConverter
 *
 * @author java.lang.Object@57ab2144
 * @version 1.0.0
 * @date 2025-11-26 18:01:50
 * @since 1.0.0
 */
public class StrConverter {


    /**
     * 方法首字母大写
     *
     * @param className class name
     * @return String
     */
    public static String firstUpperConverter(String className) {
        return Arrays.stream(StrUtil.toUnderlineCase(className).split("_"))
                .map(StrUtil::upperFirst)
                .collect(Collectors.joining(" "));
    }

    public static String firstLowerConverter(String className) {
        return Arrays.stream(StrUtil.toUnderlineCase(className).split("_"))
                .map(StrUtil::lowerFirst)
                .collect(Collectors.joining(" "));
    }

    /**
     * 处理类名的驼峰命名分割，智能去除常见的单字母前缀
     * 例如：IPage -> Page, APage -> Page, BPage -> Page, QueryDTO -> Query DTO
     * 注意：对于接口命名约定如 IUserService，会保留 I 前缀
     *
     * @param className 类名
     * @return 分割后的名称
     */
    public static String convertClassName(String className) {
        // 处理null值
        if (className == null) {
            return "";
        }

        // 去除常见的单字母前缀（A-Z的大写字母）
        if (className.length() > 1) {
            char firstChar = className.charAt(0);
            char secondChar = className.charAt(1);
            // 如果第一个字符是大写字母，第二个字符也是大写字母
            if (Character.isUpperCase(firstChar) && Character.isUpperCase(secondChar)) {
                // 定义一些有意义的前缀，不应该被移除
                String meaningfulPrefixes = "XML"; // 可以根据需要扩展这个列表
                if (meaningfulPrefixes.indexOf(firstChar) == -1) {
                    // 移除单个大写字母前缀
                    className = className.substring(1);
                }
            }
        }

        // 使用标准的驼峰命名分割方法
        return Arrays.stream(StrUtil.toUnderlineCase(className).split("_"))
                .map(String::toLowerCase)
                .collect(Collectors.joining(" "));
    }
}
