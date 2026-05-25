package io.github.ideatools.service.mybatis.expression;

import com.intellij.openapi.util.text.StringUtil;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * My Batis Expression Parser
 *
 * @author haijun
 * @date 2025-12-18 14:28:35
 * @version 1.0.0
 * @since 1.0.0
 */
public final class MyBatisExpressionParser {

    /**
     * OGNL 变量提取正则 (提取 query.name)
     *
     */
    private static final Pattern OGNL_VARIABLE_PATTERN = Pattern.compile("[a-zA-Z_$][a-zA-Z0-9_$]*(\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*");

    /**
     * MyBatis 占位符提取正则 (提取 #{query.name} 或 ${query.name} 中的内容)
     *
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("[#$]\\{([^}]+)}");

    /**
     * Java关键字集合
     *
     */
    private static final Set<String> JAVA_KEYWORDS = new HashSet<>(Arrays.asList(
            "true", "false", "null", "and", "or", "not", "eq", "ne", "lt", "gt", "le", "ge",
            "instanceof", "new", "class", "void", "return", "if", "else", "for", "while"
    ));

    /**
     * 私有构造函数,防止实例化
     *
     * @since 1.0.0
     */
    private MyBatisExpressionParser() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 从OGNL表达式中提取所有变量
     * <p>
     * 例如: "query.name != null and user.age > 18" 提取出 ["query.name", "user.age"]
     * </p>
     *
     * @param ognlExpression OGNL表达式
     * @return 变量列表
     * @since 1.0.0
     */
    @NotNull
    public static List<ExpressionVariable> extractOgnlVariables(@NotNull String ognlExpression) {
        List<ExpressionVariable> variables = new ArrayList<>();
        Matcher matcher = OGNL_VARIABLE_PATTERN.matcher(ognlExpression);

        while (matcher.find()) {
            String variableChain = matcher.group();

            // 跳过Java关键字和数字
            if (isJavaKeywordOrNumber(variableChain)) {
                continue;
            }

            variables.add(ExpressionVariable.builder()
                    .fullExpression(variableChain)
                    .startOffset(matcher.start())
                    .endOffset(matcher.end())
                    .build());
        }

        return variables;
    }

    /**
     * 从文本中提取占位符表达式
     * <p>
     * 例如: "select * from table where id = #{query.id}" 提取出 ["query.id"]
     * </p>
     *
     * @param text 文本内容
     * @return 占位符变量列表
     * @since 1.0.0
     */
    @NotNull
    public static List<ExpressionVariable> extractPlaceholders(@NotNull String text) {
        List<ExpressionVariable> variables = new ArrayList<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);

        while (matcher.find()) {
            String content = matcher.group(1); // 提取{}中的内容
            if (StringUtil.isNotEmpty(content)) {
                variables.add(ExpressionVariable.builder()
                        .fullExpression(content.trim())
                        .startOffset(matcher.start(1))
                        .endOffset(matcher.end(1))
                        .build());
            }
        }

        return variables;
    }

    /**
     * 提取表达式中最后输入的部分
     * <p>
     * 用于代码补全场景,例如:
     * <ul>
     * <li>"query." -> 返回 "query" 作为根参数</li>
     * <li>"query.user." -> 返回 "query.user" 作为前缀路径</li>
     * <li>"query.user.na" -> 返回 "query.user" 作为前缀, "na" 作为当前输入</li>
     * </ul>
     * </p>
     *
     * @param expression 表达式
     * @return 解析结果
     * @since 1.0.0
     */
    @NotNull
    public static ExpressionParseResult parseForCompletion(@NotNull String expression) {
        // 清理表达式,移除#{}包裹
        String cleanExpression = StringUtil.trim(expression)
                .replace("#{", "")
                .replace("${", "")
                .replace("}", "");

        if (StringUtil.isEmpty(cleanExpression)) {
            return ExpressionParseResult.builder()
                    .rootParam("")
                    .prefixPath("")
                    .currentInput("")
                    .parts(new String[0])
                    .build();
        }

        // 使用hutool的split方法,保留空字符串
        String[] parts = cleanExpression.split("\\.", -1);

        if (parts.length == 0) {
            return ExpressionParseResult.builder()
                    .rootParam("")
                    .prefixPath("")
                    .currentInput(cleanExpression)
                    .parts(parts)
                    .build();
        }

        String rootParam = parts[0];

        // 如果只有一个部分(没有点号)
        if (parts.length == 1) {
            return ExpressionParseResult.builder()
                    .rootParam(rootParam)
                    .prefixPath("")
                    .currentInput("")
                    .parts(parts)
                    .build();
        }

        // 构建前缀路径(除去最后一部分)
        List<String> prefixParts = new ArrayList<>();
        for (int i = 0; i < parts.length - 1; i++) {
            prefixParts.add(parts[i]);
        }
        String prefixPath = String.join(".", prefixParts);

        // 最后一部分是当前正在输入的内容
        String currentInput = parts[parts.length - 1];

        return ExpressionParseResult.builder()
                .rootParam(rootParam)
                .prefixPath(prefixPath)
                .currentInput(currentInput)
                .parts(parts)
                .build();
    }

    /**
     * 判断是否为Java关键字或数字
     *
     * @param text 文本
     * @return true如果是关键字或数字
     * @since 1.0.0
     */
    private static boolean isJavaKeywordOrNumber(@NotNull String text) {
        if (JAVA_KEYWORDS.contains(text.toLowerCase())) {
            return true;
        }

        // 检查是否为数字
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 表达式变量
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-18
     * @since 1.0.0
     */
    @Data
    @Builder
    public static class ExpressionVariable {
        /**
         * 完整表达式 (如: query.user.name)
         *
         */
        private String fullExpression;

        /**
         * 在原文本中的起始偏移量
         *
         */
        private int startOffset;

        /**
         * 在原文本中的结束偏移量
         *
         */
        private int endOffset;
    }

    /**
     * 表达式解析结果
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-18
     * @since 1.0.0
     */
    @Data
    @Builder
    public static class ExpressionParseResult {
        /**
         * 根参数名 (如: query)
         *
         */
        private String rootParam;

        /**
         * 前缀路径 (如: query.user)
         *
         */
        private String prefixPath;

        /**
         * 当前输入 (如: na)
         *
         */
        private String currentInput;

        /**
         * 所有部分
         *
         */
        private String[] parts;

        /**
         * 是否有点号结尾(表示需要补全属性)
         *
         * @return boolean
         * @since 1.0.0
         */
        public boolean hasDotSuffix() {
            return this.parts != null && this.parts.length > 1 && StringUtil.isEmpty(this.currentInput);
        }
    }
}
