package io.github.easy.tools.service.doc;

import cn.hutool.core.util.StrUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Java注释比较器实现 <p> 用于比较Java元素是否已存在注释以及注释内容是否相同 </p>
 *
 * @author haijun
 * @date 2025-11-28 18:34:40
 * @version 1.0.0
 * @since 1.0.0
 */
public class JavaDocCommentComparator implements DocCommentComparator {

    /**
     * 检查Java元素是否已存在注释
     *
     * @param element element
     * @return boolean
     * @since 1.0.0
     */
    @Override
    public boolean hasComment(@NotNull PsiElement element) {
        if (element instanceof PsiJavaDocumentedElement documentedElement) {
            return documentedElement.getDocComment() != null;
        }
        return false;
    }

    /**
     * 合并现有注释和新注释 <p> 保持原有注释格式，只对参数、返回值、异常等标签进行增加、删除操作（不覆盖、不修改已有描述） </p>
     *
     * @param element element
     * @param newComment new comment
     * @return psi element
     * @since 1.0.0
     */
    @Override
    public PsiElement mergeComments(@NotNull PsiElement element, @NotNull PsiElement newComment) {
        if (!(element instanceof PsiJavaDocumentedElement documentedElement)
                || !(newComment instanceof PsiDocComment newDocComment)) {
            return newComment;
        }

        PsiDocComment oldDocComment = documentedElement.getDocComment();
        if (oldDocComment == null) {
            return newComment;
        }

        // 使用新注释作为基础，但保留用户已有的描述内容
        String mergedComment = this.preserveUserDescription(oldDocComment, newDocComment);
        // 进一步按照“只新增/删除，不覆盖修改”的策略合并标签
        mergedComment = this.mergeTags(element, mergedComment, oldDocComment);
        // 规范化泛型参数的@param写法（确保有空格）
        mergedComment = mergedComment.replace("* @param<", "* @param <");
        try {
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
            return factory.createDocCommentFromText(mergedComment);
        } catch (Exception e) {
            // 如果创建失败，返回新的注释
            return newComment;
        }
    }

    /**
     * 保留用户的描述内容
     *
     * @param oldDocComment old doc comment
     * @param newDocComment new doc comment
     * @return string
     * @since 1.0.0
     */
    private String preserveUserDescription(PsiDocComment oldDocComment, PsiDocComment newDocComment) {
        // 提取用户已有的描述
        String userDescription = this.extractUserDescription(oldDocComment);

        // 如果用户没有手动描述，则直接使用新注释
        if (StrUtil.isBlank(userDescription)) {
            return newDocComment.getText();
        }

        // 获取新注释的文本
        String newCommentText = newDocComment.getText();

        // 替换新注释中的描述部分为用户的手动描述
        return this.replaceDescription(newCommentText, userDescription);
    }

    /**
     * 提取用户手动编写的描述内容
     *
     * @param docComment doc comment
     * @return string
     * @since 1.0.0
     */
    private String extractUserDescription(PsiDocComment docComment) {
        StringBuilder description = new StringBuilder();
        String commentText = docComment.getText();
        String[] lines = commentText.split("\n");

        for (String line : lines) {
            String trimmedLine = line.trim();
            // 跳过注释开始和结束标记
            if (trimmedLine.equals("/**") || trimmedLine.equals("*/")) {
                continue;
            }

            // 如果遇到标签行，则停止提取
            if (trimmedLine.startsWith("* @") || trimmedLine.startsWith("*@")) {
                break;
            }

            // 提取描述内容
            if (trimmedLine.startsWith("*")) {
                String content = trimmedLine.substring(1).trim();
                // 使用空格而不是换行符连接描述内容
                if (description.length() > 0 && !StrUtil.isBlank(content)) {
                    description.append(" ");
                }
                if (!StrUtil.isBlank(content)) {
                    description.append(content);
                }
            }
        }

        return description.toString();
    }

    /**
     * 替换注释中的描述部分
     *
     * @param newCommentText new comment text
     * @param userDescription user description
     * @return string
     * @since 1.0.0
     */
    private String replaceDescription(String newCommentText, String userDescription) {
        // 简单的替换策略：将新注释中的第一行描述替换为用户的描述
        String[] lines = newCommentText.split("\n");
        StringBuilder result = new StringBuilder();

        boolean descriptionReplaced = false;
        boolean inTagSection = false;

        for (String line : lines) {
            String trimmedLine = line.trim();

            // 直接添加开始标记
            if (trimmedLine.equals("/**")) {
                result.append(line).append("\n");
                continue;
            }

            // 直接添加结束标记
            if (trimmedLine.equals("*/")) {
                result.append(line);
                break;
            }

            // 检查是否进入标签区域
            if (trimmedLine.startsWith("* @") || trimmedLine.startsWith("*@")) {
                inTagSection = true;
            }

            // 如果还未替换描述且遇到描述行
            if (!descriptionReplaced && !inTagSection && trimmedLine.startsWith("*")) {
                // 添加用户的描述
                result.append(" * ").append(userDescription).append("\n");
                descriptionReplaced = true;
                continue;
            }

            // 如果已经替换过描述或者在标签区域，直接添加原行
            result.append(line).append("\n");
        }

        return result.toString();
    }

    /**
     * 合并标签
     *
     * @param element element
     * @param newCommentText new comment text
     * @param oldDocComment old doc comment
     * @return string
     * @since 1.0.0
     */
    private String mergeTags(@NotNull PsiElement element, @NotNull String newCommentText, @NotNull PsiDocComment oldDocComment) {
        // 判断元素类型,对于方法使用方法标签合并策略,对于类使用类标签合并策略
        if (element instanceof PsiMethod) {
            return this.mergeMethodTags(element, newCommentText, oldDocComment);
        } else {
            // 类、字段等其他元素:保留旧标签,追加新标签(不删除、不覆盖)
            return this.mergeSimpleTags(newCommentText, oldDocComment);
        }
    }

    /**
     * 合并方法标签
     *
     * @param element element
     * @param newCommentText new comment text
     * @param oldDocComment old doc comment
     * @return string
     * @since 1.0.0
     */
    private String mergeMethodTags(@NotNull PsiElement element, @NotNull String newCommentText, @NotNull PsiDocComment oldDocComment) {
        if (!(element instanceof PsiMethod method)) {
            return newCommentText;
        }

        // 收集旧注释中的标签
        TagCollector oldTags = this.collectTags(oldDocComment.getText());
        // 收集新注释中的标签
        TagCollector newTags = this.collectTags(newCommentText);

        // 获取当前方法的实际信息
        MethodInfo methodInfo = this.extractMethodInfo(method);

        // 合并各类标签
        List<String> mergedParams = this.mergeTagsWithValidation(
                oldTags.paramMap, newTags.paramMap, methodInfo.validParamNames,
                (key, validKeys) -> validKeys.contains(key)
        );

        String mergedReturn = this.mergeSingleTag(
                oldTags.returnLine, newTags.returnLine, methodInfo.hasReturn
        );

        List<String> mergedThrows = this.mergeTagsWithValidation(
                oldTags.throwsMap, newTags.throwsMap, methodInfo.throwsNames,
                (key, validKeys) -> validKeys.contains(key)
        );

        List<String> mergedOtherTags = this.mergeSimpleTagsMap(
                oldTags.otherTagsMap, newTags.otherTagsMap
        );

        // 重新组装注释
        return this.reassembleComment(newCommentText, mergedParams, mergedReturn, mergedThrows, mergedOtherTags);
    }

    /**
     * 合并简单标签(包括类、字段等)
     * <p>
     * 策略:保留旧标签,追加新标签(如果旧注释中没有),不删除、不覆盖
     * </p>
     *
     * @param newCommentText new comment text
     * @param oldDocComment old doc comment
     * @return string
     * @since 1.0.0
     */
    private String mergeSimpleTags(@NotNull String newCommentText, @NotNull PsiDocComment oldDocComment) {
        // 收集旧注释和新注释中的标签
        Map<String, String> oldTagsMap = this.collectSimpleTags(oldDocComment.getText());
        Map<String, String> newTagsMap = this.collectSimpleTags(newCommentText);

        // 合并标签:保留旧标签,追加新标签(如果旧注释中没有)
        List<String> mergedTagLines = this.mergeSimpleTagsMap(oldTagsMap, newTagsMap);

        // 重新组装注释
        return this.reassembleSimpleComment(newCommentText, mergedTagLines);
    }

    /**
     * 收集标签信息
     *
     * @param commentText 注释文本
     * @return 标签收集器
     * @since 1.0.0
     */
    private TagCollector collectTags(String commentText) {
        TagCollector collector = new TagCollector();
        for (String line : commentText.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("* @param") || trimmed.startsWith("*@param")) {
                String name = this.extractTokenAfterTag(trimmed, "param");
                if (name != null) {
                    collector.paramMap.put(name, line);
                }
            } else if (trimmed.startsWith("* @return") || trimmed.startsWith("*@return")) {
                collector.returnLine = line;
            } else if (trimmed.startsWith("* @throws") || trimmed.startsWith("*@throws") 
                    || trimmed.startsWith("* @exception")) {
                String exName = this.extractTokenAfterTag(trimmed, "throws", "exception");
                if (exName != null) {
                    collector.throwsMap.put(exName, line);
                }
            } else if (trimmed.startsWith("* @") || trimmed.startsWith("*@")) {
                String tagName = this.extractTagName(trimmed);
                if (tagName != null && !collector.otherTagsMap.containsKey(tagName)) {
                    collector.otherTagsMap.put(tagName, line);
                }
            }
        }
        return collector;
    }

    /**
     * 收集简单标签(类、字段等)
     *
     * @param commentText 注释文本
     * @return 标签映射
     * @since 1.0.0
     */
    private Map<String, String> collectSimpleTags(String commentText) {
        Map<String, String> tagsMap = new LinkedHashMap<>();
        for (String line : commentText.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("* @") || trimmed.startsWith("*@")) {
                String tagName = this.extractTagName(trimmed);
                if (tagName != null && !tagsMap.containsKey(tagName)) {
                    tagsMap.put(tagName, line);
                }
            }
        }
        return tagsMap;
    }

    /**
     * 提取方法信息
     *
     * @param method 方法元素
     * @return 方法信息
     * @since 1.0.0
     */
    private MethodInfo extractMethodInfo(PsiMethod method) {
        MethodInfo info = new MethodInfo();
        // 收集参数名
        for (PsiParameter param : method.getParameterList().getParameters()) {
            info.validParamNames.add(param.getName());
        }
        // 收集泛型参数名
        for (PsiTypeParameter typeParam : method.getTypeParameters()) {
            info.validParamNames.add("<" + typeParam.getName() + ">");
        }
        // 收集异常名
        for (PsiClassType exType : method.getThrowsList().getReferencedTypes()) {
            info.throwsNames.add(exType.getPresentableText());
        }
        // 检查是否有返回值
        info.hasReturn = method.getReturnType() != null 
                && !"void".equals(method.getReturnType().getPresentableText());
        return info;
    }

    /**
     * 合并带验证的标签
     * <p>
     * 根据验证条件决定标签是否保留:保留旧标签(如果通过验证),追加新标签(如果不存在且通过验证)
     * </p>
     *
     * @param oldTagsMap 旧标签映射
     * @param newTagsMap 新标签映射
     * @param validKeys 有效的键集合
     * @param validator 验证器
     * @return 合并后的标签行列表
     * @since 1.0.0
     */
    private List<String> mergeTagsWithValidation(
            Map<String, String> oldTagsMap,
            Map<String, String> newTagsMap,
            Set<String> validKeys,
            BiPredicate<String, Set<String>> validator
    ) {
        List<String> merged = new ArrayList<>();
        // 保留旧标签中仍然有效的
        for (Map.Entry<String, String> entry : oldTagsMap.entrySet()) {
            if (validator.test(entry.getKey(), validKeys)) {
                merged.add(entry.getValue());
            }
        }
        // 追加新标签中不存在于旧标签且有效的
        for (Map.Entry<String, String> entry : newTagsMap.entrySet()) {
            if (!oldTagsMap.containsKey(entry.getKey()) && validator.test(entry.getKey(), validKeys)) {
                merged.add(entry.getValue());
            }
        }
        return merged;
    }

    /**
     * 合并单个标签
     *
     * @param oldTag 旧标签
     * @param newTag 新标签
     * @param shouldKeep 是否应该保留标签
     * @return 合并后的标签
     * @since 1.0.0
     */
    private String mergeSingleTag(String oldTag, String newTag, boolean shouldKeep) {
        if (!shouldKeep) {
            return null;
        }
        return oldTag != null ? oldTag : newTag;
    }

    /**
     * 合并简单标签映射
     * <p>
     * 策略:保留所有旧标签,追加新标签(如果不存在)
     * </p>
     *
     * @param oldTagsMap 旧标签映射
     * @param newTagsMap 新标签映射
     * @return 合并后的标签行列表
     * @since 1.0.0
     */
    private List<String> mergeSimpleTagsMap(Map<String, String> oldTagsMap, Map<String, String> newTagsMap) {
        List<String> merged = new ArrayList<>(oldTagsMap.values());
        for (Map.Entry<String, String> entry : newTagsMap.entrySet()) {
            if (!oldTagsMap.containsKey(entry.getKey())) {
                merged.add(entry.getValue());
            }
        }
        return merged;
    }

    /**
     * 重新组装注释
     *
     * @param newCommentText 新注释文本
     * @param mergedParams 合并后的参数标签
     * @param mergedReturn 合并后的返回值标签
     * @param mergedThrows 合并后的异常标签
     * @param mergedOtherTags 合并后的其他标签
     * @return 重组后的注释
     * @since 1.0.0
     */
    private String reassembleComment(
            String newCommentText,
            List<String> mergedParams,
            String mergedReturn,
            List<String> mergedThrows,
            List<String> mergedOtherTags
    ) {
        StringBuilder out = new StringBuilder();
        for (String line : newCommentText.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.equals("/**")) {
                out.append(line).append("\n");
            } else if (trimmed.equals("*/")) {
                // 按顺序添加所有标签
                mergedParams.forEach(tag -> out.append(tag).append("\n"));
                if (mergedReturn != null) {
                    out.append(mergedReturn).append("\n");
                }
                mergedThrows.forEach(tag -> out.append(tag).append("\n"));
                mergedOtherTags.forEach(tag -> out.append(tag).append("\n"));
                out.append(line);
                break;
            } else if (!trimmed.startsWith("* @") && !trimmed.startsWith("*@")) {
                // 保留描述行
                out.append(line).append("\n");
            }
        }
        return out.toString();
    }

    /**
     * 重新组装简单注释
     *
     * @param newCommentText 新注释文本
     * @param mergedTagLines 合并后的标签行
     * @return 重组后的注释
     * @since 1.0.0
     */
    private String reassembleSimpleComment(String newCommentText, List<String> mergedTagLines) {
        StringBuilder out = new StringBuilder();
        for (String line : newCommentText.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.equals("/**")) {
                out.append(line).append("\n");
            } else if (trimmed.equals("*/")) {
                mergedTagLines.forEach(tag -> out.append(tag).append("\n"));
                out.append(line);
                break;
            } else if (!trimmed.startsWith("* @") && !trimmed.startsWith("*@")) {
                out.append(line).append("\n");
            }
        }
        return out.toString();
    }

    /**
     * 提取标签名
     *
     * @param trimmedLine 修剪后的行
     * @return 标签名
     * @since 1.0.0
     */
    private String extractTagName(String trimmedLine) {
        String s = trimmedLine.replaceFirst("^\\*\\s*@", "").replaceFirst("^\\*@", "");
        if (s.isEmpty()) {
            return null;
        }
        int idx = s.indexOf(' ');
        String tagName = idx > 0 ? s.substring(0, idx).trim() : s.trim();
        return tagName.isEmpty() ? null : tagName;
    }

    /**
     * 提取标签后的第一个token
     *
     * @param trimmedLine 修剪后的行
     * @param tagNames 标签名数组
     * @return 提取的token
     * @since 1.0.0
     */
    private String extractTokenAfterTag(String trimmedLine, String... tagNames) {
        String s = trimmedLine;
        for (String tagName : tagNames) {
            s = s.replaceFirst("^\\*\\s*@" + tagName + "\\s*", "")
                    .replaceFirst("^\\*@" + tagName + "\\s*", "");
        }
        if (s.isEmpty() || s.equals(trimmedLine)) {
            return null;
        }
        int idx = s.indexOf(' ');
        String token = idx > 0 ? s.substring(0, idx).trim() : s.trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 标签收集器
     * <p>
     * 用于收集注释中的各类标签
     * </p>
     *
     * @since 1.0.0
     */
    private static class TagCollector {
        Map<String, String> paramMap = new LinkedHashMap<>();
        String returnLine = null;
        Map<String, String> throwsMap = new LinkedHashMap<>();
        Map<String, String> otherTagsMap = new LinkedHashMap<>();
    }

    /**
     * 方法信息
     * <p>
     * 存储方法的参数、返回值、异常等信息
     * </p>
     *
     * @since 1.0.0
     */
    private static class MethodInfo {
        Set<String> validParamNames = new LinkedHashSet<>();
        Set<String> throwsNames = new LinkedHashSet<>();
        boolean hasReturn = false;
    }
}
