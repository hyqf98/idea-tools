package io.github.easy.tools.service.mybatis.completion.strategy;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import io.github.easy.tools.service.mybatis.completion.CompletionContext;
import io.github.easy.tools.service.mybatis.completion.CompletionStrategy;
import io.github.easy.tools.service.mybatis.expression.MyBatisExpressionParser;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Parameter Completion Strategy
 *
 * @author haijun
 * @date 2025-12-18 14:29:21
 * @version 1.0.0
 * @since 1.0.0
 */
public class ParameterCompletionStrategy implements CompletionStrategy {

    /**
     * 基本数据类型集合
     */
    private static final Set<String> PRIMITIVE_TYPES = new HashSet<>(Arrays.asList(
            "int", "Integer", "long", "Long", "short", "Short", "byte", "Byte",
            "float", "Float", "double", "Double", "boolean", "Boolean",
            "char", "Character", "String", "BigDecimal", "BigInteger",
            "Date", "LocalDate", "LocalDateTime", "LocalTime"
    ));

    /**
     * 提供参数补全
     *
     * @param context 补全上下文
     * @param result  补全结果集
     * @since 1.0.0
     */
    @Override
    public void provideCompletions(@NotNull CompletionContext context, @NotNull CompletionResultSet result) {
        PsiMethod method = context.getMapperMethod();
        if (method == null) {
            return;
        }

        MyBatisExpressionParser.ExpressionParseResult parseResult =
                MyBatisExpressionParser.parseForCompletion(context.getCurrentText());

        String currentInput = parseResult.getCurrentInput();
        PsiParameter[] parameters = method.getParameterList()
                .getParameters();

        // 使用自定义前缀匹配器，匹配参数名
        // 使用原始文本作为前缀匹配器，确保正确匹配用户输入的字符
        // 例如：用户输入"q"，应该能匹配到"query"，而不会导致重复
        CompletionResultSet paramResult = result.withPrefixMatcher(context.getCurrentText());

        for (PsiParameter parameter : parameters) {
            String paramName = parameter.getName();

            // 获取 @Param 注解的值
            String annotationValue = MyBatisUtils.getParamAnnotationValue(parameter);
            if (StringUtil.isNotEmpty(annotationValue)) {
                paramName = annotationValue;
            }

            // 尝试推断实际类型（支持泛型参数推断）
            String typeName = parameter.getType()
                    .getPresentableText();
            // 优先使用基于 context 的推断方法，这样可以从 XML 的 parameterType 属性获取类型
            // 这对泛型方法参数尤其重要，因为可以通过 parameterType 明确指定实际类型
            PsiClass resolvedClass = MyBatisUtils.resolveRootParamClass(
                    context.getPosition(),
                    parseResult.getRootParam()
            );
            if (resolvedClass == null) {
                resolvedClass = MyBatisUtils.resolveRootParamClass(parameter, paramName);
            }
            if (resolvedClass != null) {
                String resolvedTypeName = resolvedClass.getName();
                if (resolvedTypeName != null && !resolvedTypeName.equals(typeName)) {
                    // 如果推断出了更具体的类型，使用推断后的类型名
                    typeName = resolvedTypeName;

                    // 如果有完整的限定名，尝试使用短类名显示
                    String qualifiedName = resolvedClass.getQualifiedName();
                    if (qualifiedName != null) {
                        typeName = qualifiedName;
                        // 显示短类名
                        int lastDot = typeName.lastIndexOf('.');
                        if (lastDot >= 0) {
                            typeName = typeName.substring(lastDot + 1);
                        }
                    }
                }
            }

            boolean isPrimitive = this.isPrimitiveType(typeName);

            LookupElementBuilder builder = LookupElementBuilder
                    .create(paramName)
                    .withIcon(AllIcons.Nodes.Parameter)
                    .withTypeText(typeName)
                    .withTailText(" " + typeName, true)
                    .withInsertHandler(new ParameterInsertHandler(isPrimitive, context.isInXmlAttribute()));

            // 为补全项添加高优先级，确保在其他插件之前显示
            LookupElement prioritized = PrioritizedLookupElement.withPriority(builder, 100.0);
            paramResult.addElement(prioritized);
        }
    }

    /**
     * 判断是否支持该上下文
     *
     * @param context 补全上下文
     * @return 如果支持则返回 true
     * @since 1.0.0
     */
    @Override
    public boolean supports(@NotNull CompletionContext context) {
        return context.getCompletionType() == CompletionContext.CompletionType.PARAMETER;
    }

    /**
     * 判断是否为基本数据类型
     *
     * @param typeName 类型名称
     * @return 如果是基本类型则返回 true
     * @since 1.0.0
     */
    private boolean isPrimitiveType(@NotNull String typeName) {
        String simpleType = typeName.replaceAll("<.*>", "")
                .trim();
        int lastDot = simpleType.lastIndexOf('.');
        if (lastDot >= 0) {
            simpleType = simpleType.substring(lastDot + 1);
        }
        return PRIMITIVE_TYPES.contains(simpleType);
    }

    /**
     * 参数插入处理器
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-18
     * @since 1.0.0
     */
    private static class ParameterInsertHandler implements InsertHandler<LookupElement> {

        /**
         * is primitive type
         */
        private final boolean isPrimitiveType;
        /**
         * is in xml attribute
         */
        private final boolean isInXmlAttribute;

        /**
         * Parameter Insert Handler
         *
         * @param isPrimitiveType is primitive type
         * @param isInXmlAttribute is in xml attribute
         * @since 1.0.0
         */
        public ParameterInsertHandler(boolean isPrimitiveType, boolean isInXmlAttribute) {
            this.isPrimitiveType = isPrimitiveType;
            this.isInXmlAttribute = isInXmlAttribute;
        }

        /**
         * Handle Insert
         *
         * @param insertionContext insertion context
         * @param lookupElement lookup element
         * @since 1.0.0
         */
        @Override
        public void handleInsert(@NotNull InsertionContext insertionContext, @NotNull LookupElement lookupElement) {
            Editor editor = insertionContext.getEditor();
            Document document = insertionContext.getDocument();
            int startOffset = insertionContext.getStartOffset();
            int tailOffset = insertionContext.getTailOffset();

            // 确保文档已提交，避免在 injected language 环境中出现断言错误
            PsiDocumentManager.getInstance(insertionContext.getProject())
                    .commitDocument(document);

            String insertText = lookupElement.getLookupString();
            int documentLength = document.getTextLength();
            int replaceStart = Math.max(0, startOffset);

            // 严格的边界检查，防止在 injected language 环境中出现断言错误
            if (replaceStart > documentLength || tailOffset > documentLength) {
                // 如果 offset 无效，直接插入参数名
                this.insertParameter(document, editor, Math.min(startOffset, documentLength), insertText);
                return;
            }

            try {
                // IntelliJ 已经给出合法替换区间，这里只替换当前补全范围
                if (replaceStart < tailOffset) {
                    document.deleteString(replaceStart, tailOffset);
                }
            } catch (Throwable e) {
                // 如果删除失败（比如在 injected language 环境中），直接在当前位置插入
                this.insertParameter(document, editor, Math.min(tailOffset, document.getTextLength()), insertText);
                return;
            }

            // 如果在 XML 属性中，或不是基本类型，则直接插入参数名
            if (this.isInXmlAttribute || !this.isPrimitiveType) {
                document.insertString(replaceStart, insertText);
                editor.getCaretModel()
                        .moveToOffset(replaceStart + insertText.length());
                return;
            }

            // 只有在 XML 内容中且是基本类型时，才需要补上 #{}
            this.insertParameterWithExpression(document, editor, replaceStart, insertText);
        }

        /**
         * 直接插入参数名
         *
         * @param document document
         * @param editor editor
         * @param offset offset
         * @param insertText insert text
         * @since 1.0.0
         */
        private void insertParameter(@NotNull Document document,
                                     @NotNull Editor editor,
                                     int offset,
                                     @NotNull String insertText) {
            document.insertString(offset, insertText);
            editor.getCaretModel()
                    .moveToOffset(offset + insertText.length());
        }

        /**
         * 插入带 #{} 的参数表达式
         *
         * @param document document
         * @param editor editor
         * @param offset offset
         * @param insertText insert text
         * @since 1.0.0
         */
        private void insertParameterWithExpression(@NotNull Document document,
                                                   @NotNull Editor editor,
                                                   int offset,
                                                   @NotNull String insertText) {
            StringBuilder textBuilder = new StringBuilder();

            // 检查前面是否已经有 #{
            String textBefore = "";
            if (offset > 2) {
                textBefore = document.getText()
                        .substring(Math.max(0, offset - 2), offset);
            }

            if (!"#{".equals(textBefore)) {
                textBuilder.append("#{");
            }
            textBuilder.append(insertText);

            // 检查后面是否已经有 }
            String textAfter = "";
            if (offset < document.getTextLength()) {
                textAfter = document.getText()
                        .substring(offset, Math.min(offset + 1, document.getTextLength()));
            }

            if (!"}".equals(textAfter)) {
                textBuilder.append("}");
            }

            // 插入新内容
            document.insertString(offset, textBuilder.toString());

            // 移动光标到 } 后面
            editor.getCaretModel()
                    .moveToOffset(offset + textBuilder.length());
        }
    }
}
