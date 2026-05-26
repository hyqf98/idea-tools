package io.github.easy.tools.service.mybatis;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.ProcessingContext;
import io.github.easy.tools.service.mybatis.completion.CompletionContext;
import io.github.easy.tools.service.mybatis.completion.CompletionStrategyManager;
import io.github.easy.tools.service.mybatis.expression.MyBatisExpressionParser;
import io.github.easy.tools.ui.config.FeatureToggleService;
import io.github.easy.tools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * MyBatis XML代码补全贡献者(重构版)
 * <p>
 * 使用策略模式重构,提供以下功能:
 * <ul>
 * <li>参数补全: 提示方法的所有参数</li>
 * <li>字段补全: 支持链式访问(如query.user.name)</li>
 * <li>标签模板: 支持快捷生成标签(如query.name.if)</li>
 * <li>表达式解析: 使用专业的表达式解析器</li>
 * </ul>
 * </p>
 *
 * @author haijun
 * @version 2.0.0
 * @date 2025-12-18
 * @since 1.0.0
 */
public class MyBatisXmlCompletionContributor extends CompletionContributor {

    /**
     * LOG
     */
    private static final Logger LOG = Logger.getInstance(MyBatisXmlCompletionContributor.class);

    /**
     * MyBatis SQL标签集合
     */
    private static final Set<String> SQL_TAGS = new HashSet<>(Arrays.asList(
            "select", "insert", "update", "delete", "sql"
    ));

    /**
     * 构造函数,注册代码补全提供器
     * 统一注册一个补全提供器,在内部区分是属性还是文本内容
     *
     * @since 1.0.0
     */
    public MyBatisXmlCompletionContributor() {
        // 创建统一的补全提供器实例
        MyBatisParamCompletionProvider provider = new MyBatisParamCompletionProvider();

        // 注册统一的 BASIC 类型补全,匹配所有 XML 文件中的 PsiElement
        // 在 MyBatisParamCompletionProvider 内部再判断是属性还是文本内容
        this.extend(CompletionType.BASIC,
                PlatformPatterns.psiElement(),
                provider);
    }

    /**
     * 文本提取结果,包含提取的文本和表达式边界信息
     *
     * @author haijun
     * @date 2025-12-22 16:52:49
     * @version 1.0.0
     * @since 1.0.0
     */
    private static class TextExtractionResult {
        /**
         * current text
         */
        private final String currentText;
        /**
         * expression start offset
         */
        private final int expressionStartOffset;
        /**
         * expression end offset
         */
        private final int expressionEndOffset;

        /**
         * Text Extraction Result
         *
         * @param currentText current text
         * @param expressionStartOffset expression start offset
         * @param expressionEndOffset expression end offset
         * @since 1.0.0
         */
        public TextExtractionResult(String currentText, int expressionStartOffset, int expressionEndOffset) {
            this.currentText = currentText;
            this.expressionStartOffset = expressionStartOffset;
            this.expressionEndOffset = expressionEndOffset;
        }

        /**
         * Get Current Text
         *
         * @return string
         * @since 1.0.0
         */
        public String getCurrentText() {
            return this.currentText;
        }

        /**
         * Get Expression Start Offset
         *
         * @return int
         * @since 1.0.0
         */
        public int getExpressionStartOffset() {
            return this.expressionStartOffset;
        }

        /**
         * Get Expression End Offset
         *
         * @return int
         * @since 1.0.0
         */
        public int getExpressionEndOffset() {
            return this.expressionEndOffset;
        }

        /**
         * Is In Expression
         *
         * @return boolean
         * @since 1.0.0
         */
        public boolean isInExpression() {
            return this.expressionStartOffset >= 0 && this.expressionEndOffset >= 0;
        }
    }

    /**
     * MyBatis参数补全提供器(重构版)
     * 统一处理属性值和文本内容的补全,在内部区分类型
     *
     * @author haijun
     * @version 2.0.0
     * @date 2025-12-18
     * @since 1.0.0
     */
    private static class MyBatisParamCompletionProvider extends CompletionProvider<CompletionParameters> {

        /**
         * 添加补全项
         *
         * @param parameters 补全参数
         * @param context    处理上下文
         * @param result     补全结果集
         * @since 1.0.0
         */
        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters,
                                      @NotNull ProcessingContext context,
                                      @NotNull CompletionResultSet result) {
            PsiElement position = parameters.getPosition();
            Project project = position.getProject();

            // 检查MyBatis XML代码补全功能是否启用
            if (!FeatureToggleService.getInstance().isMybatisXmlCompletionEnabled()) {
                return;
            }

            // 使用InjectedLanguageManager获取顶层元素,解决其他插件包装的问题
            InjectedLanguageManager injectedManager = InjectedLanguageManager.getInstance(project);
            PsiElement topLevelElement = injectedManager.getInjectionHost(position);

            // 使用顶层元素进行文件和方法查找
            PsiElement checkElement = topLevelElement != null ? topLevelElement : position;

            // 检查是否在Mapper XML文件中
            if (!MyBatisUtils.isInMapperFile(checkElement)) {
                return;
            }

            // 使用原始position判断上下文类型,因为topLevelElement在injected环境中不是XmlText
            boolean isInAttribute = this.isInXmlAttributeValue(checkElement);
            boolean isInTextContent = this.isInXmlTextContent(checkElement);

            // 必须在属性值或文本内容中
            if (!isInAttribute && !isInTextContent) {
                return;
            }

            // 使用checkElement进行SQL标签检查
            if (!this.isInValidContext(checkElement, isInAttribute, isInTextContent)) {
                return;
            }

            // 查找对应的Mapper方法
            PsiMethod method = MyBatisUtils.findMethod(checkElement);
            if (method == null) {
                return;
            }

            // 解析当前输入的内容,传入parameters以获取正确的offset
            TextExtractionResult textResult = this.getCurrentText(parameters, isInTextContent);

            // 构建补全上下文
            CompletionContext completionContext = this.buildCompletionContext(
                    checkElement, method, textResult, isInAttribute);

            // 使用策略管理器提供补全
            CompletionStrategyManager.getInstance().provideCompletions(completionContext, result);
        }

        /**
         * 检查是否在有效的上下文中(简化版)
         *
         * @param parent           父元素
         * @param isInAttribute    是否在属性中
         * @param isInTextContent  是否在文本内容中
         * @return true如果在有效的上下文中
         * @since 1.0.0
         */
        private boolean isInValidContext(@Nullable PsiElement parent,
                                        boolean isInAttribute,
                                        boolean isInTextContent) {
            // 如果在属性中,检查是否在SQL相关标签内的属性
            if (isInAttribute) {
                return parent != null && this.isInSqlRelatedAttribute(parent);
            }

            // 如果在文本内容中,检查是否在SQL相关标签中
            if (isInTextContent) {
                return parent != null && this.isInSqlRelatedTag(parent);
            }

            return false;
        }

        /**
         * 检查是否在 XML 属性值中
         *
         * @param element PSI元素
         * @return true如果在属性值中
         * @since 1.0.0
         */
        private boolean isInXmlAttributeValue(@Nullable PsiElement element) {
            while (element != null) {
                if (element instanceof XmlAttributeValue) {
                    return true;
                }
                if (element instanceof XmlTag) {
                    return false;
                }
                element = element.getParent();
            }
            return false;
        }

        /**
         * 检查是否在 XML 文本内容中
         *
         * @param element PSI元素
         * @return true如果在文本内容中
         * @since 1.0.0
         */
        private boolean isInXmlTextContent(@Nullable PsiElement element) {
            while (element != null) {
                if (element instanceof XmlText) {
                    return true;
                }
                if (element instanceof XmlAttributeValue) {
                    return false;
                }
                element = element.getParent();
            }
            return false;
        }

        /**
         * 检查属性是否与SQL相关
         * 只要属性在SQL相关标签内(select/insert/update/delete等),就返回true
         *
         * @param element PSI元素
         * @return true如果是SQL相关属性
         * @since 1.0.0
         */
        private boolean isInSqlRelatedAttribute(@NotNull PsiElement element) {
            PsiElement current = element;
            XmlAttribute attribute = null;

            // 先向上查找到属性节点
            while (current != null) {
                if (current instanceof XmlAttribute) {
                    attribute = (XmlAttribute) current;
                    break;
                }
                if (current instanceof XmlTag) {
                    return false;
                }
                current = current.getParent();
            }

            if (attribute == null) {
                return false;
            }

            // 从属性的父标签开始向上查找SQL相关标签
            XmlTag tag = attribute.getParent();
            while (tag != null) {
                String tagName = tag.getName();

                // 检查是否是SQL相关标签
                if (SQL_TAGS.contains(tagName)) {
                    return true;
                }

                // 检查是否是其他MyBatis动态SQL标签
                if ("where".equals(tagName) || "set".equals(tagName) ||
                    "foreach".equals(tagName) || "trim".equals(tagName) ||
                    "if".equals(tagName) || "when".equals(tagName) ||
                    "otherwise".equals(tagName) || "choose".equals(tagName) ||
                    "bind".equals(tagName)) {
                    return true;
                }

                // 继续向上查找
                PsiElement parent = tag.getParent();
                if (parent instanceof XmlTag) {
                    tag = (XmlTag) parent;
                } else {
                    break;
                }
            }

            return false;
        }

        /**
         * 检查是否在SQL相关的标签中
         *
         * @param element PSI元素
         * @return true如果在SQL标签中
         * @since 1.0.0
         */
        private boolean isInSqlRelatedTag(@NotNull PsiElement element) {
            PsiElement current = element;
            while (current != null) {
                if (current instanceof XmlTag tag) {
                    String tagName = tag.getName();
                    // 检查是否是SQL相关标签
                    if (SQL_TAGS.contains(tagName)) {
                        return true;
                    }
                    // 检查是否是其他SQL标签(where, set, foreach, if等)
                    if ("where".equals(tagName) || "set".equals(tagName) ||
                        "foreach".equals(tagName) || "trim".equals(tagName) ||
                        "if".equals(tagName) || "when".equals(tagName) ||
                        "otherwise".equals(tagName) || "choose".equals(tagName)) {
                        return true;
                    }
                }
                current = current.getParent();
            }
            return false;
        }

        /**
         * 获取当前输入的文本
         *
         * @param parameters      补全参数
         * @param isInTextContent 是否在文本内容中
         * @return 文本提取结果
         * @since 1.0.0
         */
        @NotNull
        private TextExtractionResult getCurrentText(@NotNull CompletionParameters parameters,
                                                    boolean isInTextContent) {
            // 使用position所在的PSI文件获取文档，避免injected language场景下offset越界
            PsiFile psiFile = parameters.getOriginalFile();
            Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
            if (document == null) {
                return new TextExtractionResult("", -1, -1);
            }
            int offset = parameters.getOffset();
            // 边界保护：确保offset不超过文档长度
            if (offset < 0 || offset > document.getTextLength()) {
                return new TextExtractionResult("", -1, -1);
            }

            // 检查是否在XML文本内容中
            if (isInTextContent) {
                return this.getCurrentTextInXmlContent(document, offset);
            } else {
                // 在XML属性值中
                String text = this.getCurrentTextInXmlAttribute(document, offset);
                return new TextExtractionResult(text, -1, -1);
            }
        }

        /**
         * 获取XML属性中的当前文本
         *
         * @param document 文档
         * @param offset   光标位置
         * @return 当前文本
         * @since 1.0.0
         */
        @NotNull
        private String getCurrentTextInXmlAttribute(@NotNull Document document, int offset) {
            // 查找属性值的开始位置(引号后的位置)
            int attrStart = offset;
            while (attrStart > 0) {
                char c = document.getCharsSequence().charAt(attrStart - 1);
                if (c == '"' || c == '\'') {
                    break;
                }
                attrStart--;
            }

            // 提取从属性开始到光标位置的文本
            String textBeforeCursor = document.getText().substring(attrStart, offset);

            // 查找最后一个空格,只返回光标前的最后一个单词
            int lastSpace = textBeforeCursor.lastIndexOf(' ');
            if (lastSpace >= 0) {
                return textBeforeCursor.substring(lastSpace + 1);
            }

            return textBeforeCursor;
        }

        /**
         * 获取XML文本内容中的当前文本,支持识别#{}表达式
         *
         * @param document 文档
         * @param offset   光标位置
         * @return 文本提取结果
         * @since 1.0.0
         */
        @NotNull
        private TextExtractionResult getCurrentTextInXmlContent(@NotNull Document document, int offset) {
            CharSequence chars = document.getCharsSequence();
            int textLength = document.getTextLength();

            // 先检查是否在#{}表达式内部
            int exprStart = -1;
            int exprEnd = -1;

            // 向前查找#{
            for (int i = offset - 1; i >= 0; i--) {
                char c = chars.charAt(i);
                // 如果遇到},说明不在表达式内
                if (c == '}') {
                    break;
                }
                // 找到#{,记录位置
                if (i > 0 && c == '{' && chars.charAt(i - 1) == '#') {
                    exprStart = i - 1;
                    break;
                }
                // 如果遇到<或>,说明不在表达式内
                if (c == '<' || c == '>') {
                    break;
                }
            }

            // 如果找到了#{,继续向后查找}
            if (exprStart >= 0) {
                for (int i = offset; i < textLength; i++) {
                    char c = chars.charAt(i);
                    if (c == '}') {
                        exprEnd = i;
                        break;
                    }
                    // 如果遇到<或>,说明表达式不完整
                    if (c == '<' || c == '>') {
                        break;
                    }
                }
            }

            // 如果在#{}表达式内,提取表达式内的文本
            // 条件:找到了#{,并且(没找到}或者}在光标之后)
            if (exprStart >= 0) {
                // 提取#{和光标之间的文本(不包括#{)
                String text = document.getText().substring(exprStart + 2, offset);
                // 如果找到了}且在光标之后,或者没找到},都认为在表达式内
                int effectiveExprEnd = exprEnd >= 0 ? exprEnd : offset;
                return new TextExtractionResult(text, exprStart, effectiveExprEnd);
            }

            // 不在#{}表达式内,使用原有逻辑提取普通文本
            int startPos = offset;
            while (startPos > 0) {
                char c = chars.charAt(startPos - 1);
                // 遇到空白字符、<、>、{、}等分隔符就停止
                if (Character.isWhitespace(c) || c == '<' || c == '>' || c == '{' || c == '}') {
                    break;
                }
                startPos--;
            }

            String text = document.getText().substring(startPos, offset);
            return new TextExtractionResult(text, -1, -1);
        }

        /**
         * 构建补全上下文
         *
         * @param position         当前位置
         * @param method           Mapper方法
         * @param textResult       文本提取结果
         * @param isInXmlAttribute 是否在XML属性中
         * @return 补全上下文
         * @since 1.0.0
         */
        @NotNull
        private CompletionContext buildCompletionContext(@NotNull PsiElement position,
                                                         @NotNull PsiMethod method,
                                                         @NotNull TextExtractionResult textResult,
                                                         boolean isInXmlAttribute) {
            Project project = position.getProject();
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();

            String currentText = textResult.getCurrentText();

            // 解析表达式
            MyBatisExpressionParser.ExpressionParseResult parseResult =
                    MyBatisExpressionParser.parseForCompletion(currentText);

            // 决定补全类型
            CompletionContext.CompletionType completionType = this.determineCompletionType(
                    currentText, parseResult, isInXmlAttribute);

            // 提取标签信息
            String tagName = null;
            String attributeName = null;
            if (isInXmlAttribute) {
                PsiElement parent = position.getParent();
                while (parent != null) {
                    if (parent instanceof XmlAttribute attr) {
                        attributeName = attr.getName();
                        XmlTag tag = attr.getParent();
                        if (tag != null) {
                            tagName = tag.getName();
                        }
                        break;
                    }
                    parent = parent.getParent();
                }
            }

            return CompletionContext.builder()
                    .project(project)
                    .editor(editor)
                    .position(position)
                    .mapperMethod(method)
                    .currentText(currentText)
                    .inXmlAttribute(isInXmlAttribute)
                    .tagName(tagName)
                    .attributeName(attributeName)
                    .completionType(completionType)
                    .expressionStartOffset(textResult.getExpressionStartOffset())
                    .expressionEndOffset(textResult.getExpressionEndOffset())
                    .build();
        }

        /**
         * 决定补全类型
         *
         * @param currentText      当前文本
         * @param parseResult      解析结果
         * @param isInXmlAttribute 是否在XML属性中
         * @return 补全类型
         * @since 1.0.0
         */
        @NotNull
        private CompletionContext.CompletionType determineCompletionType(
                @NotNull String currentText,
                @NotNull MyBatisExpressionParser.ExpressionParseResult parseResult,
                boolean isInXmlAttribute) {

            // 如果在XML属性中，不支持标签模板和关键字补全
            if (isInXmlAttribute) {
                // 在属性中，如果包含点号就是字段补全，否则是参数补全
                return currentText.contains(".") ? 
                    CompletionContext.CompletionType.FIELD : 
                    CompletionContext.CompletionType.PARAMETER;
            }

            String[] parts = parseResult.getParts();
            
            // 处理直接输入关键字的情况(如: "if", "for")
            if (parts.length == 1 && !currentText.contains(".")) {
                String input = currentText.toLowerCase();
                if (this.isTagKeyword(input) || this.isTagKeywordPrefix(input)) {
                    return CompletionContext.CompletionType.KEYWORD_ONLY;
                }
            }

            // 优先判断标签模板(如: query.if, query.name.if)
            // 但是当lastPart为空时(如query.)，应该返回FIELD类型，让字段和标签同时补全
            if (parts.length >= 2) {
                // 检查最后一部分是否是标签关键字
                String lastPart = parts[parts.length - 1].toLowerCase();
                // 只有当lastPart非空且是标签关键字或前缀时，才返回TAG_TEMPLATE
                if (StringUtil.isNotEmpty(lastPart) && 
                    (this.isTagKeyword(lastPart) || this.isTagKeywordPrefix(lastPart))) {
                    return CompletionContext.CompletionType.TAG_TEMPLATE;
                }
            }

            // 如果包含点号且不是标签关键字，是字段补全
            if (currentText.contains(".")) {
                return CompletionContext.CompletionType.FIELD;
            }

            // 默认是参数补全
            return CompletionContext.CompletionType.PARAMETER;
        }

        /**
         * 判断是否为标签关键字
         *
         * @param keyword 关键字
         * @return true如果是标签关键字
         * @since 1.0.0
         */
        private boolean isTagKeyword(@NotNull String keyword) {
            return keyword.equals("if") || keyword.equals("ifnull") ||
                   keyword.equals("for") || keyword.equals("foreach") ||
                   keyword.equals("where") || keyword.equals("set") ||
                   keyword.equals("when") || keyword.equals("choose");
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
                return true;
            }
            // 检查是否是任何标签关键字的前缀
            return "if".startsWith(input) || "ifnull".startsWith(input) ||
                   "for".startsWith(input) || "foreach".startsWith(input) ||
                   "where".startsWith(input) || "set".startsWith(input) ||
                   "when".startsWith(input) || "choose".startsWith(input);
        }
    }
}
