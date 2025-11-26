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
}
