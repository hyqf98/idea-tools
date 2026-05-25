package io.github.ideatools.service.mybatis.completion;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Completion Context
 *
 * @author haijun
 * @date 2025-12-18 14:28:56
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
public class CompletionContext {

    /**
     * 项目对象
     *
     */
    @NotNull
    private Project project;

    /**
     * 编辑器
     *
     */
    @Nullable
    private Editor editor;

    /**
     * 当前PSI元素
     *
     */
    @NotNull
    private PsiElement position;

    /**
     * 对应的Mapper方法
     *
     */
    @Nullable
    private PsiMethod mapperMethod;

    /**
     * 当前输入的文本
     *
     */
    @NotNull
    private String currentText;

    /**
     * 是否在XML属性中
     *
     */
    private boolean inXmlAttribute;

    /**
     * 标签名(如果在标签内)
     *
     */
    @Nullable
    private String tagName;

    /**
     * 属性名(如果在属性中)
     *
     */
    @Nullable
    private String attributeName;

    /**
     * 补全类型
     *
     */
    @NotNull
    @Builder.Default
    private CompletionType completionType = CompletionType.PARAMETER;

    /**
     * 表达式开始位置(在XmlText中的#{}表达式),如果不在表达式内则为-1
     *
     */
    @Builder.Default
    private int expressionStartOffset = -1;

    /**
     * 表达式结束位置(在XmlText中的#{}表达式),如果不在表达式内则为-1
     *
     */
    @Builder.Default
    private int expressionEndOffset = -1;

    /**
     * 补全类型枚举
     *
     * @author haijun
     * @version 1.0.0
     * @date 2025-12-18
     * @since 1.0.0
     */
    public enum CompletionType {
        /**
         * 参数补全
         *
         */
        PARAMETER,

        /**
         * 字段补全
         *
         */
        FIELD,

        /**
         * 标签补全(如.if/.for等)
         *
         */
        TAG_TEMPLATE,

        /**
         * 关键字补全(直接输入if/for等关键字)
         *
         */
        KEYWORD_ONLY,

        /**
         * 普通文本
         *
         */
        PLAIN_TEXT
    }
}
