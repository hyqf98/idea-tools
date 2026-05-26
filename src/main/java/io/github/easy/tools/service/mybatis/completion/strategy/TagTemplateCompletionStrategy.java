package io.github.easy.tools.service.mybatis.completion.strategy;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import io.github.easy.tools.action.conversion.PropertyNameConverter;
import io.github.easy.tools.service.mybatis.completion.CompletionContext;
import io.github.easy.tools.service.mybatis.completion.CompletionStrategy;
import io.github.easy.tools.service.mybatis.expression.MyBatisExpressionParser;
import io.github.easy.tools.utils.MyBatisUtils;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Tag Template Completion Strategy
 *
 * @author haijun
 * @date 2025-12-18 14:30:59
 * @version 1.0.0
 * @since 1.0.0
 */
public class TagTemplateCompletionStrategy implements CompletionStrategy {

    /**
     * 支持的标签模板列表
     *
     */
    private final List<TagTemplate> templates;

    /**
     * 构造函数
     *
     * @since 1.0.0
     */
    public TagTemplateCompletionStrategy() {
        this.templates = new ArrayList<>();
        this.initializeTemplates();
    }

    /**
     * 初始化标签模板
     *
     * @since 1.0.0
     */
    private void initializeTemplates() {
        this.templates.add(TagTemplate.builder()
                                   .keyword("value")
                                   .description("生成参数取值表达式")
                                   .template("")
                                   .build());

        // if标签模板 - 使用类型感知模板标记
        this.templates.add(TagTemplate.builder()
                                   .keyword("if")
                                   .description("生成if标签(判断非空，根据字段类型自动适配)")
                                   .template("<if test=\"{expression} != null\">\n    {cursor}\n</if>")
                                   .typeAware(true)
                                   .build());

        // parentif标签模板 - 为父类字段生成if标签
        this.templates.add(TagTemplate.builder()
                                   .keyword("parentif")
                                   .description("生成父类所有字段的if标签(批量)")
                                   .template("")
                                   .typeAware(false)
                                   .isParentFields(true)
                                   .build());

        // when标签模板
        this.templates.add(TagTemplate.builder()
                                   .keyword("when")
                                   .description("生成when标签")
                                   .template("<when test=\"{expression} != null\">\n    {cursor}\n</when>")
                                   .build());

        // foreach标签模板
        this.templates.add(TagTemplate.builder()
                                   .keyword("for")
                                   .description("生成foreach标签(判断非空且集合非空)")
                                   .template(
                                           "<if test=\"{expression} != null and {expression}.size() > 0\">\n    <foreach " +
                                                   "collection=\"{expression}\" item=\"item\" separator=\",\">\n        {cursor}\n    " +
                                                   "</foreach>\n</if>")
                                   .build());

        this.templates.add(TagTemplate.builder()
                                   .keyword("foreach")
                                   .description("生成foreach标签(完整，判断非空且集合非空)")
                                   .template(
                                           "<if test=\"{expression} != null and {expression}.size() > 0\">\n    <foreach " +
                                                   "collection=\"{expression}\" item=\"item\" index=\"index\" separator=\",\" open=\"(\" " +
                                                   "close=\")\">\n        {cursor}\n    </foreach>\n</if>")
                                   .build());

        // set标签模板
        this.templates.add(TagTemplate.builder()
                                   .keyword("set")
                                   .description("生成set标签(用于update)")
                                   .template(
                                           "<set>\n    <if test=\"{expression} != null\">\n        {field} = #{{{expression}}},\n    " +
                                                   "</if>\n    {cursor}\n</set>")
                                   .build());

        // choose-when-otherwise标签模板
        this.templates.add(TagTemplate.builder()
                                   .keyword("choose")
                                   .description("生成choose-when-otherwise标签")
                                   .template(
                                           "<choose>\n    <when test=\"{expression} != null\">\n        {cursor}\n    </when>\n    " +
                                                   "<otherwise>\n        \n    </otherwise>\n</choose>")
                                   .build());
    }

    /**
     * 提供标签模板补全
     *
     * @param context 补全上下文
     * @param result  补全结果集
     * @since 1.0.0
     */
    @Override
    public void provideCompletions(@NotNull CompletionContext context, @NotNull CompletionResultSet result) {
        String currentText = context.getCurrentText();
        CompletionContext.CompletionType completionType = context.getCompletionType();

        // 处理直接输入关键字的情况(如: "if", "for")
        if (completionType == CompletionContext.CompletionType.KEYWORD_ONLY) {
            this.provideKeywordOnlyCompletions(context, result);
            return;
        }

        // 处理带参数名的标签模板(如: "query.if", "query.name.if")
        if (completionType == CompletionContext.CompletionType.TAG_TEMPLATE) {
            this.provideTagTemplateCompletions(context, result);
            return;
        }

        // 在FIELD类型时，如果当前输入以标签关键字前缀结尾，也提供标签补全
        if (completionType == CompletionContext.CompletionType.FIELD) {
            String[] parts = MyBatisExpressionParser.parseForCompletion(currentText)
                    .getParts();
            if (parts.length >= 2) {
                String lastPart = parts[parts.length - 1].toLowerCase();
                if (this.isTagKeywordPrefix(lastPart)) {
                    this.provideTagTemplateCompletions(context, result);
                }
            }
        }
    }

    /**
     * 判断是否支持该上下文
     *
     * @param context 补全上下文
     * @return true如果支持
     * @since 1.0.0
     */
    @Override
    public boolean supports(@NotNull CompletionContext context) {
        // 只在非XML属性中支持
        if (context.isInXmlAttribute()) {
            return false;
        }

        CompletionContext.CompletionType completionType = context.getCompletionType();

        // 直接支持TAG_TEMPLATE和KEYWORD_ONLY类型
        if (completionType == CompletionContext.CompletionType.TAG_TEMPLATE ||
                completionType == CompletionContext.CompletionType.KEYWORD_ONLY) {
            return true;
        }

        // 在FIELD类型时，如果当前输入以标签关键字前缀结尾，也支持标签补全
        if (completionType == CompletionContext.CompletionType.FIELD) {
            String currentText = context.getCurrentText();
            MyBatisExpressionParser.ExpressionParseResult parseResult =
                    MyBatisExpressionParser.parseForCompletion(currentText);
            String[] parts = parseResult.getParts();

            // 如果parts长度>=2，检查最后一部分是否是标签关键字的前缀
            if (parts.length >= 2) {
                String lastPart = parts[parts.length - 1].toLowerCase();
                return this.isTagKeywordPrefix(lastPart);
            }
        }

        return false;
    }

    /**
     * 判断是否为标签关键字的前缀
     *
     * @param input 输入文本
     * @return true如果是标签关键字的前缀
     * @since 1.0.0
     */
    private boolean isTagKeywordPrefix(@NotNull String input) {
        if (StringUtil.isEmpty(input)) {
            return true; // 空字符串匹配所有标签
        }

        // 检查是否是任何标签关键字的前缀
        for (TagTemplate template : this.templates) {
            if (template.getKeyword()
                    .startsWith(input)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 提供关键字补全(直接输入if/for等关键字)
     *
     * @param context 补全上下文
     * @param result  补全结果集
     * @since 1.0.0
     */
    private void provideKeywordOnlyCompletions(@NotNull CompletionContext context,
                                               @NotNull CompletionResultSet result) {
        String currentText = context.getCurrentText()
                .toLowerCase();

        // 使用自定义前缀匹配器
        CompletionResultSet tagResult = result.withPrefixMatcher(currentText);

        // 提供所有匹配的标签关键字
        for (TagTemplate template : this.templates) {
            LookupElementBuilder builder = LookupElementBuilder
                    .create(template.getKeyword())
                    .withIcon(AllIcons.Nodes.Tag)
                    .withTypeText("MyBatis标签")
                    .withTailText(" " + template.getDescription(), true)
                    .withInsertHandler(new KeywordOnlyInsertHandler(template));

            LookupElement prioritized = PrioritizedLookupElement.withPriority(builder, this.getTemplatePriority(template));
            tagResult.addElement(prioritized);
        }
    }

    /**
     * 提供标签模板补全(query.if, query.name.if)
     *
     * @param context 补全上下文
     * @param result  补全结果集
     * @since 1.0.0
     */
    private void provideTagTemplateCompletions(@NotNull CompletionContext context,
                                               @NotNull CompletionResultSet result) {
        String currentText = context.getCurrentText();

        // 解析表达式
        MyBatisExpressionParser.ExpressionParseResult parseResult =
                MyBatisExpressionParser.parseForCompletion(currentText);

        String[] parts = parseResult.getParts();
        if (parts.length < 2) {
            return;
        }

        // 最后一部分是关键字
        String keyword = parts[parts.length - 1].toLowerCase();

        // 前面部分是表达式
        StringBuilder expressionBuilder = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) {
                expressionBuilder.append(".");
            }
            expressionBuilder.append(parts[i]);
        }
        String expression = expressionBuilder.toString();

        // 验证表达式是否有效
        if (!this.isValidExpression(context, expression)) {
            return;
        }

        // 判断是单个字段还是对象级别
        boolean isObjectLevel = parts.length == 2; // 如: query.if
        boolean isFieldLevel = parts.length >= 3;   // 如: query.name.if

        // 使用自定义前缀匹配器
        CompletionResultSet tagResult = result.withPrefixMatcher(keyword);

        // 提供匹配的模板
        for (TagTemplate template : this.templates) {
            String fieldName = this.extractFieldName(parts);

            LookupElementBuilder builder = LookupElementBuilder
                    .create(template.getKeyword())
                    .withIcon(AllIcons.Nodes.Tag)
                    .withTypeText("MyBatis标签")
                    .withTailText(" " + template.getDescription(), true)
                    .withInsertHandler(new TagTemplateInsertHandler(
                            template, expression, fieldName, isObjectLevel, context));

            LookupElement prioritized = PrioritizedLookupElement.withPriority(builder, this.getTemplatePriority(template));
            tagResult.addElement(prioritized);
        }
    }

    private double getTemplatePriority(@NotNull TagTemplate template) {
        return switch (template.getKeyword()) {
            case "value" -> 120.0;
            case "if" -> 110.0;
            default -> 100.0;
        };
    }

    /**
     * 验证表达式是否有效
     *
     * @param context    上下文
     * @param expression 表达式
     * @return true如果有效
     * @since 1.0.0
     */
    private boolean isValidExpression(@NotNull CompletionContext context, @NotNull String expression) {
        PsiMethod method = context.getMapperMethod();
        if (method == null) {
            return false;
        }

        MyBatisExpressionParser.ExpressionParseResult parseResult =
                MyBatisExpressionParser.parseForCompletion(expression);

        String rootParam = parseResult.getRootParam();
        if (StringUtil.isEmpty(rootParam)) {
            return false;
        }

        // 验证根参数是否存在
        for (PsiParameter param : method.getParameterList()
                .getParameters()) {
            String paramName = param.getName();
            String annotationValue = MyBatisUtils.getParamAnnotationValue(param);
            if (StringUtil.isNotEmpty(annotationValue)) {
                paramName = annotationValue;
            }
            if (rootParam.equals(paramName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 提取字段名(用于set标签)
     *
     * @param parts 表达式部分
     * @return 字段名
     * @since 1.0.0
     */
    private String extractFieldName(@NotNull String[] parts) {
        if (parts.length >= 2) {
            // 倒数第二个部分通常是字段名
            return PropertyNameConverter.toLowerUnderline(parts[parts.length - 2]);
        }
        return "column_name";
    }

    /**
     * 标签模板
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-18
     * @since 1.0.0
     */
    @Data
    @Builder
    private static class TagTemplate {
        /**
         * 关键字
         *
         */
        private String keyword;

        /**
         * 描述
         *
         */
        private String description;

        /**
         * 模板内容
         *
         */
        private String template;

        /**
         * 是否为类型感知模板
         * 如果为true，会根据字段类型自动生成不同的判断条件
         */
        @Builder.Default
        private boolean typeAware = false;

        /**
         * 是否为父类字段模板
         * 如果为true，会为父类所有字段生成if标签
         */
        @Builder.Default
        private boolean isParentFields = false;
    }

    /**
     * 标签模板插入处理器
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-18
     * @since 1.0.0
     * @param template
    template
     * @param expression
    expression
     * @param fieldName
    field name
     * @param isObjectLevel
    is object level (query.if)
     * @param context
    completion context
     */
    private record TagTemplateInsertHandler(TagTemplate template, String expression, String fieldName, boolean isObjectLevel,
                                            CompletionContext context) implements InsertHandler<LookupElement> {

        /**
         * Tag Template Insert Handler
         *
         * @param template template
         * @param expression expression
         * @param fieldName field name
         * @param isObjectLevel is object level
         * @param context completion context
         * @since 1.0.0
         */
        private TagTemplateInsertHandler(@NotNull TagTemplate template,
                                         @NotNull String expression,
                                         @NotNull String fieldName,
                                         boolean isObjectLevel,
                                         @NotNull CompletionContext context) {
            this.template = template;
            this.expression = expression;
            this.fieldName = fieldName;
            this.isObjectLevel = isObjectLevel;
            this.context = context;
        }

        /**
         * Handle Insert
         *
         * @param context context
         * @param lookupElement lookup element
         * @since 1.0.0
         */
        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement lookupElement) {
            Editor editor = context.getEditor();
            Document document = editor.getDocument();

            int startOffset = context.getStartOffset();
            int tailOffset = context.getTailOffset();

            // 删除已输入的内容(包括表达式和关键字)
            WriteCommandAction.runWriteCommandAction(context.getProject(), () -> {
                // 向前查找,删除整个表达式
                int deleteStart = this.findExpressionStart(document, startOffset);

                // 检查是否被#{}或${}包裹,如果是则一并删除
                int finalDeleteStart = deleteStart;
                int finalDeleteEnd = tailOffset;

                // 向前检查是否有#{或${
                if (deleteStart >= 2) {
                    String textBefore = document.getText()
                            .substring(deleteStart - 2, deleteStart);
                    if ("#{".equals(textBefore) || "${".equals(textBefore)) {
                        finalDeleteStart = deleteStart - 2;
                    }
                }

                // 向后检查是否有}
                if (tailOffset < document.getTextLength()) {
                    char charAfter = document.getCharsSequence()
                            .charAt(tailOffset);
                    if (charAfter == '}') {
                        finalDeleteEnd = tailOffset + 1;
                    }
                }

                // 获取当前行的缩进
                String indent = this.getCurrentLineIndent(document, finalDeleteStart);

                document.deleteString(finalDeleteStart, finalDeleteEnd);

                // 生成模板内容
                String templateContent;

                // 处理parentif标签
                if (this.template.isParentFields()) {
                    templateContent = this.generateParentFieldIfTemplate();
                } else if (this.isObjectLevel && "if".equals(this.template.getKeyword())) {
                    // 对象级别的if模板，为所有字段生成if标签
                    templateContent = this.generateBatchFieldIfTemplate();
                } else if (this.template.isTypeAware()) {
                    // 类型感知模板，根据字段类型生成不同的判断条件
                    templateContent = this.generateTypeAwareTemplate();
                } else {
                    // 单个字段的模板
                    templateContent = this.generateTemplateContent();
                }

                // 应用缩进到模板的每一行
                templateContent = this.applyIndent(templateContent, indent);

                // 插入模板
                document.insertString(finalDeleteStart, templateContent);

                // 移动光标到{cursor}位置
                int cursorPos = templateContent.indexOf("{cursor}");
                if (cursorPos >= 0) {
                    int finalCursorPos = finalDeleteStart + cursorPos;
                    document.deleteString(finalCursorPos, finalCursorPos + "{cursor}".length());
                    editor.getCaretModel()
                            .moveToOffset(finalCursorPos);
                } else {
                    // 如果没有cursor标记，光标移动到末尾
                    editor.getCaretModel()
                            .moveToOffset(finalDeleteStart + templateContent.length());
                }
            });
        }

        /**
         * 查找表达式开始位置
         *
         * @param document    文档
         * @param startOffset 开始偏移量
         * @return 表达式开始位置
         * @since 1.0.0
         */
        private int findExpressionStart(@NotNull Document document, int startOffset) {
            int pos = startOffset - 1;
            while (pos > 0) {
                char c = document.getCharsSequence()
                        .charAt(pos);
                if (Character.isWhitespace(c) || c == '{' || c == '>') {
                    return pos + 1;
                }
                pos--;
            }
            return pos;
        }

        /**
         * 获取当前行的缩进
         *
         * @param document 文档
         * @param offset 偏移量
         * @return 缩进字符串(空格或tab)
         * @since 1.0.0
         */
        private String getCurrentLineIndent(@NotNull Document document, int offset) {
            CharSequence chars = document.getCharsSequence();

            // 向前查找到行首
            int lineStart = offset;
            while (lineStart > 0 && chars.charAt(lineStart - 1) != '\n') {
                lineStart--;
            }

            // 提取行首的空白字符
            StringBuilder indent = new StringBuilder();
            for (int i = lineStart; i < offset && i < chars.length(); i++) {
                char c = chars.charAt(i);
                if (c == ' ' || c == '\t') {
                    indent.append(c);
                } else {
                    break;
                }
            }

            return indent.toString();
        }

        /**
         * 应用缩进到模板的每一行(除了第一行)
         *
         * @param template 模板内容
         * @param indent 缩进字符串
         * @return 应用缩进后的模板
         * @since 1.0.0
         */
        private String applyIndent(@NotNull String template, @NotNull String indent) {
            if (indent.isEmpty()) {
                return template;
            }

            String[] lines = template.split("\n", -1);
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    result.append("\n");
                    // 为除了第一行外的所有行添加缩进
                    if (!lines[i].isEmpty()) {
                        result.append(indent);
                    }
                }
                result.append(lines[i]);
            }

            return result.toString();
        }

        /**
         * 生成模板内容
         *
         * @return 模板内容
         * @since 1.0.0
         */
        private String generateTemplateContent() {
            if ("value".equals(this.template.getKeyword())) {
                return "#{" + this.expression + "}";
            }
            return this.template.getTemplate()
                    .replace("{expression}", this.expression)
                    .replace("{field}", this.fieldName);
        }

        /**
         * 生成类型感知的模板内容
         * 根据字段类型生成不同的判断条件
         *
         * @return 模板内容
         * @since 1.0.0
         */
        private String generateTypeAwareTemplate() {
            // 解析表达式获取字段类型
            String fieldType = this.getFieldType();

            String keyword = this.template.getKeyword();
            if ("if".equals(keyword)) {
                return this.buildIfTag(this.expression, this.fieldName, fieldType, this.extractTerminalName(this.expression), true);
            } else if ("ifnull".equals(keyword)) {
                return "<if test=\"" + this.buildEmptyCondition(this.expression, fieldType) + "\">\n    {cursor}\n</if>";
            }

            return this.template.getTemplate()
                    .replace("{expression}", this.expression)
                    .replace("{field}", this.fieldName);
        }

        /**
         * 获取字段类型
         *
         * @return 字段类型的完全限定名
         * @since 1.0.0
         */
        private String getFieldType() {
            PsiMethod method = this.context.getMapperMethod();
            if (method == null) {
                return null;
            }

            // 解析表达式
            MyBatisExpressionParser.ExpressionParseResult parseResult =
                    MyBatisExpressionParser.parseForCompletion(this.expression);
            String[] parts = parseResult.getParts();

            if (parts.length < 2) {
                return null;
            }

            // 查找根参数
            String rootParam = parseResult.getRootParam();
            PsiParameter parameter = null;
            for (PsiParameter param : method.getParameterList()
                    .getParameters()) {
                String paramName = param.getName();
                String annotationValue = MyBatisUtils.getParamAnnotationValue(param);
                if (StringUtil.isNotEmpty(annotationValue)) {
                    paramName = annotationValue;
                }
                if (rootParam.equals(paramName)) {
                    parameter = param;
                    break;
                }
            }

            if (parameter == null) {
                return null;
            }

            // 获取参数类型
            PsiClass currentClass = MyBatisUtils.resolveRootParamClass(
                    this.context.getPosition(),
                    parameter,
                    rootParam
            );
            if (currentClass == null) {
                return null;
            }

            currentClass = MyBatisUtils.resolveActualClassFromType(currentClass);
            if (currentClass == null) {
                return null;
            }

            // 遍历路径找到目标字段
            // parts[0]是根参数，parts[parts.length-1]是要判断的字段
            for (int i = 1; i < parts.length; i++) {
                String fieldName = parts[i];
                PsiField field = this.findFieldInClass(currentClass, fieldName);

                if (field == null) {
                    return null;
                }

                if (i == parts.length - 1) {
                    // 最后一个字段，返回其类型
                    return field.getType()
                            .getCanonicalText();
                }

                // 继续解析嵌套类型
                currentClass = MyBatisUtils.getTypeOfResolvedElement(field);
                if (currentClass == null) {
                    return null;
                }
                currentClass = MyBatisUtils.resolveActualClassFromType(currentClass);
            }

            return null;
        }

        /**
         * 在类中查找字段(包括父类)
         *
         * @param psiClass 类
         * @param fieldName 字段名
         * @return 字段对象
         * @since 1.0.0
         */
        private PsiField findFieldInClass(@NotNull PsiClass psiClass, @NotNull String fieldName) {
            // 先查找当前类
            PsiField field = psiClass.findFieldByName(fieldName, false);
            if (field != null) {
                return field;
            }
            // 再查找父类
            return psiClass.findFieldByName(fieldName, true);
        }

        /**
         * 判断是否为String类型
         *
         * @param typeCanonicalText 类型的完全限定名
         * @return true如果是String类型
         * @since 1.0.0
         */
        private boolean isStringType(String typeCanonicalText) {
            if (StringUtil.isEmpty(typeCanonicalText)) {
                return false;
            }
            String type = typeCanonicalText.toLowerCase();
            return type.equals("java.lang.string") ||
                    type.equals("string");
        }

        /**
         * 判断是否为集合或Map类型
         *
         * @param typeCanonicalText 类型的完全限定名
         * @return true如果是集合或Map类型
         * @since 1.0.0
         */
        private boolean isCollectionOrMapType(String typeCanonicalText) {
            if (StringUtil.isEmpty(typeCanonicalText)) {
                return false;
            }
            String type = typeCanonicalText.toLowerCase();
            return type.startsWith("java.util.collection") ||
                    type.startsWith("java.util.list") ||
                    type.startsWith("java.util.set") ||
                    type.startsWith("java.util.map") ||
                    type.startsWith("java.util.iterable") ||
                    type.contains("collection<") ||
                    type.contains("list<") ||
                    type.contains("set<") ||
                    type.contains("map<") ||
                    type.contains("iterable<");
        }

        /**
         * 判断是否为数组类型
         *
         * @param typeCanonicalText 类型的完全限定名
         * @return true如果是数组类型
         * @since 1.0.0
         */
        private boolean isArrayType(String typeCanonicalText) {
            return StringUtil.isNotEmpty(typeCanonicalText) && typeCanonicalText.endsWith("[]");
        }

        /**
         * 构建非空条件
         *
         * @param expression 表达式
         * @param fieldType 字段类型
         * @return 条件表达式
         * @since 1.0.0
         */
        private String buildPresentCondition(@NotNull String expression, String fieldType) {
            if (this.isCollectionOrMapType(fieldType)) {
                return expression + " != null and " + expression + ".size() > 0";
            }
            if (this.isArrayType(fieldType)) {
                return expression + " != null and " + expression + ".length > 0";
            }
            if (this.isStringType(fieldType)) {
                return expression + " != null and " + expression + " != ''";
            }
            return expression + " != null";
        }

        /**
         * 构建空值条件
         *
         * @param expression 表达式
         * @param fieldType 字段类型
         * @return 条件表达式
         * @since 1.0.0
         */
        private String buildEmptyCondition(@NotNull String expression, String fieldType) {
            if (this.isCollectionOrMapType(fieldType)) {
                return expression + " == null or " + expression + ".size() == 0";
            }
            if (this.isArrayType(fieldType)) {
                return expression + " == null or " + expression + ".length == 0";
            }
            if (this.isStringType(fieldType)) {
                return expression + " == null or " + expression + " == ''";
            }
            return expression + " == null";
        }

        /**
         * 生成批量字段if模板(query.if -> 为所有字段生成if)
         *
         * @return 模板内容
         * @since 1.0.0
         */
        private String generateBatchFieldIfTemplate() {
            PsiClass psiClass = this.resolveExpressionRootClass();
            if (psiClass == null) {
                return this.generateTemplateContent();
            }

            return this.generateFieldIfTags(this.getOwnFields(psiClass));
        }

        /**
         * 生成父类字段if模板(parentif)
         *
         * @return 模板内容
         * @since 1.0.0
         */
        private String generateParentFieldIfTemplate() {
            PsiClass psiClass = this.resolveExpressionRootClass();
            if (psiClass == null) {
                return "{cursor}";
            }

            return this.generateFieldIfTags(this.getParentFields(psiClass));
        }

        @NotNull
        private PsiField[] getOwnFields(@NotNull PsiClass psiClass) {
            return psiClass.getFields();
        }

        @NotNull
        private PsiField[] getParentFields(@NotNull PsiClass psiClass) {
            PsiField[] allFields = psiClass.getAllFields();
            PsiField[] ownFields = this.getOwnFields(psiClass);

            Set<String> ownFieldNames = new HashSet<>();
            for (PsiField field : ownFields) {
                ownFieldNames.add(field.getName());
            }

            List<PsiField> parentFields = new ArrayList<>();
            for (PsiField field : allFields) {
                if (!ownFieldNames.contains(field.getName())) {
                    parentFields.add(field);
                }
            }

            return parentFields.toArray(new PsiField[0]);
        }

        private PsiClass resolveExpressionRootClass() {
            PsiMethod method = this.context.getMapperMethod();
            if (method == null) {
                return null;
            }

            MyBatisExpressionParser.ExpressionParseResult parseResult =
                    MyBatisExpressionParser.parseForCompletion(this.expression);
            String rootParam = parseResult.getRootParam();

            PsiParameter parameter = null;
            for (PsiParameter param : method.getParameterList().getParameters()) {
                String paramName = param.getName();
                String annotationValue = MyBatisUtils.getParamAnnotationValue(param);
                if (StringUtil.isNotEmpty(annotationValue)) {
                    paramName = annotationValue;
                }
                if (rootParam.equals(paramName)) {
                    parameter = param;
                    break;
                }
            }

            if (parameter == null) {
                return null;
            }

            PsiClass psiClass = MyBatisUtils.resolveRootParamClass(
                    this.context.getPosition(),
                    parameter,
                    rootParam
            );
            if (psiClass == null) {
                return null;
            }

            return MyBatisUtils.resolveActualClassFromType(psiClass);
        }

        /**
         * 生成字段if标签（支持类型检测）
         *
         * @param fields 字段数组
         * @return 模板内容
         * @since 1.0.0
         */
        private String generateFieldIfTags(PsiField[] fields) {
            if (fields.length == 0) {
                return "{cursor}";
            }

            PsiClass entityClass = this.resolveEntityClass();

            // 生成所有字段的if标签
            StringBuilder result = new StringBuilder();
            for (PsiField field : fields) {
                String fieldName = field.getName();

                // 跳过静态字段和特殊字段
                if (field.hasModifierProperty("static") ||
                        "serialVersionUID".equals(fieldName) ||
                        "class".equals(fieldName)) {
                    continue;
                }

                String fullPath = this.expression + "." + fieldName;
                String columnName = this.resolveBestColumnName(fieldName, entityClass);

                String fieldType = field.getType()
                        .getCanonicalText();
                result.append(this.buildIfTag(fullPath, columnName, fieldType, fieldName, false));
                result.append("\n");
            }

            // 添加cursor标记
            result.append("{cursor}");

            return result.toString();
        }

        private String buildIfTag(@NotNull String expression,
                                  @NotNull String columnName,
                                  String fieldType,
                                  @NotNull String fieldName,
                                  boolean appendCursor) {
            StringBuilder result = new StringBuilder();
            result.append("<if test=\"")
                    .append(this.buildPresentCondition(expression, fieldType))
                    .append("\">\n")
                    .append(this.buildIfBody(expression, columnName, fieldType, fieldName));
            if (appendCursor) {
                result.append("\n    {cursor}");
            }
            result.append("\n</if>");
            return result.toString();
        }

        private String buildIfBody(@NotNull String expression,
                                   @NotNull String columnName,
                                   String fieldType,
                                   @NotNull String fieldName) {
            if (this.isCollectionOrMapType(fieldType) || this.isArrayType(fieldType)) {
                String itemName = this.buildForeachItemName(fieldName);
                return "    and " + columnName + " in\n" +
                        "    <foreach item=\"" + itemName + "\" collection=\"" + expression +
                        "\" separator=\",\" open=\"(\" close=\")\">\n" +
                        "        #{" + itemName + "}\n" +
                        "    </foreach>";
            }
            if (this.isStringType(fieldType)) {
                return "    and " + columnName + " like concat('%', #{" + expression + "}, '%')";
            }
            return "    and " + columnName + " = #{" + expression + "}";
        }

        @NotNull
        private String buildForeachItemName(@NotNull String fieldName) {
            return fieldName + "Item";
        }

        @NotNull
        private String extractTerminalName(@NotNull String expression) {
            int lastDotIndex = expression.lastIndexOf('.');
            if (lastDotIndex < 0 || lastDotIndex == expression.length() - 1) {
                return expression;
            }
            return expression.substring(lastDotIndex + 1);
        }

        @NotNull
        private String resolveBestColumnName(@NotNull String fieldName, @Nullable PsiClass entityClass) {
            String defaultColumnName = PropertyNameConverter.toLowerUnderline(fieldName);
            if (entityClass == null) {
                return defaultColumnName;
            }

            double bestScore = 0.0D;
            String bestColumnName = defaultColumnName;
            for (PsiField entityField : entityClass.getAllFields()) {
                if (entityField.hasModifierProperty("static")) {
                    continue;
                }

                String entityFieldName = entityField.getName();
                String entityColumnName = this.resolveColumnName(entityField);
                double fieldScore = this.calculateColumnSimilarity(fieldName, entityFieldName);
                double columnScore = this.calculateColumnSimilarity(fieldName, entityColumnName);
                double score = Math.max(fieldScore, columnScore);
                if (score > bestScore) {
                    bestScore = score;
                    bestColumnName = entityColumnName;
                }
            }

            return bestScore >= 0.75D ? bestColumnName : defaultColumnName;
        }

        @NotNull
        private String resolveColumnName(@NotNull PsiField field) {
            String annotationColumnName = this.findColumnNameFromAnnotation(field);
            if (StringUtil.isNotEmpty(annotationColumnName)) {
                return annotationColumnName;
            }
            return PropertyNameConverter.toLowerUnderline(field.getName());
        }

        @Nullable
        private String findColumnNameFromAnnotation(@NotNull PsiField field) {
            for (PsiAnnotation annotation : field.getAnnotations()) {
                String qualifiedName = annotation.getQualifiedName();
                if (StringUtil.isEmpty(qualifiedName)) {
                    continue;
                }
                if (qualifiedName.endsWith(".TableField") || qualifiedName.endsWith(".TableId")) {
                    String value = this.getAnnotationStringAttribute(annotation, "value");
                    if (StringUtil.isNotEmpty(value)) {
                        return value;
                    }
                }
                if (qualifiedName.endsWith(".Column")) {
                    String name = this.getAnnotationStringAttribute(annotation, "name");
                    if (StringUtil.isNotEmpty(name)) {
                        return name;
                    }
                    String value = this.getAnnotationStringAttribute(annotation, "value");
                    if (StringUtil.isNotEmpty(value)) {
                        return value;
                    }
                }
            }
            return null;
        }

        @Nullable
        private String getAnnotationStringAttribute(@NotNull PsiAnnotation annotation, @NotNull String attributeName) {
            PsiAnnotationMemberValue attributeValue = annotation.findAttributeValue(attributeName);
            if (attributeValue instanceof PsiLiteralExpression literalExpression) {
                Object value = literalExpression.getValue();
                if (value instanceof String stringValue && StringUtil.isNotEmpty(stringValue)) {
                    return stringValue;
                }
            }
            return null;
        }

        private double calculateColumnSimilarity(@NotNull String source, @NotNull String candidate) {
            String normalizedSource = this.normalizeForColumnMatch(source);
            String normalizedCandidate = this.normalizeForColumnMatch(candidate);
            if (normalizedSource.isEmpty() || normalizedCandidate.isEmpty()) {
                return 0.0D;
            }
            if (normalizedSource.equals(normalizedCandidate)) {
                return 1.0D;
            }
            if (normalizedSource.contains(normalizedCandidate) || normalizedCandidate.contains(normalizedSource)) {
                return (double) Math.min(normalizedSource.length(), normalizedCandidate.length()) /
                        (double) Math.max(normalizedSource.length(), normalizedCandidate.length()) + 0.1D;
            }
            int lcsLength = this.longestCommonSubsequenceLength(normalizedSource, normalizedCandidate);
            return (2.0D * lcsLength) / (normalizedSource.length() + normalizedCandidate.length());
        }

        @NotNull
        private String normalizeForColumnMatch(@NotNull String name) {
            String normalized = name.toLowerCase()
                    .replace("_", "")
                    .replace("-", "");
            String[] suffixes = {"list", "set", "array", "collection", "items", "item", "values"};
            boolean changed = true;
            while (changed) {
                changed = false;
                for (String suffix : suffixes) {
                    if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                        normalized = normalized.substring(0, normalized.length() - suffix.length());
                        changed = true;
                    }
                }
            }
            return normalized;
        }

        private int longestCommonSubsequenceLength(@NotNull String left, @NotNull String right) {
            int[][] dp = new int[left.length() + 1][right.length() + 1];
            for (int i = 1; i <= left.length(); i++) {
                for (int j = 1; j <= right.length(); j++) {
                    if (left.charAt(i - 1) == right.charAt(j - 1)) {
                        dp[i][j] = dp[i - 1][j - 1] + 1;
                    } else {
                        dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                    }
                }
            }
            return dp[left.length()][right.length()];
        }

        @Nullable
        private PsiClass resolveEntityClass() {
            PsiMethod method = this.context.getMapperMethod();
            if (method == null) {
                return null;
            }

            PsiClass mapperClass = method.getContainingClass();
            if (mapperClass != null) {
                PsiClass entityClass = this.findEntityTypeFromMapper(mapperClass);
                if (entityClass != null) {
                    entityClass = MyBatisUtils.resolveActualClassFromType(entityClass);
                    if (entityClass != null) {
                        return entityClass;
                    }
                }
            }

            PsiClass rootClass = this.resolveExpressionRootClass();
            if (rootClass == null) {
                return null;
            }
            return this.inferEntityClassFromQueryClass(rootClass);
        }

        @Nullable
        private PsiClass findEntityTypeFromMapper(@NotNull PsiClass mapperClass) {
            for (PsiClassType superType : mapperClass.getSuperTypes()) {
                PsiClass superClass = superType.resolve();
                if (superClass == null) {
                    continue;
                }

                PsiType[] typeParameters = superType.getParameters();
                if (typeParameters.length > 0) {
                    PsiClass entityClass = PsiTypesUtil.getPsiClass(typeParameters[0]);
                    if (entityClass != null) {
                        return entityClass;
                    }
                }

                PsiClass nestedEntityClass = this.findEntityTypeFromMapper(superClass);
                if (nestedEntityClass != null) {
                    return nestedEntityClass;
                }
            }
            return null;
        }

        @Nullable
        private PsiClass inferEntityClassFromQueryClass(@NotNull PsiClass queryClass) {
            String className = queryClass.getName();
            String qualifiedName = queryClass.getQualifiedName();
            if (StringUtil.isEmpty(className) || StringUtil.isEmpty(qualifiedName)) {
                return null;
            }

            String entityName = className;
            String[] suffixes = {"Query", "DTO", "VO", "Request"};
            for (String suffix : suffixes) {
                if (entityName.endsWith(suffix) && entityName.length() > suffix.length()) {
                    entityName = entityName.substring(0, entityName.length() - suffix.length());
                    break;
                }
            }
            if (entityName.equals(className)) {
                return null;
            }

            String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
            String[] candidatePackages = {
                    packageName,
                    packageName.replace(".query", ".entity"),
                    packageName.replace(".query", ".model"),
                    packageName.replace(".dto", ".entity"),
                    packageName.replace(".dto", ".model"),
                    packageName.replace(".vo", ".entity"),
                    packageName.replace(".request", ".entity")
            };

            GlobalSearchScope scope = GlobalSearchScope.allScope(queryClass.getProject());
            for (String candidatePackage : candidatePackages) {
                String candidateFqn = candidatePackage + "." + entityName;
                PsiClass candidateClass = JavaPsiFacade.getInstance(queryClass.getProject())
                        .findClass(candidateFqn, scope);
                if (candidateClass != null) {
                    return candidateClass;
                }
            }
            return null;
        }
    }

    /**
     * 关键字插入处理器(直接输入if/for等关键字)
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-23
     * @since 1.0.0
     * @param template
    template
     */
    private record KeywordOnlyInsertHandler(TagTemplate template) implements InsertHandler<LookupElement> {

        /**
         * Keyword Only Insert Handler
         *
         * @param template template
         * @since 1.0.0
         */
        private KeywordOnlyInsertHandler(@NotNull TagTemplate template) {
            this.template = template;
        }

        /**
         * Handle Insert
         *
         * @param context context
         * @param lookupElement lookup element
         * @since 1.0.0
         */
        @Override
        public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement lookupElement) {
            Editor editor = context.getEditor();
            Document document = editor.getDocument();

            int startOffset = context.getStartOffset();
            int tailOffset = context.getTailOffset();

            WriteCommandAction.runWriteCommandAction(context.getProject(), () -> {
                // 删除IntelliJ插入的关键字
                document.deleteString(startOffset, tailOffset);

                // 也删除之前输入的关键字前缀
                int deleteStart = this.findKeywordStart(document, startOffset);
                if (deleteStart < startOffset) {
                    document.deleteString(deleteStart, startOffset);
                }

                // 获取当前行的缩进
                String indent = this.getCurrentLineIndent(document, deleteStart);

                // 生成基本模板(不包含具体表达式)
                String templateContent = this.generateBasicTemplate();

                // 应用缩进到模板的每一行
                templateContent = this.applyIndent(templateContent, indent);

                // 插入模板
                document.insertString(deleteStart, templateContent);

                // 移动光标到{cursor}位置
                int cursorPos = templateContent.indexOf("{cursor}");
                if (cursorPos >= 0) {
                    int finalCursorPos = deleteStart + cursorPos;
                    document.deleteString(finalCursorPos, finalCursorPos + "{cursor}".length());
                    editor.getCaretModel()
                            .moveToOffset(finalCursorPos);
                } else {
                    editor.getCaretModel()
                            .moveToOffset(deleteStart + templateContent.length());
                }
            });
        }

        /**
         * 查找关键字开始位置
         *
         * @param document document
         * @param offset offset
         * @return 开始位置
         * @since 1.0.0
         */
        private int findKeywordStart(@NotNull Document document, int offset) {
            int pos = offset - 1;
            while (pos > 0) {
                char c = document.getCharsSequence()
                        .charAt(pos);
                if (Character.isWhitespace(c) || c == '>' || c == '{') {
                    return pos + 1;
                }
                pos--;
            }
            return 0;
        }

        /**
         * 获取当前行的缩进
         *
         * @param document 文档
         * @param offset 偏移量
         * @return 缩进字符串(空格或tab)
         * @since 1.0.0
         */
        private String getCurrentLineIndent(@NotNull Document document, int offset) {
            CharSequence chars = document.getCharsSequence();

            // 向前查找到行首
            int lineStart = offset;
            while (lineStart > 0 && chars.charAt(lineStart - 1) != '\n') {
                lineStart--;
            }

            // 提取行首的空白字符
            StringBuilder indent = new StringBuilder();
            for (int i = lineStart; i < offset && i < chars.length(); i++) {
                char c = chars.charAt(i);
                if (c == ' ' || c == '\t') {
                    indent.append(c);
                } else {
                    break;
                }
            }

            return indent.toString();
        }

        /**
         * 应用缩进到模板的每一行(除了第一行)
         *
         * @param template 模板内容
         * @param indent 缩进字符串
         * @return 应用缩进后的模板
         * @since 1.0.0
         */
        private String applyIndent(@NotNull String template, @NotNull String indent) {
            if (indent.isEmpty()) {
                return template;
            }

            String[] lines = template.split("\n", -1);
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    result.append("\n");
                    // 为除了第一行外的所有行添加缩进
                    if (!lines[i].isEmpty()) {
                        result.append(indent);
                    }
                }
                result.append(lines[i]);
            }

            return result.toString();
        }

        /**
         * 生成基本模板(不包含具体表达式)
         *
         * @return 模板内容
         * @since 1.0.0
         */
        private String generateBasicTemplate() {
            String keyword = this.template.getKeyword();

            // 根据不同的关键字生成不同的模板
            return switch (keyword) {
                case "value" -> "#{}";
                case "if" -> "<if test=\"\">\n    {cursor}\n</if>";
                case "ifnull" -> "<if test=\" == null or  == ''\">\n    {cursor}\n</if>";
                case "when" -> "<when test=\"\">\n    {cursor}\n</when>";
                case "for" ->
                        "<if test=\" != null and .size() > 0\">\n    <foreach collection=\"\" item=\"item\" separator=\",\">\n        " +
                                "{cursor}\n    </foreach>\n</if>";
                case "foreach" ->
                        "<if test=\" != null and .size() > 0\">\n    <foreach collection=\"\" item=\"item\" index=\"index\" separator=\"," +
                                "\" open=\"(\" close=\")\">\n        {cursor}\n    </foreach>\n</if>";
                case "where" -> "<where>\n    {cursor}\n</where>";
                case "set" -> "<set>\n    {cursor}\n</set>";
                case "choose" ->
                        "<choose>\n    <when test=\"\">\n        {cursor}\n    </when>\n    <otherwise>\n        \n    " +
                                "</otherwise>\n</choose>";
                default -> this.template.getTemplate()
                        .replace("{expression}", "")
                        .replace("{field}", "");
            };
        }
    }
}
