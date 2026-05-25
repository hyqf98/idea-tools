package io.github.ideatools.service.mybatis.completion.strategy;

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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import io.github.ideatools.action.conversion.PropertyNameConverter;
import io.github.ideatools.service.mybatis.completion.CompletionContext;
import io.github.ideatools.service.mybatis.completion.CompletionStrategy;
import io.github.ideatools.service.mybatis.expression.MyBatisExpressionParser;
import io.github.ideatools.utils.MyBatisUtils;
import lombok.Builder;
import lombok.Data;
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
        // if标签模板 - 使用类型感知模板标记
        this.templates.add(TagTemplate.builder()
                .keyword("if")
                .description("生成if标签(判断非空，根据字段类型自动适配)")
                .template("<if test=\"{expression} != null\">\n    {cursor}\n</if>")
                .typeAware(true)
                .build());

        this.templates.add(TagTemplate.builder()
                .keyword("ifnull")
                .description("生成if标签(判断为空)")
                .template("<if test=\"{expression} == null\">\n    {cursor}\n</if>")
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
                .description("生成foreach标签")
                .template("<foreach collection=\"{expression}\" item=\"item\" separator=\",\">\n    {cursor}\n</foreach>")
                .build());

        this.templates.add(TagTemplate.builder()
                .keyword("foreach")
                .description("生成foreach标签(完整)")
                .template("<foreach collection=\"{expression}\" item=\"item\" index=\"index\" separator=\",\" open=\"(\" close=\")\">\n    {cursor}\n</foreach>")
                .build());

        // where标签模板
        this.templates.add(TagTemplate.builder()
                .keyword("where")
                .description("生成where标签")
                .template("<where>\n    {cursor}\n</where>")
                .build());

        // set标签模板
        this.templates.add(TagTemplate.builder()
                .keyword("set")
                .description("生成set标签(用于update)")
                .template("<set>\n    <if test=\"{expression} != null\">\n        {field} = #{{{expression}}},\n    </if>\n    {cursor}\n</set>")
                .build());

        // choose-when-otherwise标签模板
        this.templates.add(TagTemplate.builder()
                .keyword("choose")
                .description("生成choose-when-otherwise标签")
                .template("<choose>\n    <when test=\"{expression} != null\">\n        {cursor}\n    </when>\n    <otherwise>\n        \n    </otherwise>\n</choose>")
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
            String[] parts = MyBatisExpressionParser.parseForCompletion(currentText).getParts();
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
            if (template.getKeyword().startsWith(input)) {
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
        String currentText = context.getCurrentText().toLowerCase();
        
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

            LookupElement prioritized = PrioritizedLookupElement.withPriority(builder, 100.0);
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

            LookupElement prioritized = PrioritizedLookupElement.withPriority(builder, 100.0);
            tagResult.addElement(prioritized);
        }
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
        for (PsiParameter param : method.getParameterList().getParameters()) {
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
     */
    private static class TagTemplateInsertHandler implements InsertHandler<LookupElement> {

        /**
         * template
         *
         */
        private final TagTemplate template;
        /**
         * expression
         *
         */
        private final String expression;
        /**
         * field name
         *
         */
        private final String fieldName;
        /**
         * is object level (query.if)
         */
        private final boolean isObjectLevel;
        /**
         * completion context
         */
        private final CompletionContext context;

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
        public TagTemplateInsertHandler(@NotNull TagTemplate template,
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
                    String textBefore = document.getText().substring(deleteStart - 2, deleteStart);
                    if ("#{".equals(textBefore) || "${".equals(textBefore)) {
                        finalDeleteStart = deleteStart - 2;
                    }
                }

                // 向后检查是否有}
                if (tailOffset < document.getTextLength()) {
                    char charAfter = document.getCharsSequence().charAt(tailOffset);
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
                    editor.getCaretModel().moveToOffset(finalCursorPos);
                } else {
                    // 如果没有cursor标记，光标移动到末尾
                    editor.getCaretModel().moveToOffset(finalDeleteStart + templateContent.length());
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
                char c = document.getCharsSequence().charAt(pos);
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

            // 根据字段类型生成判断条件
            String condition;
            if (this.isStringType(fieldType)) {
                // String类型: != null and != ''
                condition = this.expression + " != null and " + this.expression + " != ''";
            } else {
                // 非String类型: 只判断 != null
                condition = this.expression + " != null";
            }

            String keyword = this.template.getKeyword();
            if ("if".equals(keyword)) {
                return "<if test=\"" + condition + "\">\n    {cursor}\n</if>";
            } else if ("ifnull".equals(keyword)) {
                // 反转条件
                String nullCondition;
                if (this.isStringType(fieldType)) {
                    nullCondition = this.expression + " == null or " + this.expression + " == ''";
                } else {
                    nullCondition = this.expression + " == null";
                }
                return "<if test=\"" + nullCondition + "\">\n    {cursor}\n</if>";
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

            // 获取参数类型
            PsiClass currentClass = MyBatisUtils.resolveRootParamClass(parameter, rootParam);
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
                    return field.getType().getCanonicalText();
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
                // 默认当作String类型处理
                return true;
            }
            String type = typeCanonicalText.toLowerCase();
            return type.equals("java.lang.string") ||
                   type.equals("string") ||
                   type.contains("string");
        }

        /**
         * 生成批量字段if模板(query.if -> 为所有字段生成if)
         *
         * @return 模板内容
         * @since 1.0.0
         */
        private String generateBatchFieldIfTemplate() {
            PsiMethod method = this.context.getMapperMethod();
            if (method == null) {
                return this.generateTemplateContent();
            }

            // 解析表达式获取根参数
            MyBatisExpressionParser.ExpressionParseResult parseResult =
                    MyBatisExpressionParser.parseForCompletion(this.expression);
            String rootParam = parseResult.getRootParam();

            // 查找根参数
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
                return this.generateTemplateContent();
            }

            // 获取参数类型
            PsiClass psiClass = MyBatisUtils.resolveRootParamClass(parameter, rootParam);
            if (psiClass == null) {
                return this.generateTemplateContent();
            }

            psiClass = MyBatisUtils.resolveActualClassFromType(psiClass);
            if (psiClass == null) {
                return this.generateTemplateContent();
            }

            // 只获取当前类的字段（不包括父类）
            PsiField[] ownFields = psiClass.getFields();

            return this.generateFieldIfTags(ownFields, false);
        }

        /**
         * 生成父类字段if模板(parentif)
         *
         * @return 模板内容
         * @since 1.0.0
         */
        private String generateParentFieldIfTemplate() {
            PsiMethod method = this.context.getMapperMethod();
            if (method == null) {
                return "{cursor}";
            }

            // 解析表达式获取根参数
            MyBatisExpressionParser.ExpressionParseResult parseResult =
                    MyBatisExpressionParser.parseForCompletion(this.expression);
            String rootParam = parseResult.getRootParam();

            // 查找根参数
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
                return "{cursor}";
            }

            // 获取参数类型
            PsiClass psiClass = MyBatisUtils.resolveRootParamClass(parameter, rootParam);
            if (psiClass == null) {
                return "{cursor}";
            }

            psiClass = MyBatisUtils.resolveActualClassFromType(psiClass);
            if (psiClass == null) {
                return "{cursor}";
            }

            // 获取所有字段（包括父类）
            PsiField[] allFields = psiClass.getAllFields();
            // 获取当前类的字段
            PsiField[] ownFields = psiClass.getFields();

            // 过滤出父类字段
            java.util.Set<String> ownFieldNames = new java.util.HashSet<>();
            for (PsiField f : ownFields) {
                ownFieldNames.add(f.getName());
            }

            java.util.List<PsiField> parentFields = new java.util.ArrayList<>();
            for (PsiField f : allFields) {
                if (!ownFieldNames.contains(f.getName())) {
                    parentFields.add(f);
                }
            }

            return this.generateFieldIfTags(parentFields.toArray(new PsiField[0]), true);
        }

        /**
         * 生成字段if标签（支持类型检测）
         *
         * @param fields 字段数组
         * @param includeParentFields 是否包含父类字段
         * @return 模板内容
         * @since 1.0.0
         */
        private String generateFieldIfTags(PsiField[] fields, boolean includeParentFields) {
            if (fields.length == 0) {
                return "{cursor}";
            }

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
                String columnName = PropertyNameConverter.toLowerUnderline(fieldName);

                // 根据字段类型生成判断条件
                String fieldType = field.getType().getCanonicalText();
                String condition;
                if (this.isStringType(fieldType)) {
                    // String类型: != null and != ''
                    condition = fullPath + " != null and " + fullPath + " != ''";
                } else {
                    // 非String类型: 只判断 != null
                    condition = fullPath + " != null";
                }

                result.append("<if test=\"").append(condition).append("\">\n")
                      .append("    ").append(columnName).append(" = #{")
                      .append(fullPath).append("},\n")
                      .append("</if>\n");
            }

            // 添加cursor标记
            result.append("{cursor}");

            return result.toString();
        }
    }

    /**
     * 关键字插入处理器(直接输入if/for等关键字)
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-23
     * @since 1.0.0
     */
    private static class KeywordOnlyInsertHandler implements InsertHandler<LookupElement> {

        /**
         * template
         */
        private final TagTemplate template;

        /**
         * Keyword Only Insert Handler
         *
         * @param template template
         * @since 1.0.0
         */
        public KeywordOnlyInsertHandler(@NotNull TagTemplate template) {
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
                    editor.getCaretModel().moveToOffset(finalCursorPos);
                } else {
                    editor.getCaretModel().moveToOffset(deleteStart + templateContent.length());
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
                char c = document.getCharsSequence().charAt(pos);
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
                case "if" -> "<if test=\"\">\n    {cursor}\n</if>";
                case "ifnull" -> "<if test=\" == null or  == ''\">\n    {cursor}\n</if>";
                case "when" -> "<when test=\"\">\n    {cursor}\n</when>";
                case "for", "foreach" -> "<foreach collection=\"\" item=\"item\" separator=\",\">\n    {cursor}\n</foreach>";
                case "where" -> "<where>\n    {cursor}\n</where>";
                case "set" -> "<set>\n    {cursor}\n</set>";
                case "choose" -> "<choose>\n    <when test=\"\">\n        {cursor}\n    </when>\n    <otherwise>\n        \n    </otherwise>\n</choose>";
                default -> this.template.getTemplate().replace("{expression}", "").replace("{field}", "");
            };
        }
    }
}
