package io.github.ideatools.ui.mybatis;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import io.github.ideatools.utils.MyBatisUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * My Batis Xml Annotator
 *
 * @author haijun
 * @date 2025-12-12 14:12:36
 * @version 1.0.0
 * @since 1.0.0
 */
public class MyBatisXmlAnnotator implements Annotator {

    /**
     * MyBatis核心SQL标签集合
     */
    private static final Set<String> MYBATIS_SQL_TAGS = Set.of(
            "select", "insert", "update", "delete",
            "sql", "include", "selectKey"
    );

    /**
     * MyBatis结果映射相关标签
     */
    private static final Set<String> MYBATIS_RESULT_TAGS = Set.of(
            "resultMap", "result", "id", "collection", "association", "discriminator"
    );

    /**
     * MyBatis动态SQL标签
     */
    private static final Set<String> MYBATIS_DYNAMIC_TAGS = Set.of(
            "if", "choose", "when", "otherwise",
            "trim", "where", "set", "foreach", "bind"
    );

    /**
     * MyBatis参数映射标签
     */
    private static final Set<String> MYBATIS_PARAM_TAGS = Set.of(
            "parameterMap", "parameter"
    );

    /**
     * 高亮颜色 - SQL语句标签（蓝色）
     */
    private static final TextAttributesKey MYBATIS_SQL_TAG =
            TextAttributesKey.createTextAttributesKey(
                    "MYBATIS_SQL_TAG",
                    DefaultLanguageHighlighterColors.KEYWORD
            );

    /**
     * 高亮颜色 - 结果映射标签（紫色）
     */
    private static final TextAttributesKey MYBATIS_RESULT_TAG =
            TextAttributesKey.createTextAttributesKey(
                    "MYBATIS_RESULT_TAG",
                    DefaultLanguageHighlighterColors.INSTANCE_FIELD
            );

    /**
     * 高亮颜色 - 动态SQL标签（绿色）
     */
    private static final TextAttributesKey MYBATIS_DYNAMIC_TAG =
            TextAttributesKey.createTextAttributesKey(
                    "MYBATIS_DYNAMIC_TAG",
                    DefaultLanguageHighlighterColors.METADATA
            );

    /**
     * 注解PSI元素，提供语法高亮
     *
     * @param element PSI元素
     * @param holder  注解持有者
     * @since 1.0.0
     */
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // 只处理XML标签名称
        if (!(element instanceof XmlToken xmlToken)) {
            return;
        }

        // 检查是否为标签名Token
        if (xmlToken.getTokenType() != XmlTokenType.XML_NAME) {
            return;
        }

        // 获取父元素（应该是XmlTag）
        PsiElement parent = element.getParent();
        if (!(parent instanceof XmlTag xmlTag)) {
            return;
        }

        // 检查是否在MyBatis的mapper文件中
        if (!MyBatisUtils.isInMapperFile(xmlTag)) {
            return;
        }

        String tagName = xmlTag.getName();

        // 根据标签类型应用不同的高亮颜色
        if (MYBATIS_SQL_TAGS.contains(tagName)) {
            this.highlightElement(holder, element, MYBATIS_SQL_TAG);
        } else if (MYBATIS_RESULT_TAGS.contains(tagName)) {
            this.highlightElement(holder, element, MYBATIS_RESULT_TAG);
        } else if (MYBATIS_DYNAMIC_TAGS.contains(tagName)) {
            this.highlightElement(holder, element, MYBATIS_DYNAMIC_TAG);
        } else if (MYBATIS_PARAM_TAGS.contains(tagName)) {
            this.highlightElement(holder, element, MYBATIS_RESULT_TAG);
        }
    }

    /**
     * 应用高亮到元素
     *
     * @param holder          注解持有者
     * @param element         要高亮的元素
     * @param textAttributesKey 文本属性键
     * @since 1.0.0
     */
    private void highlightElement(@NotNull AnnotationHolder holder,
                                   @NotNull PsiElement element,
                                   @NotNull TextAttributesKey textAttributesKey) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .textAttributes(textAttributesKey)
                .create();
    }
}
